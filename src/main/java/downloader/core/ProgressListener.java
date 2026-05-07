package downloader.core;

@FunctionalInterface
public interface ProgressListener {
    void onProgress(long downloadedBytes, long totalBytes);
}
