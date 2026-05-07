# Parallel File Downloader

Java 17 implementation of a parallel HTTP range downloader.

The downloader:

- sends a `HEAD` request to validate that the server supports byte ranges;
- reads `Content-Length` to determine the file size;
- splits the remote file into chunks;
- downloads chunks concurrently using HTTP `Range` requests;
- writes chunks into the correct offsets of a temporary `.part` file;
- validates the final file size and SHA-256 checksum;
- optionally produces JSON and Markdown diagnostic reports.

This project was prepared for the JetBrains Data Ingestion internship task.

Preferred project: **LLM-Driven Validation Strategy**.

---

## Requirements

You need:

- Java 17 or newer;
- Maven 3.8+;
- Docker, only for the local Apache test server.

Check Java:

```bash
java -version
```

Expected output should contain Java 17 or newer, for example:

```text
openjdk version "17.0.x"
```

If you see Java 8, for example:

```text
java version "1.8.0_xxx"
```

then the JAR will not run. Install Java 17 first.

On Windows, one option is:

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
```

Then close and reopen PowerShell and check again:

```powershell
java -version
```

---

## Step 1: Build the project

From the project root, run:

```bash
mvn clean package
```

This creates the executable JAR:

```text
target/parallel-file-downloader.jar
```

You can also run the full test suite:

```bash
mvn clean verify
```

---

## Step 2: Create a local file to download

From the project root, create a test file.

On Linux/macOS/Git Bash:

```bash
echo hello > my-local-file.txt
```

On Windows PowerShell:

```powershell
Set-Content -Path my-local-file.txt -Value "hello"
```

The file should now exist in the current project directory.

---

## Step 3: Start the local Apache web server

Open **Terminal 1** in the project root.

### Windows PowerShell

Use this command:

```powershell
docker run --rm -p 8080:80 -v "${PWD}:/usr/local/apache2/htdocs/" httpd:latest
```

Important: keep this terminal open. The server runs in the foreground.

### Linux/macOS/Git Bash

Use this command:

```bash
docker run --rm -p 8080:80 -v "$(pwd):/usr/local/apache2/htdocs/" httpd:latest
```

Important: keep this terminal open. The server runs in the foreground.

---

## Step 4: Verify that the server works

Open **Terminal 2** in the same project root.

First, check the `HEAD` response.

On Windows PowerShell, use `curl.exe` rather than PowerShell's `curl` alias:

```powershell
curl.exe -I http://localhost:8080/my-local-file.txt
```

On Linux/macOS/Git Bash:

```bash
curl -I http://localhost:8080/my-local-file.txt
```

Expected response should contain:

```text
HTTP/1.1 200 OK
Accept-Ranges: bytes
Content-Length: ...
```

Then verify that range requests work.

Windows PowerShell:

```powershell
curl.exe -H "Range: bytes=0-2" http://localhost:8080/my-local-file.txt
```

Linux/macOS/Git Bash:

```bash
curl -H "Range: bytes=0-2" http://localhost:8080/my-local-file.txt
```

Expected output should be the first bytes of the file, for example:

```text
hel
```

If this step fails, do not run the downloader yet. Fix the local server first.

---

## Step 5: Run the downloader

Run this from **Terminal 2**, while the Apache server is still running in Terminal 1.

### Windows PowerShell

```powershell
java -jar target/parallel-file-downloader.jar `
  --url http://localhost:8080/my-local-file.txt `
  --output downloaded.txt `
  --workers 4 `
  --retries 3 `
  --report-json download-report.json `
  --report-md download-report.md
```

### Linux/macOS/Git Bash

```bash
java -jar target/parallel-file-downloader.jar \
  --url http://localhost:8080/my-local-file.txt \
  --output downloaded.txt \
  --workers 4 \
  --retries 3 \
  --report-json download-report.json \
  --report-md download-report.md
```

Expected result:

```text
Download completed successfully
```

The following files should be created:

```text
downloaded.txt
download-report.json
download-report.md
```

Check the downloaded file:

Windows PowerShell:

```powershell
Get-Content downloaded.txt
```

Linux/macOS/Git Bash:

```bash
cat downloaded.txt
```

Expected output:

```text
hello
```

---

## Viewing the JSON report in the HTML report viewer

The project also includes a small browser-based report viewer:

```text
demo/report-viewer.html
```

It loads the generated `download-report.json` file and displays the download summary and chunk table in a readable HTML page.

This is optional, but it is useful for demonstrating the diagnostic-reporting part of the solution.

### Step 1: Generate the JSON report

First run the downloader with the `--report-json` option.

### Windows PowerShell

```powershell
java -jar target/parallel-file-downloader.jar `
  --url http://localhost:8080/my-local-file.txt `
  --output downloaded.txt `
  --workers 4 `
  --retries 3 `
  --report-json download-report.json `
  --report-md download-report.md
```

### Linux/macOS/Git Bash

```bash
java -jar target/parallel-file-downloader.jar \
  --url http://localhost:8080/my-local-file.txt \
  --output downloaded.txt \
  --workers 4 \
  --retries 3 \
  --report-json download-report.json \
  --report-md download-report.md
```

After this command, the project root should contain:

```text
download-report.json
download-report.md
```

### Step 2: Open the HTML viewer through Apache

The viewer is designed to be opened through the local Apache server, not directly from the filesystem.

Make sure the Apache container is still running from the project root:

### Windows PowerShell

```powershell
docker run --rm -p 8080:80 -v "${PWD}:/usr/local/apache2/htdocs/" httpd:latest
```

### Linux/macOS/Git Bash

```bash
docker run --rm -p 8080:80 -v "$(pwd):/usr/local/apache2/htdocs/" httpd:latest
```

Then open this URL in your browser:

```text
http://localhost:8080/demo/report-viewer.html
```

The page should show:

- source URL;
- output path;
- file size;
- number of chunks;
- SHA-256 checksum;
- per-chunk byte ranges and retry attempts.

### Important path assumption

`demo/report-viewer.html` fetches:

```text
../download-report.json
```

So the expected project layout is:

```text
project-root/
├── download-report.json
└── demo/
    └── report-viewer.html
```

If you save the JSON report somewhere else, either move it to the project root or update the `fetch(...)` path inside `report-viewer.html`.

---

## Step 6: Try a larger binary file

Small text files are useful for a smoke test, but parallel range downloading is more meaningful with a larger file.

### Windows PowerShell

Create a 5 MB binary file:

```powershell
$bytes = New-Object byte[] (5MB)
[System.Random]::new().NextBytes($bytes)
[System.IO.File]::WriteAllBytes("sample.bin", $bytes)
```

Download it:

```powershell
java -jar target/parallel-file-downloader.jar `
  --url http://localhost:8080/sample.bin `
  --output sample-downloaded.bin `
  --workers 4 `
  --chunk-size 1048576 `
  --retries 3 `
  --report-json download-report.json `
  --report-md download-report.md
```

Verify equality:

```powershell
certutil -hashfile sample.bin SHA256
certutil -hashfile sample-downloaded.bin SHA256
```

The two SHA-256 hashes should match.

### Linux/macOS/Git Bash

Create a 5 MB binary file:

```bash
dd if=/dev/urandom of=sample.bin bs=1M count=5
```

Download it:

```bash
java -jar target/parallel-file-downloader.jar \
  --url http://localhost:8080/sample.bin \
  --output sample-downloaded.bin \
  --workers 4 \
  --chunk-size 1048576 \
  --retries 3 \
  --report-json download-report.json \
  --report-md download-report.md
```

Verify equality:

```bash
sha256sum sample.bin sample-downloaded.bin
```

The two SHA-256 hashes should match.

---

## Command-line options

```text
--url <url>                    Required. Source file URL.
--output <path>                Required. Destination file path.
--workers <n>                  Optional. Number of parallel workers. Default: 4.
--chunk-size <bytes>           Optional. Chunk size in bytes. Default: 1048576.
--retries <n>                  Optional. Retry attempts per chunk. Default: 3.
--report-json <path>           Optional. Write JSON diagnostic report.
--report-md <path>             Optional. Write Markdown diagnostic report.
```

Example:

```bash
java -jar target/parallel-file-downloader.jar \
  --url http://localhost:8080/sample.bin \
  --output sample-downloaded.bin \
  --workers 4 \
  --chunk-size 1048576 \
  --retries 3 \
  --report-json download-report.json \
  --report-md download-report.md
```

---

## Common problems

### `UnsupportedClassVersionError`

Example:

```text
class file version 61.0, this version of the Java Runtime only recognizes class file versions up to 52.0
```

Cause: the JAR was compiled for Java 17, but the terminal is running Java 8.

Fix:

```bash
java -version
```

Install Java 17 or update your `PATH` so that Java 17 is first.

On Windows:

```powershell
where java
```

If Java 8 appears before Java 17, update the system `PATH`, or call Java 17 directly using its full path.

---

### `Download failed: HEAD request failed`

Cause: the downloader could not complete the initial `HEAD` request.

Check the following:

1. Is Docker running?
2. Is the Apache container still running in Terminal 1?
3. Does the file exist in the directory mounted into Apache?
4. Does this command work?

Windows PowerShell:

```powershell
curl.exe -I http://localhost:8080/my-local-file.txt
```

Linux/macOS/Git Bash:

```bash
curl -I http://localhost:8080/my-local-file.txt
```

If you receive `404 Not Found`, the file is not in the directory being served.

If you receive `Connection refused`, the Apache container is not running or port `8080` is unavailable.

---

### Docker error: `invalid reference format`

This usually happens on Windows when the project path contains spaces and the volume mount is not quoted.

Incorrect:

```powershell
docker run --rm -p 8080:80 -v C:\Users\Vadim\IdeaProjects\LLM-Driven Validation Strategy:/usr/local/apache2/htdocs/ httpd:latest
```

Correct:

```powershell
docker run --rm -p 8080:80 -v "${PWD}:/usr/local/apache2/htdocs/" httpd:latest
```

---

### PowerShell `curl` behaves strangely

In Windows PowerShell, `curl` may be an alias for `Invoke-WebRequest`.

Use this instead:

```powershell
curl.exe -I http://localhost:8080/my-local-file.txt
```

---

## Architecture

The main components are:

```text
downloader.cli.Main
```

Parses command-line arguments and calls the downloader.

```text
downloader.core.ParallelFileDownloader
```

Coordinates metadata validation, chunk planning, parallel execution, file writing, and final verification.

```text
downloader.core.DownloadConfig
```

Immutable configuration object for one download.

```text
downloader.core.DownloadReport
```

Structured result containing downloaded byte count, chunk details, duration, checksum, and diagnostics.

```text
downloader.report.ReportWriters
```

Writes JSON and Markdown reports.

```text
downloader.privacy.PrivacyFinding
```

Small deterministic privacy-signal model used for the optional Data Ingestion-oriented validation extension.

---

## Correctness guarantees

The downloader checks:

- the server responds to `HEAD` successfully;
- `Accept-Ranges: bytes` is present;
- `Content-Length` is present and valid;
- each chunk request receives `206 Partial Content`;
- each chunk response has the expected `Content-Range`;
- each chunk has the expected number of bytes;
- the final file size equals the remote `Content-Length`;
- the final file hash is reported as SHA-256.

The output is first written to:

```text
<output>.part
```

Only after successful verification is it moved to the requested output path.

This prevents a failed download from being mistaken for a complete file.

---

## Test coverage

The test suite uses an embedded HTTP server and verifies:

- successful text file download;
- successful binary file download;
- zero-byte file handling;
- missing `Accept-Ranges` rejection;
- missing `Content-Length` rejection;
- invalid range response rejection;
- retry behaviour after transient failures;
- checksum correctness;
- actual parallel downloading behaviour using delayed chunk responses.

Run:

```bash
mvn clean verify
```

---

## Relation to LLM-Driven Validation Strategy

The core assignment is the parallel file downloader.

Because my preferred Data Ingestion project is **LLM-Driven Validation Strategy**, the project also includes a small deterministic privacy-validation component. It is intentionally not presented as a complete LLM system. Instead, it demonstrates the kind of pre-LLM quality gate that can detect obvious privacy risks before an LLM-based reviewer produces a richer diagnostic report.

This mirrors the intended validation workflow:

1. deterministic trigger detection;
2. structured findings;
3. report generation;
4. potential CI or merge-request feedback integration.

Examples of deterministic signals include email-like strings, local filesystem paths, long numeric identifiers, token-like values, and other values that may be inappropriate in telemetry payloads.

---

## Repository hygiene

Generated files should not be committed.

Ignored examples include:

```text
target/
.idea/
*.iml
*.class
*.jar
downloaded.*
download-report.*
*.part
```

The repository should remain buildable with:

```bash
mvn clean verify
```
