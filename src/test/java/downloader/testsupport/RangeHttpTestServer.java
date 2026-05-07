package downloader.testsupport;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public final class RangeHttpTestServer implements AutoCloseable {

    private final HttpServer server;
    private final byte[] data;
    private boolean advertiseRanges = true;
    private boolean includeContentLength = true;
    private boolean ignoreRangeAndReturn200 = false;
    private int transientFailuresRemaining = 0;
    private final AtomicInteger requestCount = new AtomicInteger();

    private RangeHttpTestServer(byte[] data) throws IOException {
        this.data = data;
        this.server = HttpServer.create(new InetSocketAddress(0), 0);
        this.server.createContext("/file", exchange -> {
            try {
                handle(exchange.getRequestMethod(), exchange.getRequestHeaders(), exchange.getResponseHeaders(), exchange);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        });
    }

    public static RangeHttpTestServer create(byte[] data) throws IOException {
        return new RangeHttpTestServer(data);
    }

    public RangeHttpTestServer withoutRanges() {
        this.advertiseRanges = false;
        return this;
    }

    public RangeHttpTestServer withoutContentLength() {
        this.includeContentLength = false;
        return this;
    }

    public RangeHttpTestServer returning200ForRange() {
        this.ignoreRangeAndReturn200 = true;
        return this;
    }

    public RangeHttpTestServer withTransientFailures(int count) {
        this.transientFailuresRemaining = count;
        return this;
    }

    public String start() {
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/file";
    }

    public int requestCount() {
        return requestCount.get();
    }

    private void handle(String method, Headers requestHeaders, Headers responseHeaders, com.sun.net.httpserver.HttpExchange exchange)
            throws IOException {

        requestCount.incrementAndGet();

        if (advertiseRanges) {
            responseHeaders.add("Accept-Ranges", "bytes");
        }

        if (includeContentLength) {
            responseHeaders.add("Content-Length", String.valueOf(data.length));
        }

        if ("HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        if (!"GET".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        if (transientFailuresRemaining > 0) {
            transientFailuresRemaining--;
            exchange.sendResponseHeaders(503, -1);
            return;
        }

        if (ignoreRangeAndReturn200) {
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(data);
            }
            return;
        }

        String rangeHeader = requestHeaders.getFirst("Range");

        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        Range range = parseRange(rangeHeader, data.length);
        byte[] chunk = Arrays.copyOfRange(data, (int) range.start(), (int) range.end() + 1);

        responseHeaders.add("Content-Range", "bytes " + range.start() + "-" + range.end() + "/" + data.length);
        exchange.sendResponseHeaders(206, chunk.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(chunk);
        }
    }

    private Range parseRange(String header, int fileSize) {
        String value = header.substring("bytes=".length());
        String[] parts = value.split("-");
        long start = Long.parseLong(parts[0]);
        long end = Long.parseLong(parts[1]);

        if (start < 0 || end >= fileSize || start > end) {
            throw new IllegalArgumentException("Invalid range: " + header);
        }

        return new Range(start, end);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private record Range(long start, long end) {
    }
}
