package com.benchmark.engine.jlamaserver;

import com.benchmark.core.EngineRunner;
import com.benchmark.core.EngineType;
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
    private static final String HOST =
    System.getProperty(
        "jlamaserver.host",
        System.getenv().getOrDefault("JLAMA_SERVER_HOST", "http://localhost:8080")
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
        HttpRequest request = HttpRequest.newBuilder(URI.create(HOST + "/v1/models"))
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
        long loadStart = System.currentTimeMillis();
        ensureReady(spec);
        long loadTimeMs = System.currentTimeMillis() - loadStart;

        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", spec.modelRef());
        requestBody.put("temperature", spec.temperature());
        requestBody.put("max_tokens", spec.maxTokens());

        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", spec.systemPrompt() != null ? spec.systemPrompt() : DEFAULT_SYSTEM_PROMPT);
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        HttpRequest request = HttpRequest.newBuilder(URI.create(HOST + "/v1/chat/completions"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        long generateStart = System.currentTimeMillis();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long generateTimeMs = System.currentTimeMillis() - generateStart;

        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("JLama respondio con codigo " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        String responseText = json.path("choices").path(0).path("message").path("content").asText("");
        int tokensGenerated = json.path("usage").path("completion_tokens").asInt(estimateTokens(responseText));

        return RunResult.of(type(), spec.modelRef(), prompt, responseText, loadTimeMs, generateTimeMs, tokensGenerated);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    @Override
    public void close() {
        ready = false;
    }
}