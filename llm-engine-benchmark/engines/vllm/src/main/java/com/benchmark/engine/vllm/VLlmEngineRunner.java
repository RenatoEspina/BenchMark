package com.benchmark.engine.vllm;

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

public final class VLlmEngineRunner implements EngineRunner {

    private static final String DEFAULT_SYSTEM_PROMPT = "Eres un asistente conciso.";
    private static final String HOST = System.getProperty("vllm.host", "http://localhost:8000");
    private static final String API_KEY = System.getProperty("vllm.apiKey");

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private boolean ready;

    @Override
    public EngineType type() {
        return EngineType.VLLM;
    }

    @Override
    public void ensureReady(ModelSpec spec) throws Exception {
        if (ready) {
            return;
        }
        if (!modelServed(spec.modelRef())) {
            throw new IllegalStateException("El servidor vLLM en " + HOST + " no esta sirviendo el modelo " + spec.modelRef());
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

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(HOST + "/v1/chat/completions"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json");
        if (API_KEY != null && !API_KEY.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + API_KEY);
        }
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        long generateStart = System.currentTimeMillis();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long generateTimeMs = System.currentTimeMillis() - generateStart;

        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("vLLM respondio con codigo " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        String responseText = json.path("choices").path(0).path("message").path("content").asText("");
        int tokensGenerated = json.path("usage").path("completion_tokens").asInt(estimateTokens(responseText));

        return RunResult.of(type(), spec.modelRef(), prompt, responseText, loadTimeMs, generateTimeMs, tokensGenerated);
    }

    private boolean modelServed(String modelRef) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(HOST + "/v1/models"))
                .timeout(Duration.ofSeconds(30))
                .GET();
        if (API_KEY != null && !API_KEY.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + API_KEY);
        }

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            return false;
        }

        JsonNode json = mapper.readTree(response.body());
        for (JsonNode model : json.path("data")) {
            if (modelRef.equals(model.path("id").asText())) {
                return true;
            }
        }
        return false;
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
