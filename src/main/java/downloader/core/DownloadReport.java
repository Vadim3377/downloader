package downloader.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Structured diagnostics for a completed download.
 *
 * The report is suitable for JSON or Markdown output and includes both
 * global download information and per-chunk timing/attempt data.
 */
public record DownloadReport(
        String url,
        Path outputPath,
        long fileSizeBytes,
        int workers,
        long chunkSizeBytes,
        int maxRetries,
        int chunkCount,
        Duration duration,
        long downloadedBytes,
        String sha256,
        boolean checksumMatched,
        List<ChunkReport> chunks
) {
    /**
     * Diagnostics for one byte-range request.
     *
     * <p>These fields make it easier to identify slow chunks, repeated retries,
     * or incorrect range handling by a server.</p>
     */
    public record ChunkReport(
            int index,
            long startInclusive,
            long endInclusive,
            long bytesWritten,
            int attempts,
            Duration duration
    ) {
    }
}
