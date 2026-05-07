package downloader.validation;

import java.util.List;

public record ValidationReport(
        boolean passed,
        List<ValidationFinding> findings
) {
}
