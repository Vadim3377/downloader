package downloader.validation;

public record PrivacyFinding(PrivacyFindingType type, String matchedValue, int startInclusive, int endExclusive) {
}
