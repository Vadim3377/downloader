package downloader.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrivacyValidator {
    private static final List<Rule> RULES = List.of(
            new Rule(PrivacyFindingType.EMAIL_ADDRESS, Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
            new Rule(PrivacyFindingType.IP_ADDRESS, Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")),
            new Rule(PrivacyFindingType.LOCAL_USER_PATH, Pattern.compile("(?:/Users/|/home/|C:\\\\Users\\\\)[^\\s,;]+")),
            new Rule(PrivacyFindingType.API_KEY_LIKE_SECRET, Pattern.compile("(?i)(api[_-]?key|token|secret)[=:]\\s*[A-Za-z0-9_\\-]{16,}")),
            new Rule(PrivacyFindingType.JWT_LIKE_TOKEN, Pattern.compile("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")),
            new Rule(PrivacyFindingType.LONG_NUMERIC_IDENTIFIER, Pattern.compile("\\b\\d{12,}\\b"))
    );

    public List<PrivacyFinding> scan(String text) {
        List<PrivacyFinding> findings = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return findings;
        }

        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(text);
            while (matcher.find()) {
                findings.add(new PrivacyFinding(rule.type(), matcher.group(), matcher.start(), matcher.end()));
            }
        }
        return List.copyOf(findings);
    }

    private record Rule(PrivacyFindingType type, Pattern pattern) {
    }
}
