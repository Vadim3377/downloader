# Changes made for the internship submission archive

## Repository hygiene

- Removed generated files from the submission package.
- Added a stronger `.gitignore` for Maven, IntelliJ, downloaded demo files, reports, and temporary `.part` files.
- Added GitHub Actions CI configuration for `mvn clean verify` on push and pull request.

## Downloader correctness

- Reworked the downloader into a clean Java 17 Maven project.
- Added explicit metadata validation for `Accept-Ranges: bytes` and `Content-Length`.
- Added strict `206 Partial Content` validation for range requests.
- Added `Content-Range` validation for every chunk.
- Added `.part` file output and final atomic move when possible.
- Added SHA-256 calculation and optional expected-checksum validation.
- Kept memory usage bounded by chunk size rather than full file size.

## Tests

- Added embedded HTTP range server for deterministic unit tests.
- Added tests for text files, binary files, zero-byte files, retries, invalid server metadata, invalid range responses, checksum validation, and real parallelism.

## Code comments

Comments were added only where they clarify non-obvious engineering choices:

- why the downloader writes to a `.part` file first;
- why `Content-Range` is validated in addition to HTTP status;
- why parallel writes are safe for disjoint byte ranges;
- why temporary-file cleanup is best-effort in failure paths.
