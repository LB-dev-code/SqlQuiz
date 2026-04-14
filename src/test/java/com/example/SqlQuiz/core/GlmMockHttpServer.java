package com.example.SqlQuiz.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

final class GlmMockHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final String responseBody;
    private volatile String lastRequestBody = "";

    GlmMockHttpServer(String responseBody) {
        try {
            this.responseBody = responseBody;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.server.createContext("/api/paas/v4/chat/completions", this::handle);
            this.server.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start mock GLM server", e);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String lastRequestBody() {
        return lastRequestBody;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            this.lastRequestBody = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
