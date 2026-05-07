package downloader.core;

public record Chunk(int index, long startInclusive, long endInclusive) {
    public long sizeBytes() {
        return endInclusive - startInclusive + 1;
    }
}
