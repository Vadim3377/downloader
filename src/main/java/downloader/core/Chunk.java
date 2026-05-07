package downloader.core;

/**
 * Represents a byte-range interval assigned to one download task.
 *
 * The interval is inclusive at both ends because HTTP Range requests use
 * the same convention:  Range: bytes=start-end.
 */
public record Chunk(int index, long startInclusive, long endInclusive) {
    /**
     * Returns the number of bytes covered by this inclusive range.
     */
    public long sizeBytes() {
        return endInclusive - startInclusive + 1;
    }
}
