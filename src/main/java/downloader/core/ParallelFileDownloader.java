package downloader.core;

import downloader.util.ChecksumUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class ParallelFileDownloader {

    private static final int BUFFER_SIZE = 64 * 1024;

    private final HttpClient httpClient;

    public ParallelFileDownloader() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public ParallelFileDownloader(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public DownloadReport download(DownloadConfig config) throws IOException, InterruptedException {
        return download(config, new NoOpProgressListener());
    }

    public DownloadReport download(DownloadConfig config, ProgressListener progressListener)
            throws IOException, InterruptedException {

        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();

        URI uri = URI.create(config.url());
        DownloadMetadata metadata = fetchMetadata(uri, config);

        if (!metadata.acceptsRanges()) {
            throw new IOException("Server does not support byte ranges: missing or invalid Accept-Ranges header");
        }

        long fileSize = metadata.contentLength();
        if (fileSize < 0) {
            throw new IOException("Invalid Content-Length: " + fileSize);
        }

        if (config.outputPath().getParent() != null) {
            Files.createDirectories(config.outputPath().getParent());
        }

        prepareOutputFile(config.outputPath(), fileSize);

        List<Chunk> chunks = planChunks(fileSize, config);
        AtomicLong downloadedBytes = new AtomicLong(0);

        ExecutorService executor = Executors.newFixedThreadPool(Math.min(config.workers(), chunks.size()));
        CompletionService<DownloadReport.ChunkReport> completionService = new ExecutorCompletionService<>(executor);

        try {
            for (Chunk chunk : chunks) {
                completionService.submit(new ChunkTask(uri, config, chunk, downloadedBytes, fileSize, progressListener));
            }

            List<DownloadReport.ChunkReport> chunkReports = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                try {
                    chunkReports.add(completionService.take().get());
                } catch (Exception e) {
                    executor.shutdownNow();
                    throw new IOException("Failed to download one or more chunks", e);
                }
            }

            verifyFinalSize(config.outputPath(), fileSize);

            String sha256 = null;
            boolean checksumVerified = false;

            if (config.expectedSha256() != null && !config.expectedSha256().isBlank()) {
                sha256 = ChecksumUtil.sha256Hex(config.outputPath());
                checksumVerified = sha256.equalsIgnoreCase(config.expectedSha256());

                if (!checksumVerified) {
                    throw new IOException("SHA-256 mismatch. Expected " + config.expectedSha256() + ", got " + sha256);
                }
            } else {
                sha256 = ChecksumUtil.sha256Hex(config.outputPath());
            }

            chunkReports.sort(Comparator.comparingInt(DownloadReport.ChunkReport::index));

            Instant finishedAt = Instant.now();
            long durationMillis = (System.nanoTime() - startedNanos) / 1_000_000;
            int retryCount = chunkReports.stream()
                    .mapToInt(report -> Math.max(0, report.attempts() - 1))
                    .sum();

            return new DownloadReport(
                    config.url(),
                    config.outputPath(),
                    fileSize,
                    config.workers(),
                    chunks.size(),
                    chunkReports.size(),
                    retryCount,
                    durationMillis,
                    downloadedBytes.get(),
                    sha256,
                    checksumVerified,
                    startedAt,
                    finishedAt,
                    List.copyOf(chunkReports)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private DownloadMetadata fetchMetadata(URI uri, DownloadConfig config) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(config.requestTimeout())
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HEAD request failed with status " + response.statusCode());
        }

        OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
        if (contentLength.isEmpty()) {
            throw new IOException("Missing Content-Length header");
        }

        boolean acceptsRanges = response.headers()
                .firstValue("Accept-Ranges")
                .map(value -> value.equalsIgnoreCase("bytes"))
                .orElse(false);

        return new DownloadMetadata(contentLength.getAsLong(), acceptsRanges);
    }

    private void prepareOutputFile(java.nio.file.Path outputPath, long fileSize) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(outputPath.toFile(), "rw")) {
            file.setLength(fileSize);
        }
    }

    private List<Chunk> planChunks(long fileSize, DownloadConfig config) {
        List<Chunk> chunks = new ArrayList<>();

        if (fileSize == 0) {
            return chunks;
        }

        long chunkSize = config.chunkSizeBytes() != null
                ? config.chunkSizeBytes()
                : Math.max(1, (fileSize + config.workers() - 1) / config.workers());

        int index = 0;
        for (long start = 0; start < fileSize; start += chunkSize) {
            long end = Math.min(fileSize - 1, start + chunkSize - 1);
            chunks.add(new Chunk(index++, start, end));
        }

        return chunks;
    }

    private void verifyFinalSize(java.nio.file.Path outputPath, long expectedSize) throws IOException {
        long actualSize = Files.size(outputPath);
        if (actualSize != expectedSize) {
            throw new IOException("Output size mismatch. Expected " + expectedSize + ", got " + actualSize);
        }
    }

    private final class ChunkTask implements Callable<DownloadReport.ChunkReport> {
        private final URI uri;
        private final DownloadConfig config;
        private final Chunk chunk;
        private final AtomicLong downloadedBytes;
        private final long totalBytes;
        private final ProgressListener progressListener;

        private ChunkTask(
                URI uri,
                DownloadConfig config,
                Chunk chunk,
                AtomicLong downloadedBytes,
                long totalBytes,
                ProgressListener progressListener
        ) {
            this.uri = uri;
            this.config = config;
            this.chunk = chunk;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
            this.progressListener = progressListener;
        }

        @Override
        public DownloadReport.ChunkReport call() throws Exception {
            long started = System.nanoTime();
            int attempts = 0;
            IOException lastException = null;

            while (attempts <= config.maxRetries()) {
                attempts++;

                try {
                    long bytesWritten = downloadChunkOnce();
                    long durationMillis = (System.nanoTime() - started) / 1_000_000;
                    return new DownloadReport.ChunkReport(
                            chunk.index(),
                            chunk.startInclusive(),
                            chunk.endInclusive(),
                            attempts,
                            bytesWritten,
                            durationMillis
                    );
                } catch (IOException e) {
                    lastException = e;
                    if (attempts > config.maxRetries()) {
                        break;
                    }

                    long backoffMillis = Math.min(1000, 100L * attempts);
                    Thread.sleep(backoffMillis);
                }
            }

            throw lastException == null
                    ? new IOException("Chunk download failed")
                    : lastException;
        }

        private long downloadChunkOnce() throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(config.requestTimeout())
                    .GET()
                    .header("Range", chunk.rangeHeaderValue())
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 206) {
                throw new IOException("Expected 206 Partial Content for " + chunk.rangeHeaderValue()
                        + ", got " + response.statusCode());
            }

            long expectedSize = chunk.size();
            long bytesWritten = 0;

            try (InputStream inputStream = response.body();
                 RandomAccessFile outputFile = new RandomAccessFile(config.outputPath().toFile(), "rw")) {

                outputFile.seek(chunk.startInclusive());

                byte[] buffer = new byte[BUFFER_SIZE];
                int read;

                while ((read = inputStream.read(buffer)) != -1) {
                    outputFile.write(buffer, 0, read);
                    bytesWritten += read;
                    long current = downloadedBytes.addAndGet(read);
                    progressListener.onProgress(current, totalBytes);
                }
            }

            if (bytesWritten != expectedSize) {
                throw new IOException("Chunk size mismatch for " + chunk.rangeHeaderValue()
                        + ". Expected " + expectedSize + ", got " + bytesWritten);
            }

            return bytesWritten;
        }
    }
}
