package downloader.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public final class DownloadConfig {

    private final String url;
    private final Path outputPath;
    private final int workers;
    private final Long chunkSizeBytes;
    private final int maxRetries;
    private final Duration requestTimeout;
    private final String expectedSha256;

    private DownloadConfig(Builder builder) {
        this.url = Objects.requireNonNull(builder.url, "url");
        this.outputPath = Objects.requireNonNull(builder.outputPath, "outputPath");
        this.workers = builder.workers;
        this.chunkSizeBytes = builder.chunkSizeBytes;
        this.maxRetries = builder.maxRetries;
        this.requestTimeout = Objects.requireNonNull(builder.requestTimeout, "requestTimeout");
        this.expectedSha256 = builder.expectedSha256;

        if (workers <= 0) {
            throw new IllegalArgumentException("workers must be positive");
        }
        if (chunkSizeBytes != null && chunkSizeBytes <= 0) {
            throw new IllegalArgumentException("chunkSizeBytes must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries cannot be negative");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public String url() {
        return url;
    }

    public Path outputPath() {
        return outputPath;
    }

    public int workers() {
        return workers;
    }

    public Long chunkSizeBytes() {
        return chunkSizeBytes;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public String expectedSha256() {
        return expectedSha256;
    }

    public static final class Builder {
        private String url;
        private Path outputPath;
        private int workers = 4;
        private Long chunkSizeBytes;
        private int maxRetries = 3;
        private Duration requestTimeout = Duration.ofSeconds(30);
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

        public Builder chunkSizeBytes(Long chunkSizeBytes) {
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
            return new DownloadConfig(this);
        }
    }
}
