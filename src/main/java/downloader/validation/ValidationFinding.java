package downloader.validation;

public record ValidationFinding(
        Severity severity,
        String fieldPath,
        String trigger,
        String evidence,
        String message
) {
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH
    }
}
