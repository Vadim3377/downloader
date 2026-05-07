package downloader.core;

public record DownloadMetadata(long contentLength, boolean acceptsRanges) {
}
