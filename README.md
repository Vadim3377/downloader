# Parallel File Downloader

This project implements a Java-based file downloader that retrieves a file from a web server by downloading multiple byte ranges in parallel and combining them into a complete local output file.

The solution was developed for the Data Ingestion team task and focuses on correctness, concurrency, diagnostics, and testability.

## Key Functionality

The downloader first sends a `HEAD` request to the provided URL. From this response, it checks that the server supports byte-range requests through the `Accept-Ranges: bytes` header and reads the total file size from `Content-Length`.

After obtaining the file size, the downloader divides the file into byte ranges. Each range is downloaded independently using an HTTP `GET` request with a `Range` header. These chunk downloads are executed in parallel using a fixed-size worker pool.

Each downloaded chunk is written directly into the correct position in the output file using random-access file writing. This avoids storing the whole file in memory and allows the final file to be reconstructed safely even when chunks complete in a different order.

## Reliability Features

The downloader includes retry logic for failed chunk downloads. If a chunk request fails, the downloader retries it up to the configured retry limit before failing the whole download.

It also validates server responses. A chunk request is expected to return `206 Partial Content`; if the server returns an unexpected response, the downloader treats it as an error.

After all chunks complete, the downloader verifies that the final output file has the expected size. It also calculates a SHA-256 hash of the downloaded file. If the user provides an expected SHA-256 value, the downloader compares it against the computed hash.

## Command-Line Interface

The program can be run as a command-line tool. The user provides the source URL, output file path, worker count, retry count, optional chunk size, optional checksum, and optional report paths.

This makes the downloader easy to test locally using a Docker-hosted Apache server or any other HTTP server that supports byte ranges.

## Diagnostic Reports

The downloader can generate diagnostic reports in JSON and Markdown formats.

These reports include the source URL, output path, file size, number of workers, number of chunks, retry count, total duration, downloaded byte count, SHA-256 hash, and per-chunk information such as byte range, attempts, bytes written, and duration.

This report format is intended to be useful for debugging, automated validation, and CI-style feedback.

## Diagnostic Viewer

The project also includes a small static HTML report viewer. It loads the generated JSON report and presents the download result visually.

The viewer shows the overall download status, progress, file size, worker count, chunk count, retry count, duration, checksum information, and a table of individual chunk downloads.

This is not a full frontend application. It is a lightweight diagnostic interface that demonstrates how structured reports could be surfaced to developers.

## Privacy Validation Extension

Because the target project is related to LLM-driven telemetry validation and prevention of PII leaks, the solution also includes a small deterministic validation module.

This module scans telemetry-like data for potentially deanonymizing values such as email addresses, IP addresses, local user paths, API-key-like secrets, JWT-like tokens, and long numeric identifiers.

The purpose of this module is to show how deterministic privacy checks can produce structured findings before being used in a larger LLM-assisted validation workflow.

## Testing

The project includes unit tests for the downloader, utility logic, and privacy validation logic.

The downloader tests use an embedded local HTTP server that simulates the required server behaviour, including `HEAD` responses, byte-range support, ranged `GET` requests, partial-content responses, transient failures, and invalid server responses.

This means the tests do not depend on Docker or an external web server.

The test suite verifies successful text and binary downloads, handling of small files, retry behaviour, missing range support, missing content length, invalid range responses, checksum verification, size parsing, and PII detection.

## Current Status

The implementation has been built and tested successfully. The downloader was also tested manually against a local Apache HTTP server running on `localhost:8080`.

A test file was served locally, downloaded through the parallel downloader, reconstructed correctly, and accompanied by generated JSON and Markdown diagnostic reports.

## Future Improvements

Possible future improvements include resumable downloads, cancellation support, throughput metrics, more detailed error classification, GitHub or GitLab merge-request comment generation, and a fuller LLM-driven telemetry validation quality gate.

````markdown
## End-to-End Local Demo

The following commands demonstrate the full workflow using a local Apache HTTP server through Docker.

```powershell
# 1. Create a local file
echo hello > my-local-file.txt

# 2. Start Apache HTTPD from the project root
docker run --rm -p 8080:80 -v "${PWD}:/usr/local/apache2/htdocs/" httpd:latest
````

Keep the Docker terminal running. Then open a second terminal from the project root:

```powershell
# 3. Verify HEAD request
curl.exe -I http://localhost:8080/my-local-file.txt

# 4. Verify Range request
curl.exe -i -H "Range: bytes=0-9" http://localhost:8080/my-local-file.txt

# 5. Build the project
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd" clean package

# 6. Run the downloader
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\jbr\bin\java.exe" -jar target/parallel-file-downloader.jar --url http://localhost:8080/my-local-file.txt --output downloaded.txt --workers 4 --retries 3 --report-json download-report.json --report-md download-report.md

# 7. Verify output
type downloaded.txt
```

Finally, open the diagnostic report viewer in a browser:

```text
http://localhost:8080/demo/report-viewer.html
```

The viewer automatically loads `download-report.json` from the project root and displays the download diagnostics.

