package downloader.core;

public record Chunk(int index, long startInclusive, long endInclusive) {

    public long size() {
        return endInclusive - startInclusive + 1;
    }

    public String rangeHeaderValue() {
        return "bytes=" + startInclusive + "-" + endInclusive;
    }
}
