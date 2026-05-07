package downloader.cli;

import downloader.core.DownloadConfig;
import downloader.core.DownloadException;
import downloader.core.DownloadReport;
import downloader.core.ParallelFileDownloader;
import downloader.report.ReportWriter;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        if (options.containsKey("help") || !options.containsKey("url") || !options.containsKey("output")) {
            printUsage();
            return;
        }

        DownloadConfig config = DownloadConfig.builder()
                .url(options.get("url"))
                .outputPath(Path.of(options.get("output")))
                .workers(parseInt(options, "workers", 4))
                .chunkSizeBytes(parseLong(options, "chunk-size", 1024 * 1024L))
                .maxRetries(parseInt(options, "retries", 3))
                .requestTimeout(Duration.ofSeconds(parseLong(options, "timeout-seconds", 30)))
                .expectedSha256(options.get("expected-sha256"))
                .build();

        try {
            DownloadReport report = new ParallelFileDownloader().download(config);
            ReportWriter reportWriter = new ReportWriter();
            if (options.containsKey("report-json")) {
                reportWriter.writeJson(report, Path.of(options.get("report-json")));
            }
            if (options.containsKey("report-md")) {
                reportWriter.writeMarkdown(report, Path.of(options.get("report-md")));
            }
            System.out.println("Downloaded " + report.downloadedBytes() + " bytes to " + report.outputPath());
            System.out.println("SHA-256: " + report.sha256());
        } catch (DownloadException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Download failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
            String key = arg.substring(2);
            if (key.equals("help")) {
                options.put("help", "true");
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Missing value for --" + key);
            }
            options.put(key, args[++i]);
        }
        return options;
    }

    private static int parseInt(Map<String, String> options, String key, int defaultValue) {
        return options.containsKey(key) ? Integer.parseInt(options.get(key)) : defaultValue;
    }

    private static long parseLong(Map<String, String> options, String key, long defaultValue) {
        return options.containsKey(key) ? Long.parseLong(options.get(key)) : defaultValue;
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java -jar target/parallel-file-downloader.jar \
                    --url <url> \
                    --output <path> \
                    [--workers 4] \
                    [--chunk-size 1048576] \
                    [--retries 3] \
                    [--timeout-seconds 30] \
                    [--expected-sha256 <hex>] \
                    [--report-json download-report.json] \
                    [--report-md download-report.md]
                """);
    }
}
