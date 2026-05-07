package downloader.core;

/**
 * Represents a byte-range interval assigned to one download task.
 *
 * <p>The interval is inclusive at both ends because HTTP Range requests use
 * the same convention: {@code Range: bytes=start-end}.</p>
 */
public record Chunk(int index, long startInclusive, long endInclusive) {
    /**
     * Returns the number of bytes covered by this inclusive range.
     */
    public long sizeBytes() {
        return endInclusive - startInclusive + 1;
    }
}
