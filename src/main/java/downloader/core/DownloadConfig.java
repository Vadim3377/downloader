package downloader.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public record DownloadConfig(
        String url,
        Path outputPath,
        int workers,
        long chunkSizeBytes,
        int maxRetries,
        Duration requestTimeout,
        String expectedSha256
) {
    private static final int DEFAULT_WORKERS = 4;
    private static final long DEFAULT_CHUNK_SIZE_BYTES = 1024 * 1024;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public DownloadConfig {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(outputPath, "outputPath");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        if (workers <= 0) {
            throw new IllegalArgumentException("workers must be positive");
        }
        if (chunkSizeBytes <= 0) {
            throw new IllegalArgumentException("chunkSizeBytes must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        if (expectedSha256 != null && !expectedSha256.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedSha256 must be a 64-character hex string");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String url;
        private Path outputPath;
        private int workers = DEFAULT_WORKERS;
        private long chunkSizeBytes = DEFAULT_CHUNK_SIZE_BYTES;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private String expectedSha256;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder outputPath(Path outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public Builder workers(int workers) {
            this.workers = workers;
            return this;
        }

        public Builder chunkSizeBytes(long chunkSizeBytes) {
            this.chunkSizeBytes = chunkSizeBytes;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder expectedSha256(String expectedSha256) {
            this.expectedSha256 = expectedSha256;
            return this;
        }

        public DownloadConfig build() {
            return new DownloadConfig(url, outputPath, workers, chunkSizeBytes, maxRetries, requestTimeout, expectedSha256);
        }
    }
}
