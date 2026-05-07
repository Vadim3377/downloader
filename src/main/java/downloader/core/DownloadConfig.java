package downloader.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for a single parallel download.
 *
 * <p>The downloader expects the remote server to support HTTP byte ranges and
 * to expose both {@code Accept-Ranges: bytes} and {@code Content-Length} in
 * response to a HEAD request.</p>
 */
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

        // Validate configuration early so downloader failures are caused by I/O or server behaviour,
        // not by invalid local settings discovered halfway through the download.
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

    /**
     * Builder with safe defaults for a normal local or remote download.
     */
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

        /**
         * Sets how many retries are allowed after the first failed attempt.
         *
         * <p>For example, {@code maxRetries = 3} allows up to four total
         * attempts for an individual chunk.</p>
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * Optional expected SHA-256 hash of the completed file.
         *
         * <p>If provided, the downloader verifies the temporary file before it
         * replaces the final output path.</p>
         */
        public Builder expectedSha256(String expectedSha256) {
            this.expectedSha256 = expectedSha256;
            return this;
        }

        public DownloadConfig build() {
            return new DownloadConfig(url, outputPath, workers, chunkSizeBytes, maxRetries, requestTimeout, expectedSha256);
        }
    }
}
