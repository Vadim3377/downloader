package downloader.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record DownloadReport(
        String url,
        Path outputPath,
        long fileSizeBytes,
        int workers,
        int chunksTotal,
        int chunksSucceeded,
        int chunksRetried,
        long durationMillis,
        long downloadedBytes,
        String sha256,
        boolean checksumVerified,
        Instant startedAt,
        Instant finishedAt,
        List<ChunkReport> chunkReports
) {
    public record ChunkReport(
            int index,
            long startInclusive,
            long endInclusive,
            int attempts,
            long bytesWritten,
            long durationMillis
    ) {
    }
}
