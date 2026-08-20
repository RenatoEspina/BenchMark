package com.benchmark.engine.jlama;

import com.benchmark.core.EngineRunner;
import com.benchmark.core.EngineType;
import com.benchmark.core.BenchmarkTiming;
import com.benchmark.core.ModelSpec;
import com.benchmark.core.RunResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class JlamaEngineRunner implements EngineRunner {

    private static final String DEFAULT_SYSTEM_PROMPT = "Eres un asistente conciso.";
    private static final String HOST = normalizeHost(System.getProperty(
        "jlamaserver.host",
        System.getenv().getOrDefault("JLAMA_SERVER_HOST", "http://localhost:8080")
    ));
    private static final String HEALTH_PATH = System.getProperty(
        "jlamaserver.healthPath",
        System.getenv().getOrDefault("JLAMA_SERVER_HEALTH_PATH", "/ui/index.html")
    );

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private boolean ready;

    @Override
    public EngineType type() {
        return EngineType.JLAMA_SERVER;
    }

    @Override
    public void ensureReady(ModelSpec spec) throws Exception {
        if (ready) {
            return;
        }
        // JLama 0.8.4 does not expose /v1/models. Its REST API starts after
        // the model is loaded, and the UI route is the official health check.
        HttpRequest request = HttpRequest.newBuilder(URI.create(HOST + HEALTH_PATH))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("El servidor JLama en " + HOST + " no esta respondiendo (codigo " + response.statusCode() + ")");
        }
        ready = true;
    }

    @Override
    public RunResult run(ModelSpec spec, String prompt) throws Exception {
        long preparationStartNanos = System.nanoTime();
        ensureReady(spec);
        long preparationTimeMs = BenchmarkTiming.elapsedMillis(preparationStartNanos);

        long requestStartNanos = System.nanoTime();
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", spec.modelRef());
        requestBody.put("temperature", spec.temperature());
        requestBody.put("max_tokens", spec.maxTokens());
        requestBody.put("stream", false);

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", spec.systemPrompt() != null ? spec.systemPrompt() : DEFAULT_SYSTEM_PROMPT);
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        HttpRequest request = HttpRequest.newBuilder(URI.create(HOST + "/chat/completions"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("JLama respondio con codigo " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        String responseText = json.path("choices").path(0).path("message").path("content").asText("");
        int tokensGenerated = json.path("usage").path("completion_tokens").asInt(estimateTokens(responseText));
        long requestTimeMs = BenchmarkTiming.elapsedMillis(requestStartNanos);

        return RunResult.of(type(), spec.modelRef(), prompt, responseText, preparationTimeMs, requestTimeMs, tokensGenerated);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return "http://localhost:8080";
        }
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }

    @Override
    public void close() {
        ready = false;
    }
}