package downloader.core;

/**
 * Receives progress updates from a download implementation.
 *
 * Implementations should be thread-safe if they are invoked from worker
 * threads in a parallel downloader.
 */
@FunctionalInterface
public interface ProgressListener {
    /**
     * Called when the number of downloaded bytes changes.
     *
     * @param downloadedBytes number of bytes successfully written so far
     * @param totalBytes expected total file size
     */
    void onProgress(long downloadedBytes, long totalBytes);
}
