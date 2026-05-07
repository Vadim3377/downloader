package downloader.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SizeParser {

    private static final Pattern PATTERN = Pattern.compile("^(\\d+)(B|KB|MB|GB)?$", Pattern.CASE_INSENSITIVE);

    private SizeParser() {
    }

    public static long parseBytes(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = PATTERN.matcher(normalized);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid size: " + value);
        }

        long number = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        if (unit == null || unit.equals("B")) {
            return number;
        }

        return switch (unit) {
            case "KB" -> number * 1024L;
            case "MB" -> number * 1024L * 1024L;
            case "GB" -> number * 1024L * 1024L * 1024L;
            default -> throw new IllegalArgumentException("Unsupported size unit: " + unit);
        };
    }
}
