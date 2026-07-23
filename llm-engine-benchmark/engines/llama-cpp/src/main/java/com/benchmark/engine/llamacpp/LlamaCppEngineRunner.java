package com.benchmark.engine.llamacpp;

import com.benchmark.core.EngineRunner;
import com.benchmark.core.EngineType;
import com.benchmark.core.ModelResolver;
import com.benchmark.core.ModelSpec;
import com.benchmark.core.RunResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LlamaCppEngineRunner implements EngineRunner {

    private static final String DEFAULT_SYSTEM_PROMPT = "Eres un asistente conciso.";
    private static final String BINARY = System.getProperty("llamacpp.binary", "llama-cli");
    private static final int DEFAULT_CTX_SIZE = Integer.getInteger("llamacpp.ctxSize", 4096);
    private static final int DEFAULT_N_GPU_LAYERS = Integer.getInteger("llamacpp.nGpuLayers", 0);

    private Path modelPath;

    @Override
    public EngineType type() {
        return EngineType.LLAMA_CPP;
    }

    @Override
    public void ensureReady(ModelSpec spec) throws Exception {
        if (modelPath != null) {
            return;
        }
        modelPath = ModelResolver.resolve(spec.modelRef(), spec.workDir());
    }

    @Override
    public RunResult run(ModelSpec spec, String prompt) throws Exception {
        long loadStart = System.currentTimeMillis();
        ensureReady(spec);
        long loadTimeMs = System.currentTimeMillis() - loadStart;

        List<String> command = buildCommand(spec, prompt);

        long generateStart = System.currentTimeMillis();
        String responseText = executeProcess(command);
        long generateTimeMs = System.currentTimeMillis() - generateStart;

        int tokensGenerated = estimateTokens(responseText);
        return RunResult.of(type(), spec.modelRef(), prompt, responseText.strip(), loadTimeMs, generateTimeMs, tokensGenerated);
    }

    private List<String> buildCommand(ModelSpec spec, String prompt) {
        String systemPrompt = spec.systemPrompt() != null ? spec.systemPrompt() : DEFAULT_SYSTEM_PROMPT;
        List<String> command = new ArrayList<>();
        command.add(BINARY);
        command.add("-m");
        command.add(modelPath.toString());
        command.add("-sys");
        command.add(systemPrompt);
        command.add("-p");
        command.add(prompt);
        command.add("-cnv");
        command.add("-st");
        command.add("-n");
        command.add(String.valueOf(spec.maxTokens()));
        command.add("--temp");
        command.add(String.valueOf(spec.temperature()));
        command.add("-c");
        command.add(String.valueOf(DEFAULT_CTX_SIZE));
        if (DEFAULT_N_GPU_LAYERS > 0) {
            command.add("-ngl");
            command.add(String.valueOf(DEFAULT_N_GPU_LAYERS));
        }
        command.add("--no-display-prompt");
        command.add("--simple-io");
        command.add("--no-warmup");
        command.add("--no-perf");
        command.add("--no-show-timings");
        return command;
    }

    private String executeProcess(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = processBuilder.start();

        Thread stderrDrain = new Thread(() -> {
            try (InputStream err = process.getErrorStream()) {
                err.readAllBytes();
            } catch (IOException ignored) {
            }
        });
        stderrDrain.setDaemon(true);
        stderrDrain.start();

        String responseText;
        try (InputStream out = process.getInputStream()) {
            responseText = new String(out.readAllBytes(), StandardCharsets.UTF_8);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(BINARY + " finalizo con codigo " + exitCode);
        }
        return responseText;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    @Override
    public void close() {
        modelPath = null;
    }
}