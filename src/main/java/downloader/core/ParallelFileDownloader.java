package downloader.core;

import downloader.util.HashUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ParallelFileDownloader {
    private final HttpClient httpClient;

    public ParallelFileDownloader() {
        this(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build());
    }

    public ParallelFileDownloader(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public DownloadReport download(DownloadConfig config) throws DownloadException, InterruptedException {
        Instant startedAt = Instant.now();
        URI uri = URI.create(config.url());
        DownloadMetadata metadata = fetchMetadata(uri, config.requestTimeout());

        if (!metadata.acceptsRanges()) {
            throw new DownloadException("Server does not advertise byte-range support using Accept-Ranges: bytes");
        }

        List<Chunk> chunks = planChunks(metadata.contentLengthBytes(), config.chunkSizeBytes());
        Path outputPath = config.outputPath();
        Path tempPath = outputPath.resolveSibling(outputPath.getFileName() + ".part");

        try {
            Path parent = outputPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // The .part file prevents a failed download from looking like a complete final artifact.
            Files.deleteIfExists(tempPath);
            preallocate(tempPath, metadata.contentLengthBytes());

            List<DownloadReport.ChunkReport> chunkReports = downloadChunks(uri, tempPath, config, chunks, metadata.contentLengthBytes());
            validateFinalSize(tempPath, metadata.contentLengthBytes());

            String actualSha256 = HashUtils.sha256(tempPath);
            boolean checksumMatched = config.expectedSha256() == null || actualSha256.equalsIgnoreCase(config.expectedSha256());
            if (!checksumMatched) {
                throw new DownloadException("SHA-256 mismatch. Expected " + config.expectedSha256() + ", got " + actualSha256);
            }

            moveAtomicallyWhenPossible(tempPath, outputPath);

            long downloadedBytes = chunkReports.stream().mapToLong(DownloadReport.ChunkReport::bytesWritten).sum();
            return new DownloadReport(
                    config.url(),
                    outputPath,
                    metadata.contentLengthBytes(),
                    config.workers(),
                    config.chunkSizeBytes(),
                    config.maxRetries(),
                    chunks.size(),
                    Duration.between(startedAt, Instant.now()),
                    downloadedBytes,
                    actualSha256,
                    checksumMatched,
                    chunkReports
            );
        } catch (IOException e) {
            throw new DownloadException("Could not write downloaded file", e);
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
                // Best effort cleanup only: the original failure is more useful to the caller.
            }
        }
    }

    private DownloadMetadata fetchMetadata(URI uri, Duration timeout) throws DownloadException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).timeout(timeout).build();
        HttpResponse<Void> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            throw new DownloadException("HEAD request failed", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new DownloadException("HEAD request returned HTTP " + response.statusCode());
        }

        String acceptRanges = response.headers().firstValue("Accept-Ranges").orElse("");
        boolean acceptsRanges = acceptRanges.toLowerCase(Locale.ROOT).contains("bytes");
        String contentLengthHeader = response.headers().firstValue("Content-Length").orElse(null);
        if (contentLengthHeader == null) {
            throw new DownloadException("Missing Content-Length header");
        }

        long contentLength;
        try {
            contentLength = Long.parseLong(contentLengthHeader);
        } catch (NumberFormatException e) {
            throw new DownloadException("Invalid Content-Length header: " + contentLengthHeader, e);
        }

        if (contentLength < 0) {
            throw new DownloadException("Content-Length must not be negative");
        }
        return new DownloadMetadata(contentLength, acceptsRanges);
    }

    private List<Chunk> planChunks(long fileSizeBytes, long chunkSizeBytes) {
        List<Chunk> chunks = new ArrayList<>();
        for (long start = 0; start < fileSizeBytes; start += chunkSizeBytes) {
            long end = Math.min(fileSizeBytes - 1, start + chunkSizeBytes - 1);
            chunks.add(new Chunk(chunks.size(), start, end));
        }
        return chunks;
    }

    private void preallocate(Path path, long sizeBytes) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.setLength(sizeBytes);
        }
    }

    private List<DownloadReport.ChunkReport> downloadChunks(
            URI uri,
            Path tempPath,
            DownloadConfig config,
            List<Chunk> chunks,
            long totalSizeBytes
    ) throws DownloadException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(config.workers());
        CompletionService<DownloadReport.ChunkReport> completionService = new ExecutorCompletionService<>(executor);
        try {
            for (Chunk chunk : chunks) {
                completionService.submit(new ChunkTask(uri, tempPath, config, chunk, totalSizeBytes));
            }

            List<DownloadReport.ChunkReport> reports = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                try {
                    reports.add(completionService.take().get());
                } catch (ExecutionException e) {
                    executor.shutdownNow();
                    Throwable cause = e.getCause();
                    if (cause instanceof DownloadException downloadException) {
                        throw downloadException;
                    }
                    throw new DownloadException("Chunk download failed", cause);
                }
            }

            reports.sort(Comparator.comparingInt(DownloadReport.ChunkReport::index));
            return List.copyOf(reports);
        } finally {
            executor.shutdownNow();
        }
    }

    private void validateFinalSize(Path path, long expectedSizeBytes) throws IOException, DownloadException {
        long actualSizeBytes = Files.size(path);
        if (actualSizeBytes != expectedSizeBytes) {
            throw new DownloadException("Invalid final size. Expected " + expectedSizeBytes + " bytes, got " + actualSizeBytes);
        }
    }

    private void moveAtomicallyWhenPossible(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private final class ChunkTask implements Callable<DownloadReport.ChunkReport> {
        private final URI uri;
        private final Path tempPath;
        private final DownloadConfig config;
        private final Chunk chunk;
        private final long totalSizeBytes;

        private ChunkTask(URI uri, Path tempPath, DownloadConfig config, Chunk chunk, long totalSizeBytes) {
            this.uri = uri;
            this.tempPath = tempPath;
            this.config = config;
            this.chunk = chunk;
            this.totalSizeBytes = totalSizeBytes;
        }

        @Override
        public DownloadReport.ChunkReport call() throws DownloadException, InterruptedException {
            Instant startedAt = Instant.now();
            int attempts = 0;
            IOException lastIoException = null;

            while (attempts <= config.maxRetries()) {
                attempts++;
                try {
                    byte[] data = fetchChunk();
                    if (data.length != chunk.sizeBytes()) {
                        throw new IOException("Chunk " + chunk.index() + " returned " + data.length
                                + " bytes instead of " + chunk.sizeBytes());
                    }
                    writeChunk(data);
                    return new DownloadReport.ChunkReport(
                            chunk.index(),
                            chunk.startInclusive(),
                            chunk.endInclusive(),
                            data.length,
                            attempts,
                            Duration.between(startedAt, Instant.now())
                    );
                } catch (IOException e) {
                    lastIoException = e;
                    if (attempts > config.maxRetries()) {
                        break;
                    }
                }
            }

            throw new DownloadException("Chunk " + chunk.index() + " failed after " + attempts + " attempt(s)", lastIoException);
        }

        private byte[] fetchChunk() throws IOException, InterruptedException, DownloadException {
            String range = "bytes=" + chunk.startInclusive() + "-" + chunk.endInclusive();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .timeout(config.requestTimeout())
                    .header("Range", range)
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 206) {
                throw new IOException("Expected HTTP 206 for range " + range + ", got HTTP " + response.statusCode());
            }

            // Content-Range proves that the server returned the exact interval requested, not merely any 206 response.
            validateContentRange(response, chunk, totalSizeBytes);
            return response.body();
        }

        private void validateContentRange(HttpResponse<?> response, Chunk chunk, long totalSizeBytes) throws DownloadException {
            String expected = "bytes " + chunk.startInclusive() + "-" + chunk.endInclusive() + "/" + totalSizeBytes;
            String actual = response.headers()
                    .firstValue("Content-Range")
                    .orElseThrow(() -> new DownloadException("Missing Content-Range for chunk " + chunk.index()));
            if (!expected.equals(actual)) {
                throw new DownloadException("Invalid Content-Range for chunk " + chunk.index()
                        + ". Expected '" + expected + "', got '" + actual + "'");
            }
        }

        private void writeChunk(byte[] data) throws IOException {
            // Each task writes a disjoint byte interval, so parallel writes are safe at the file-offset level.
            try (RandomAccessFile file = new RandomAccessFile(tempPath.toFile(), "rw")) {
                file.seek(chunk.startInclusive());
                file.write(data);
            }
        }
    }
}
