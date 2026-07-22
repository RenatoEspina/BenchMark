package com.benchmark.engine.ollama;

import com.benchmark.core.EngineRunner;
import com.benchmark.core.EngineType;
import com.benchmark.core.ModelSpec;
import com.benchmark.core.RunResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class OllamaEngineRunner implements EngineRunner {

    private static final String DEFAULT_SYSTEM_PROMPT = "Eres un asistente conciso.";
    private static final String HOST = System.getProperty("ollama.host", "http://localhost:11434");
    private static final Duration PULL_TIMEOUT = Duration.ofMinutes(Long.getLong("ollama.pullTimeoutMinutes", 30));

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private boolean ready;

    @Override
    public EngineType type() {
        return EngineType.OLLAMA;
    }

    @Override
    public void ensureReady(ModelSpec spec) throws Exception {
        if (ready) {
            return;
        }
        if (!modelExists(spec.modelRef())) {
            pullModel(spec.modelRef());
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
        requestBody.put("prompt", prompt);
        requestBody.put("system", spec.systemPrompt() != null ? spec.systemPrompt() : DEFAULT_SYSTEM_PROMPT);
        requestBody.put("stream", false);

        ObjectNode options = requestBody.putObject("options");
        options.put("temperature", spec.temperature());
        options.put("num_predict", spec.maxTokens());

        HttpRequest request = HttpRequest.newBuilder(URI.create(HOST + "/api/generate"))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        long generateStart = System.currentTimeMillis();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long generateTimeMs = System.currentTimeMillis() - generateStart;

        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Ollama respondio con codigo " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = mapper.readTree(response.body());
        String responseText = json.path("response").asText("");
        int tokensGenerated = json.path("eval_count").asInt(estimateTokens(responseText));

        return RunResult.of(type(), spec.modelRef(), prompt, responseText, loadTimeMs, generateTimeMs, tokensGenerated);
    }

    private boolean modelExists(String modelRef) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", modelRef);

        HttpRequest request = HttpRequest.newBuilder(URI.create(HOST + "/api/show"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.statusCode() == 200;
    }

    private void pullModel(String modelRef) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", modelRef);
        body.put("stream", false);

        HttpRequest request = HttpRequest.newBuilder(URI.create(HOST + "/api/pull"))
                .timeout(PULL_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("No se pudo descargar el modelo " + modelRef + " (codigo " + response.statusCode() + "): " + response.body());
        }
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