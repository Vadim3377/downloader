package downloader.validation;

public final class ValidationReportFormatter {

    private ValidationReportFormatter() {
    }

    public static String toMarkdown(ValidationReport report) {
        StringBuilder builder = new StringBuilder();

        builder.append("# Telemetry Validation Report\n\n");

        if (report.passed()) {
            builder.append("✅ Validation passed. No deterministic PII triggers were detected.\n");
            return builder.toString();
        }

        builder.append("❌ Validation failed. Potential deanonymization triggers were detected.\n\n");
        builder.append("| Severity | Field | Trigger | Evidence | Message |\n");
        builder.append("|---|---|---|---|---|\n");

        for (ValidationFinding finding : report.findings()) {
            builder.append("| ")
                    .append(finding.severity())
                    .append(" | `")
                    .append(finding.fieldPath())
                    .append("` | `")
                    .append(finding.trigger())
                    .append("` | `")
                    .append(escapePipes(finding.evidence()))
                    .append("` | ")
                    .append(escapePipes(finding.message()))
                    .append(" |\n");
        }

        return builder.toString();
    }

    private static String escapePipes(String value) {
        return value.replace("|", "\\|");
    }
}
