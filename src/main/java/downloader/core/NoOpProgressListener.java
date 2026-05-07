package downloader.core;

public final class NoOpProgressListener implements ProgressListener {
    @Override
    public void onProgress(long downloadedBytes, long totalBytes) {
        // intentionally empty
    }
}
