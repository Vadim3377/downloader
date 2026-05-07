package downloader.testsupport;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class RangeHttpTestServer implements Closeable {
    private final byte[] content;
    private final boolean acceptRanges;
    private final boolean includeContentLength;
    private final boolean returnOkForRange;
    private final boolean wrongContentRange;
    private final int failuresBeforeSuccess;
    private final long perRequestDelayMillis;
    private final AtomicInteger requests = new AtomicInteger();
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private RangeHttpTestServer(Builder builder) throws IOException {
        this.content = builder.content;
        this.acceptRanges = builder.acceptRanges;
        this.includeContentLength = builder.includeContentLength;
        this.returnOkForRange = builder.returnOkForRange;
        this.wrongContentRange = builder.wrongContentRange;
        this.failuresBeforeSuccess = builder.failuresBeforeSuccess;
        this.perRequestDelayMillis = builder.perRequestDelayMillis;
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        this.server.createContext("/file", this::handle);
        this.server.setExecutor(executor);
    }

    public static Builder builder(byte[] content) {
        return new Builder(content);
    }

    public String start() {
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/file";
    }

    public int requestCount() {
        return requests.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        try {
            if (perRequestDelayMillis > 0 && "GET".equals(exchange.getRequestMethod())) {
                Thread.sleep(perRequestDelayMillis);
            }
            if ("HEAD".equals(exchange.getRequestMethod())) {
                sendHead(exchange);
            } else if ("GET".equals(exchange.getRequestMethod())) {
                sendGet(exchange);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exchange.sendResponseHeaders(500, -1);
        } finally {
            exchange.close();
        }
    }

    private void sendHead(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        if (acceptRanges) {
            headers.add("Accept-Ranges", "bytes");
        }
        if (includeContentLength) {
            headers.add("Content-Length", String.valueOf(content.length));
        }
        exchange.sendResponseHeaders(200, -1);
    }

    private void sendGet(HttpExchange exchange) throws IOException {
        if (failuresBeforeSuccess > 0 && requests.get() <= failuresBeforeSuccess + 1) {
            byte[] failure = "temporary failure".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, failure.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(failure);
            }
            return;
        }

        String range = exchange.getRequestHeaders().getFirst("Range");
        if (range == null || !range.startsWith("bytes=")) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }

        String[] parts = range.substring("bytes=".length()).split("-", 2);
        int start = Integer.parseInt(parts[0]);
        int end = Integer.parseInt(parts[1]);
        byte[] response = Arrays.copyOfRange(content, start, end + 1);

        Headers headers = exchange.getResponseHeaders();
        headers.add("Accept-Ranges", "bytes");
        String contentRange = wrongContentRange ? "bytes 0-" + (response.length - 1) + "/" + content.length
                : "bytes " + start + "-" + end + "/" + content.length;
        headers.add("Content-Range", contentRange);
        headers.add("Content-Length", String.valueOf(response.length));

        if (returnOkForRange) {
            exchange.sendResponseHeaders(200, response.length);
        } else {
            exchange.sendResponseHeaders(206, response.length);
        }
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    public static final class Builder {
        private final byte[] content;
        private boolean acceptRanges = true;
        private boolean includeContentLength = true;
        private boolean returnOkForRange;
        private boolean wrongContentRange;
        private int failuresBeforeSuccess;
        private long perRequestDelayMillis;

        private Builder(byte[] content) {
            this.content = content;
        }

        public Builder acceptRanges(boolean acceptRanges) {
            this.acceptRanges = acceptRanges;
            return this;
        }

        public Builder includeContentLength(boolean includeContentLength) {
            this.includeContentLength = includeContentLength;
            return this;
        }

        public Builder returnOkForRange(boolean returnOkForRange) {
            this.returnOkForRange = returnOkForRange;
            return this;
        }

        public Builder wrongContentRange(boolean wrongContentRange) {
            this.wrongContentRange = wrongContentRange;
            return this;
        }

        public Builder failuresBeforeSuccess(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
            return this;
        }

        public Builder perRequestDelayMillis(long perRequestDelayMillis) {
            this.perRequestDelayMillis = perRequestDelayMillis;
            return this;
        }

        public RangeHttpTestServer build() throws IOException {
            return new RangeHttpTestServer(this);
        }
    }
}
