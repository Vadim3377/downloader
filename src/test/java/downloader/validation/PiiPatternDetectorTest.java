package downloader.validation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PiiPatternDetectorTest {

    @Test
    void detectsEmailAndLocalPath() {
        Map<String, Object> event = Map.of(
                "event", "feature.used",
                "metadata", Map.of(
                        "email", "user@example.com",
                        "filePath", "C:\\Users\\Vadim\\project\\Main.java"
                )
        );

        ValidationReport report = new PiiPatternDetector().validate(event);

        assertFalse(report.passed());
        assertTrue(report.findings().stream().anyMatch(f -> f.trigger().equals("EMAIL_ADDRESS")));
        assertTrue(report.findings().stream().anyMatch(f -> f.trigger().equals("WINDOWS_USER_PATH")));
    }

    @Test
    void passesAnonymousEvent() {
        Map<String, Object> event = Map.of(
                "event", "feature.used",
                "metadata", Map.of(
                        "feature", "code-completion",
                        "enabled", "true"
                )
        );

        ValidationReport report = new PiiPatternDetector().validate(event);

        assertTrue(report.passed());
        assertTrue(report.findings().isEmpty());
    }

    @Test
    void formatsFailedReportAsMarkdown() {
        Map<String, Object> event = Map.of(
                "token", "api_key=abcdefghijklmnopqrstuvwxyz123456"
        );

        ValidationReport report = new PiiPatternDetector().validate(event);
        String markdown = ValidationReportFormatter.toMarkdown(report);

        assertTrue(markdown.contains("Validation failed"));
        assertTrue(markdown.contains("API_KEY_LIKE_SECRET"));
    }
}
