package downloader.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static downloader.validation.ValidationFinding.Severity.HIGH;
import static downloader.validation.ValidationFinding.Severity.MEDIUM;

public final class PiiPatternDetector {

    private static final List<Rule> RULES = List.of(
            new Rule(
                    "EMAIL_ADDRESS",
                    HIGH,
                    Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", Pattern.CASE_INSENSITIVE),
                    "Value appears to contain an email address."
            ),
            new Rule(
                    "IPV4_ADDRESS",
                    MEDIUM,
                    Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
                    "Value appears to contain an IPv4 address."
            ),
            new Rule(
                    "WINDOWS_USER_PATH",
                    HIGH,
                    Pattern.compile("(?i)[A-Z]:\\\\Users\\\\[^\\\\\\s]+\\\\[^\\s]*"),
                    "Value appears to contain a Windows local user path."
            ),
            new Rule(
                    "UNIX_HOME_PATH",
                    HIGH,
                    Pattern.compile("(?i)/(home|users)/[^/\\s]+/[^\\s]*"),
                    "Value appears to contain a Unix/macOS local user path."
            ),
            new Rule(
                    "JWT_TOKEN",
                    HIGH,
                    Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"),
                    "Value appears to contain a JWT-like token."
            ),
            new Rule(
                    "API_KEY_LIKE_SECRET",
                    HIGH,
                    Pattern.compile("(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*['\"]?[A-Za-z0-9_\\-]{16,}"),
                    "Value appears to contain a secret or API-key-like token."
            ),
            new Rule(
                    "LONG_NUMERIC_IDENTIFIER",
                    MEDIUM,
                    Pattern.compile("\\b\\d{12,}\\b"),
                    "Value contains a long numeric identifier that may be deanonymizing."
            )
    );

    public ValidationReport validate(Map<String, ?> event) {
        List<ValidationFinding> findings = new ArrayList<>();
        walk("$", event, findings);
        return new ValidationReport(findings.isEmpty(), List.copyOf(findings));
    }

    @SuppressWarnings("unchecked")
    private void walk(String path, Object value, List<ValidationFinding> findings) {
        if (value == null) {
            return;
        }

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                walk(path + "." + entry.getKey(), entry.getValue(), findings);
            }
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object item : iterable) {
                walk(path + "[" + index + "]", item, findings);
                index++;
            }
            return;
        }

        if (value instanceof CharSequence sequence) {
            checkString(path, sequence.toString(), findings);
        }
    }

    private void checkString(String path, String value, List<ValidationFinding> findings) {
        for (Rule rule : RULES) {
            var matcher = rule.pattern().matcher(value);
            if (matcher.find()) {
                findings.add(new ValidationFinding(
                        rule.severity(),
                        path,
                        rule.trigger(),
                        abbreviate(matcher.group()),
                        rule.message()
                ));
            }
        }
    }

    private String abbreviate(String evidence) {
        if (evidence.length() <= 80) {
            return evidence;
        }
        return evidence.substring(0, 77) + "...";
    }

    private record Rule(
            String trigger,
            ValidationFinding.Severity severity,
            Pattern pattern,
            String message
    ) {
    }
}
