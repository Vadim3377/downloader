package downloader.core;

/**
 * Receives progress updates from a download implementation.
 *
 * <p>Implementations should be thread-safe if they are invoked from worker
 * threads in a parallel downloader.</p>
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
