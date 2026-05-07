package downloader.core;

/**
 * Checked exception used for downloader-specific failures.
 *
 * Using a domain exception keeps caller-facing error handling separate from
 * lower-level exceptions such as {@link java.io.IOException} or HTTP parsing
 * failures.
 */
public class DownloadException extends Exception {
    public DownloadException(String message) {
        super(message);
    }

    public DownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
