package downloader.core;

/**
 * Progress listener implementation used when the caller does not need progress updates.
 */
public final class NoOpProgressListener implements ProgressListener {
    @Override
    public void onProgress(long downloadedBytes, long totalBytes) {
        // Intentionally empty: this object is a safe default no-op implementation.
    }
}
