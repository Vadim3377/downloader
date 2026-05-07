package downloader.core;

/**
 * Metadata discovered from the server before chunk planning begins.
 *
 * @param contentLengthBytes total size of the remote file according to Content-Length
 * @param acceptsRanges whether the server advertises byte-range support
 */
public record DownloadMetadata(long contentLengthBytes, boolean acceptsRanges) {
}
