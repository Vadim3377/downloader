package downloader.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import downloader.core.DownloadReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ReportWriter() {
    }

    public static void writeJson(DownloadReport report, Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        OBJECT_MAPPER.writeValue(path.toFile(), toSerializableMap(report));
    }

    private static Map<String, Object> toSerializableMap(DownloadReport report) {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("url", report.url());
        map.put("outputPath", report.outputPath().toString());
        map.put("fileSizeBytes", report.fileSizeBytes());
        map.put("workers", report.workers());
        map.put("chunksTotal", report.chunksTotal());
        map.put("chunksSucceeded", report.chunksSucceeded());
        map.put("chunksRetried", report.chunksRetried());
        map.put("durationMillis", report.durationMillis());
        map.put("downloadedBytes", report.downloadedBytes());
        map.put("sha256", report.sha256());
        map.put("checksumVerified", report.checksumVerified());
        map.put("startedAt", report.startedAt().toString());
        map.put("finishedAt", report.finishedAt().toString());

        List<Map<String, Object>> chunks = report.chunkReports()
                .stream()
                .map(chunk -> {
                    Map<String, Object> chunkMap = new LinkedHashMap<>();
                    chunkMap.put("index", chunk.index());
                    chunkMap.put("startInclusive", chunk.startInclusive());
                    chunkMap.put("endInclusive", chunk.endInclusive());
                    chunkMap.put("attempts", chunk.attempts());
                    chunkMap.put("bytesWritten", chunk.bytesWritten());
                    chunkMap.put("durationMillis", chunk.durationMillis());
                    return chunkMap;
                })
                .toList();

        map.put("chunkReports", chunks);

        return map;
    }

    public static void writeMarkdown(DownloadReport report, Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# Download Diagnostic Report\n\n");
        builder.append("| Field | Value |\n");
        builder.append("|---|---|\n");
        builder.append("| URL | `").append(report.url()).append("` |\n");
        builder.append("| Output | `").append(report.outputPath()).append("` |\n");
        builder.append("| File size | ").append(report.fileSizeBytes()).append(" bytes |\n");
        builder.append("| Workers | ").append(report.workers()).append(" |\n");
        builder.append("| Chunks total | ").append(report.chunksTotal()).append(" |\n");
        builder.append("| Chunks succeeded | ").append(report.chunksSucceeded()).append(" |\n");
        builder.append("| Chunk retries | ").append(report.chunksRetried()).append(" |\n");
        builder.append("| Downloaded bytes | ").append(report.downloadedBytes()).append(" |\n");
        builder.append("| Duration | ").append(report.durationMillis()).append(" ms |\n");
        builder.append("| SHA-256 | `").append(report.sha256()).append("` |\n");
        builder.append("| Checksum verified | ").append(report.checksumVerified()).append(" |\n\n");

        builder.append("## Chunks\n\n");
        builder.append("| Index | Range | Attempts | Bytes written | Duration |\n");
        builder.append("|---:|---|---:|---:|---:|\n");

        for (DownloadReport.ChunkReport chunk : report.chunkReports()) {
            builder.append("| ")
                    .append(chunk.index())
                    .append(" | ")
                    .append(chunk.startInclusive())
                    .append("-")
                    .append(chunk.endInclusive())
                    .append(" | ")
                    .append(chunk.attempts())
                    .append(" | ")
                    .append(chunk.bytesWritten())
                    .append(" | ")
                    .append(chunk.durationMillis())
                    .append(" ms |\n");
        }

        Files.writeString(path, builder.toString());
    }
}