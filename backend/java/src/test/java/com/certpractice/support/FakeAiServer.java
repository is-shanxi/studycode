package com.certpractice.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal in-process stand-in for the Python internal AI service. Tests can
 * assert the exact payload Java sends and control the response without
 * depending on a separately running Python process.
 */
public final class FakeAiServer implements AutoCloseable {

    private static final String INTERNAL_TOKEN = "local-internal-token";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final AtomicReference<ChatBehavior> chatBehavior =
            new AtomicReference<>(new ChatBehavior(200, "Mock AI answer", 3, 2, 0));
    private final AtomicReference<String> generateResponse =
            new AtomicReference<>(defaultGenerateResponse());
    private final AtomicInteger generateStatus = new AtomicInteger(200);
    private final AtomicReference<JsonNode> lastChatRequest = new AtomicReference<>();
    private final AtomicReference<JsonNode> lastGenerateRequest = new AtomicReference<>();

    private FakeAiServer(final HttpServer server) {
        this.server = server;
    }

    public static FakeAiServer start() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            FakeAiServer fake = new FakeAiServer(server);
            server.createContext("/internal/v1/chat", fake::handleChat);
            server.createContext("/internal/v1/generate-variants", fake::handleGenerate);
            server.setExecutor(Executors.newFixedThreadPool(8));
            server.start();
            return fake;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start fake AI server", exception);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void respondChat(
            final int status,
            final String content,
            final int inputTokens,
            final int outputTokens,
            final long delayMillis) {
        chatBehavior.set(new ChatBehavior(status, content, inputTokens, outputTokens, delayMillis));
    }

    public void respondGenerate(final String json) {
        generateResponse.set(json);
    }

    public void respondGenerateStatus(final int status) {
        generateStatus.set(status);
    }

    public JsonNode lastChatRequest() {
        return lastChatRequest.get();
    }

    public JsonNode lastGenerateRequest() {
        return lastGenerateRequest.get();
    }

    public void reset() {
        chatBehavior.set(new ChatBehavior(200, "Mock AI answer", 3, 2, 0));
        generateResponse.set(defaultGenerateResponse());
        generateStatus.set(200);
        lastChatRequest.set(null);
        lastGenerateRequest.set(null);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handleChat(final HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            write(exchange, 401, "{\"code\":\"UNAUTHORIZED\"}");
            return;
        }
        JsonNode request = OBJECT_MAPPER.readTree(readBody(exchange));
        lastChatRequest.set(request);
        ChatBehavior behavior = chatBehavior.get();
        if (behavior.delayMillis() > 0) {
            try {
                Thread.sleep(behavior.delayMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        if (behavior.status() != 200) {
            write(exchange, behavior.status(), "{\"code\":\"AI_PROVIDER_ERROR\"}");
            return;
        }
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("requestId", request.path("requestId").asText());
        response.put("content", behavior.content());
        ObjectNode usage = response.putObject("usage");
        usage.put("inputTokens", behavior.inputTokens());
        usage.put("outputTokens", behavior.outputTokens());
        response.put("providerRequestId", "fake-provider-request");
        write(exchange, 200, OBJECT_MAPPER.writeValueAsString(response));
    }

    private void handleGenerate(final HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            write(exchange, 401, "{\"code\":\"UNAUTHORIZED\"}");
            return;
        }
        lastGenerateRequest.set(OBJECT_MAPPER.readTree(readBody(exchange)));
        if (generateStatus.get() != 200) {
            write(exchange, generateStatus.get(), "{\"code\":\"AI_PROVIDER_ERROR\"}");
            return;
        }
        write(exchange, 200, generateResponse.get());
    }

    private boolean authorized(final HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst("X-Internal-Token");
        return INTERNAL_TOKEN.equals(token);
    }

    private String readBody(final HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void write(final HttpExchange exchange, final int status, final String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String defaultGenerateResponse() {
        String nonce = UUID.randomUUID().toString();
        ArrayNode candidates = OBJECT_MAPPER.createArrayNode();
        for (int index = 1; index <= 2; index++) {
            ObjectNode candidate = candidates.addObject();
            candidate.put("stem", "Generated variant " + index + " " + nonce);
            candidate.put("type", "SINGLE_CHOICE");
            ArrayNode options = candidate.putArray("options");
            options.addObject().put("key", "A")
                    .put("content", "Option A " + nonce).put("isCorrect", true);
            options.addObject().put("key", "B")
                    .put("content", "Option B " + nonce).put("isCorrect", false);
            candidate.putNull("referenceAnswer");
            candidate.put("explanation", "Generated explanation " + index + " " + nonce);
            candidate.put("difficulty", "MEDIUM");
            candidate.putArray("suggestedTags").add("generated");
        }
        return candidates.toString();
    }

    private record ChatBehavior(
            int status,
            String content,
            int inputTokens,
            int outputTokens,
            long delayMillis) {
    }
}
