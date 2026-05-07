package downloader.core;

import downloader.testsupport.RangeHttpTestServer;
import downloader.util.HashUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelFileDownloaderTest {
    @TempDir
    Path tempDir;

    @Test
    void downloadsTextFileUsingParallelRanges() throws Exception {
        byte[] content = "hello parallel downloader".getBytes();
        try (RangeHttpTestServer server = RangeHttpTestServer.builder(content).build()) {
            Path output = tempDir.resolve("file.txt");
            DownloadReport report = new ParallelFileDownloader().download(config(server.start(), output, 4, 5).build());

            assertArrayEquals(content, Files.readAllBytes(output));
            assertEquals(content.length, report.downloadedBytes());
            assertTrue(report.chunkCount() > 1);
        }
    }

    @Test
    void downloadsBinaryFileWithoutCorruption() throws Exception {
        byte[] content = new byte[100_000];
        new Random(42).nextBytes(content);
        try (RangeHttpTestServer server = RangeHttpTestServer.builder(content).build()) {
            Path output = tempDir.resolve("file.bin");
            new ParallelFileDownloader().download(config(server.start(), output, 6, 4096).build());

            assertArrayEquals(content, Files.readAllBytes(output));
        }
    }

    @Test
    void supportsZeroByteFile() throws Exception {
        byte[] content = new byte[0];
        try (RangeHttpTestServer server = RangeHttpTestServer.builder(content).build()) {
            Path output = tempDir.resolve("empty.bin");
            DownloadReport report = new ParallelFileDownloader().download(config(server.start(), output, 2, 1024).build());

            assertEquals(0, Files.size(output));
            assertEquals(0, report.chunkCount());
        }
    }

    @Test
    void retriesTransientChunkFailures() throws Exception {
        byte[] content = "retry eventually succeeds".getBytes();
        try (RangeHttpTestServer server = RangeHttpTestServer.builder(content).failuresBeforeSuccess(1).build()) {
            Path output = tempDir.resolve("retry.txt");
            new ParallelFileDownloader().download(config(server.start(), output, 2, 8).withRetries(2).build());

            assertArrayEquals(content, Files.readAllBytes(output));
        }
    }

    @Test
    void rejectsServerWithoutRangeSupport() throws Exception {
        byte[] content = "hello".getBytes();
        try (RangeHttpTestServer server = RangeHttpTestServer.builder(content).acceptRanges(false).build()) {
            Path output = tempDir.resolve("file.txt");

            assertThrows(DownloadException.class, () -> new ParallelFileDownloader().download(config(server.start(), output, 2, 2).build()));
        }
    }

    @Test
    void rejectsMissingContentLength() throws Exception {
        byte[] content = "hello".getBytes();
        try (RangeHttpTestServer server = RangeHttpTestServer.builder(content).includeContentLength(false).build()) {
            Path output = tempDir.resolve("file.txt");

            assertThrows(DownloadException.class, () -> new ParallelFileDownloader().download(config(server.start(), output, 2, 2).build()));
        }
    }

    @Test
    void rejectsRangeRequestThatReturnsHttp200() throws Exception {
        byte[] content = "hello".getBytes();
        try (RangeHttpTestServer server = RangeHttpTestServer.builder(content).returnOkForRange(true).build()) {
            Path output = tempDir.resolve("file.txt");

            assertThrows(DownloadException.class, () -> new ParallelFileDownloader().download(config(server.start(), output, 2, 2).build()));
        }
    }

    @Test
    void rejectsIncorrectContentRangeHeader() throws Exception {
        byte[] content = "hello world".getBytes();
        try (RangeHttpTestServer server = RangeHttpTestServer.builder(content).wrongContentRange(true).build()) {
            Path output = tempDir.resolve("file.txt");

            assertThrows(DownloadException.class, () -> new ParallelFileDownloader().download(config(server.start(), output, 2, 5).build()));
        }
    }

    @Test
    void verifiesExpectedSha256() throws Exception {
        byte[] content = "checksum".getBytes();
        try (RangeHttpTestServer server = RangeHttpTestServer.builder(content).build()) {
            Path output = tempDir.resolve("file.txt");
            String expected = HashUtils.toHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));

            DownloadReport report = new ParallelFileDownloader().download(
                    config(server.start(), output, 2, 3).withExpectedSha256(expected).build()
            );

            assertEquals(expected, report.sha256());
            assertTrue(report.checksumMatched());
        }
    }

    @Test
    void provesChunksAreDownloadedConcurrently() throws Exception {
        byte[] content = new byte[400_000];
        try (RangeHttpTestServer server = RangeHttpTestServer.builder(content).perRequestDelayMillis(300).build()) {
            Path output = tempDir.resolve("parallel.bin");
            long started = System.nanoTime();

            new ParallelFileDownloader().download(config(server.start(), output, 4, 100_000).build());

            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertArrayEquals(content, Files.readAllBytes(output));
            assertTrue(elapsedMillis < 1_000, "Sequential download would take about 1,200ms; elapsed=" + elapsedMillis);
        }
    }

    private TestConfigBuilder config(String url, Path output, int workers, long chunkSizeBytes) {
        return new TestConfigBuilder(url, output, workers, chunkSizeBytes);
    }

    private static final class TestConfigBuilder {
        private final String url;
        private final Path output;
        private final int workers;
        private final long chunkSizeBytes;
        private int retries = 0;
        private String expectedSha256;

        private TestConfigBuilder(String url, Path output, int workers, long chunkSizeBytes) {
            this.url = url;
            this.output = output;
            this.workers = workers;
            this.chunkSizeBytes = chunkSizeBytes;
        }

        private TestConfigBuilder withRetries(int retries) {
            this.retries = retries;
            return this;
        }

        private TestConfigBuilder withExpectedSha256(String expectedSha256) {
            this.expectedSha256 = expectedSha256;
            return this;
        }

        private DownloadConfig build() {
            return DownloadConfig.builder()
                    .url(url)
                    .outputPath(output)
                    .workers(workers)
                    .chunkSizeBytes(chunkSizeBytes)
                    .maxRetries(retries)
                    .expectedSha256(expectedSha256)
                    .build();
        }
    }
}
