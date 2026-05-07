package downloader.cli;

import downloader.core.DownloadConfig;
import downloader.core.DownloadReport;
import downloader.core.ParallelFileDownloader;
import downloader.core.ProgressListener;
import downloader.report.ReportWriter;
import downloader.util.SizeParser;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        try {
            Map<String, String> options = parseArgs(args);

            if (!options.containsKey("--url") || !options.containsKey("--output")) {
                printUsage();
                System.exit(1);
            }

            String url = options.get("--url");
            Path output = Path.of(options.get("--output"));

            int workers = Integer.parseInt(options.getOrDefault("--workers", "4"));
            int retries = Integer.parseInt(options.getOrDefault("--retries", "3"));
            int timeoutSeconds = Integer.parseInt(options.getOrDefault("--timeout", "30"));

            Long chunkSizeBytes = null;
            if (options.containsKey("--chunk-size")) {
                chunkSizeBytes = SizeParser.parseBytes(options.get("--chunk-size"));
            }

            String expectedSha256 = options.get("--sha256");

            DownloadConfig config = DownloadConfig.builder()
                    .url(url)
                    .outputPath(output)
                    .workers(workers)
                    .chunkSizeBytes(chunkSizeBytes)
                    .maxRetries(retries)
                    .requestTimeout(Duration.ofSeconds(timeoutSeconds))
                    .expectedSha256(expectedSha256)
                    .build();

            ProgressListener progress = (downloaded, total) -> {
                if (total > 0) {
                    double percent = downloaded * 100.0 / total;
                    System.out.printf(
                            "\rDownloaded %,d / %,d bytes (%.2f%%)",
                            downloaded,
                            total,
                            percent
                    );
                }
            };

            ParallelFileDownloader downloader = new ParallelFileDownloader();
            DownloadReport report = downloader.download(config, progress);

            System.out.println();
            System.out.println("Download completed: " + output);
            System.out.printf("Duration: %d ms%n", report.durationMillis());
            System.out.printf("Chunks: %d succeeded, %d retries%n",
                    report.chunksSucceeded(),
                    report.chunksRetried()
            );

            if (report.sha256() != null) {
                System.out.println("SHA-256: " + report.sha256());
            }

            if (options.containsKey("--report-json")) {
                Path reportJson = Path.of(options.get("--report-json"));
                ReportWriter.writeJson(report, reportJson);
                System.out.println("JSON report written to: " + reportJson);
            }

            if (options.containsKey("--report-md")) {
                Path reportMarkdown = Path.of(options.get("--report-md"));
                ReportWriter.writeMarkdown(report, reportMarkdown);
                System.out.println("Markdown report written to: " + reportMarkdown);
            }

        } catch (Exception e) {
            System.err.println();
            System.err.println("Download failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            String key = args[i];

            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + key);
            }

            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for option: " + key);
            }

            options.put(key, args[i + 1]);
            i++;
        }

        return options;
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("java -jar target/parallel-file-downloader.jar \\");
        System.err.println("  --url <source-url> \\");
        System.err.println("  --output <output-file> \\");
        System.err.println("  [--workers 4] \\");
        System.err.println("  [--chunk-size 1MB] \\");
        System.err.println("  [--retries 3] \\");
        System.err.println("  [--timeout 30] \\");
        System.err.println("  [--sha256 <expected-sha256>] \\");
        System.err.println("  [--report-json report.json] \\");
        System.err.println("  [--report-md report.md]");
    }
}