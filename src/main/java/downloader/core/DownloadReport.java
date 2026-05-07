package downloader.core;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

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
