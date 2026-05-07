package downloader.core;

import downloader.testsupport.RangeHttpTestServer;
import downloader.util.ChecksumUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ParallelFileDownloaderTest {

    @TempDir
    Path tempDir;

    @Test
    void downloadsTextFileInParallel() throws Exception {
        byte[] data = """
                This is a test file.
                It should be downloaded in parallel.
                Each chunk should be fetched using HTTP Range requests.
                The final file should match the original content exactly.
                """.getBytes(StandardCharsets.UTF_8);

        try (RangeHttpTestServer server = RangeHttpTestServer.create(data)) {
            String url = server.start();
            Path output = tempDir.resolve("downloaded.txt");

            DownloadReport report = new ParallelFileDownloader().download(config(url, output, 4));

            assertArrayEquals(data, Files.readAllBytes(output));
            assertEquals(data.length, report.fileSizeBytes());
            assertEquals(4, report.chunksSucceeded());
            assertEquals(0, report.chunksRetried());
        }
    }

    @Test
    void downloadsBinaryFileInParallel() throws Exception {
        byte[] data = new byte[1024 * 1024];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 251);
        }

        try (RangeHttpTestServer server = RangeHttpTestServer.create(data)) {
            String url = server.start();
            Path output = tempDir.resolve("downloaded.bin");

            DownloadReport report = new ParallelFileDownloader().download(config(url, output, 8));

            assertArrayEquals(data, Files.readAllBytes(output));
            assertEquals(data.length, report.downloadedBytes());
            assertEquals(8, report.chunksTotal());
        }
    }

    @Test
    void supportsChunkCountGreaterThanFileSize() throws Exception {
        byte[] data = "small".getBytes(StandardCharsets.UTF_8);

        try (RangeHttpTestServer server = RangeHttpTestServer.create(data)) {
            String url = server.start();
            Path output = tempDir.resolve("small.txt");

            DownloadReport report = new ParallelFileDownloader().download(config(url, output, 100));

            assertArrayEquals(data, Files.readAllBytes(output));
            assertEquals(data.length, report.chunksTotal());
        }
    }

    @Test
    void retriesTransientChunkFailures() throws Exception {
        byte[] data = new byte[128 * 1024];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        try (RangeHttpTestServer server = RangeHttpTestServer.create(data).withTransientFailures(2)) {
            String url = server.start();
            Path output = tempDir.resolve("retry.bin");

            DownloadConfig config = DownloadConfig.builder()
                    .url(url)
                    .outputPath(output)
                    .workers(4)
                    .maxRetries(3)
                    .build();

            DownloadReport report = new ParallelFileDownloader().download(config);

            assertArrayEquals(data, Files.readAllBytes(output));
            assertTrue(report.chunksRetried() >= 1);
        }
    }

    @Test
    void rejectsServerWithoutRangeSupport() throws Exception {
        byte[] data = "no range support".getBytes(StandardCharsets.UTF_8);

        try (RangeHttpTestServer server = RangeHttpTestServer.create(data).withoutRanges()) {
            String url = server.start();
            Path output = tempDir.resolve("out.txt");

            IOException error = assertThrows(IOException.class,
                    () -> new ParallelFileDownloader().download(config(url, output, 4)));

            assertTrue(error.getMessage().contains("byte ranges"));
        }
    }

    @Test
    void rejectsServerWithoutContentLength() throws Exception {
        byte[] data = "missing length".getBytes(StandardCharsets.UTF_8);

        try (RangeHttpTestServer server = RangeHttpTestServer.create(data).withoutContentLength()) {
            String url = server.start();
            Path output = tempDir.resolve("out.txt");

            IOException error = assertThrows(IOException.class,
                    () -> new ParallelFileDownloader().download(config(url, output, 4)));

            assertTrue(error.getMessage().contains("Content-Length"));
        }
    }

    @Test
    void rejectsServerReturning200ForRangeRequest() throws Exception {
        byte[] data = "wrong status".getBytes(StandardCharsets.UTF_8);

        try (RangeHttpTestServer server = RangeHttpTestServer.create(data).returning200ForRange()) {
            String url = server.start();
            Path output = tempDir.resolve("out.txt");

            IOException error = assertThrows(IOException.class,
                    () -> new ParallelFileDownloader().download(config(url, output, 4)));

            assertTrue(error.getMessage().contains("Failed to download"));
        }
    }

    @Test
    void verifiesSha256WhenExpectedChecksumIsProvided() throws Exception {
        byte[] data = "checksum data".getBytes(StandardCharsets.UTF_8);

        try (RangeHttpTestServer server = RangeHttpTestServer.create(data)) {
            String url = server.start();

            Path reference = tempDir.resolve("reference.txt");
            Files.write(reference, data);
            String sha256 = ChecksumUtil.sha256Hex(reference);

            Path output = tempDir.resolve("checksum.txt");

            DownloadConfig config = DownloadConfig.builder()
                    .url(url)
                    .outputPath(output)
                    .workers(3)
                    .expectedSha256(sha256)
                    .build();

            DownloadReport report = new ParallelFileDownloader().download(config);

            assertTrue(report.checksumVerified());
            assertEquals(sha256, report.sha256());
        }
    }

    private DownloadConfig config(String url, Path output, int workers) {
        return DownloadConfig.builder()
                .url(url)
                .outputPath(output)
                .workers(workers)
                .maxRetries(0)
                .build();
    }
}
