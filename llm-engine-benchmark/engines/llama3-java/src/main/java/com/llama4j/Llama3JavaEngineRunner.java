package com.llama4j;

import com.benchmark.core.EngineRunner;
import com.benchmark.core.EngineType;
import com.benchmark.core.ModelResolver;
import com.benchmark.core.ModelSpec;
import com.benchmark.core.RunResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Llama3JavaEngineRunner implements EngineRunner {

    private static final int BATCH_SIZE = Integer.getInteger("llama.BatchSize", 16);
    private static final String DEFAULT_SYSTEM_PROMPT = "Eres un asistente conciso.";
    private static final float DEFAULT_TOP_P = 0.95f;

    private Llama model;

    @Override
    public EngineType type() {
        return EngineType.LLAMA3_JAVA;
    }

    @Override
    public void ensureReady(ModelSpec spec) throws Exception {
        if (model != null) {
            return;
        }
        Path ggufPath = ModelResolver.resolve(spec.modelRef(), spec.workDir());
        model = ModelLoader.loadModel(ggufPath, spec.maxTokens(), true);
    }

    @Override
    public RunResult run(ModelSpec spec, String prompt) throws Exception {
        long loadStart = System.currentTimeMillis();
        ensureReady(spec);
        long loadTimeMs = System.currentTimeMillis() - loadStart;

        Llama.State state = model.createNewState(BATCH_SIZE);
        ChatFormat chatFormat = new ChatFormat(model.tokenizer());

        List<Integer> promptTokens = new ArrayList<>();
        promptTokens.add(chatFormat.beginOfText);
        String systemPrompt = spec.systemPrompt() != null ? spec.systemPrompt() : DEFAULT_SYSTEM_PROMPT;
        promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.SYSTEM, systemPrompt)));
        promptTokens.addAll(chatFormat.encodeMessage(new ChatFormat.Message(ChatFormat.Role.USER, prompt)));
        promptTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));

        Set<Integer> stopTokens = chatFormat.getStopTokens();
        Sampler sampler = Llama3.selectSampler(model.configuration().vocabularySize, spec.temperature(), DEFAULT_TOP_P, System.nanoTime());

        long generateStart = System.currentTimeMillis();
        List<Integer> responseTokens = Llama.generateTokens(model, state, 0, promptTokens, stopTokens, spec.maxTokens(), sampler, false, null);
        long generateTimeMs = System.currentTimeMillis() - generateStart;

        if (!responseTokens.isEmpty() && stopTokens.contains(responseTokens.get(responseTokens.size() - 1))) {
            responseTokens.remove(responseTokens.size() - 1);
        }
        String responseText = model.tokenizer().decode(responseTokens);
        return RunResult.of(type(), spec.modelRef(), prompt, responseText, loadTimeMs, generateTimeMs, responseTokens.size());
    }

    @Override
    public void close() {
        model = null;
    }
}
