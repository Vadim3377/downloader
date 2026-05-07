package downloader.core;

public record DownloadMetadata(long contentLengthBytes, boolean acceptsRanges) {
}
