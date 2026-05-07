package downloader.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import downloader.core.DownloadReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReportWriter {
    private final ObjectMapper objectMapper = new ObjectMapper()
            // DownloadReport contains java.time.Duration values. Jackson needs the JavaTimeModule
            // explicitly registered so JSON report generation works on a normal shaded CLI jar.
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void writeJson(DownloadReport report, Path path) throws IOException {
        ensureParentExists(path);
        objectMapper.writeValue(path.toFile(), report);
    }

    public void writeMarkdown(DownloadReport report, Path path) throws IOException {
        ensureParentExists(path);
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Download Report\n\n");
        markdown.append("| Field | Value |\n");
        markdown.append("|---|---|\n");
        markdown.append("| URL | `").append(report.url()).append("` |\n");
        markdown.append("| Output | `").append(report.outputPath()).append("` |\n");
        markdown.append("| File size | ").append(report.fileSizeBytes()).append(" bytes |\n");
        markdown.append("| Workers | ").append(report.workers()).append(" |\n");
        markdown.append("| Chunks | ").append(report.chunkCount()).append(" |\n");
        markdown.append("| Duration | ").append(report.duration().toMillis()).append(" ms |\n");
        markdown.append("| SHA-256 | `").append(report.sha256()).append("` |\n\n");

        markdown.append("## Chunks\n\n");
        markdown.append("| Index | Range | Bytes | Attempts | Duration |\n");
        markdown.append("|---:|---|---:|---:|---:|\n");
        for (DownloadReport.ChunkReport chunk : report.chunks()) {
            markdown.append("| ").append(chunk.index())
                    .append(" | `").append(chunk.startInclusive()).append("-").append(chunk.endInclusive()).append("`")
                    .append(" | ").append(chunk.bytesWritten())
                    .append(" | ").append(chunk.attempts())
                    .append(" | ").append(chunk.duration().toMillis()).append(" ms |\n");
        }
        Files.writeString(path, markdown.toString());
    }

    private void ensureParentExists(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
