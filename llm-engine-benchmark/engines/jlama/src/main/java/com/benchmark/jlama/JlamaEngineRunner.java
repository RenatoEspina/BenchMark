package com.benchmark.engine.jlama;

import com.benchmark.core.EngineRunner;
import com.benchmark.core.EngineType;
import com.benchmark.core.ModelSpec;
import com.benchmark.core.RunResult;
import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.model.functions.Generator;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.prompt.PromptContext;
import com.github.tjake.jlama.util.Downloader;

import java.io.File;
import java.util.UUID;

public final class JlamaEngineRunner implements EngineRunner {

    private static final String DEFAULT_SYSTEM_PROMPT = "Eres un asistente conciso.";

    private AbstractModel model;

    @Override
    public EngineType type() {
        return EngineType.JLAMA;
    }

    @Override
    public void ensureReady(ModelSpec spec) throws Exception {
        if (model != null) {
            return;
        }
        File localModelPath = new Downloader(spec.workDir().toString(), spec.modelRef()).huggingFaceModel();
        model = ModelSupport.loadModel(localModelPath, DType.F32, DType.I8);
    }

    @Override
    public RunResult run(ModelSpec spec, String prompt) throws Exception {
        long loadStart = System.currentTimeMillis();
        ensureReady(spec);
        long loadTimeMs = System.currentTimeMillis() - loadStart;

        PromptContext ctx = buildPromptContext(spec, prompt);

        long generateStart = System.currentTimeMillis();
        Generator.Response response = model.generateBuilder()
                .session(UUID.randomUUID())
                .promptContext(ctx)
                .ntokens(spec.maxTokens())
                .temperature(spec.temperature())
                .generate();
        long generateTimeMs = System.currentTimeMillis() - generateStart;

        int tokensGenerated = estimateTokens(response.responseText);
        return RunResult.of(type(), spec.modelRef(), ctx.getPrompt(), response.responseText, loadTimeMs, generateTimeMs, tokensGenerated);
    }

    private PromptContext buildPromptContext(ModelSpec spec, String prompt) {
        if (model.promptSupport().isEmpty()) {
            return PromptContext.of(prompt);
        }
        String systemPrompt = spec.systemPrompt() != null ? spec.systemPrompt() : DEFAULT_SYSTEM_PROMPT;
        return model.promptSupport()
                .get()
                .builder()
                .addSystemMessage(systemPrompt)
                .addUserMessage(prompt)
                .build();
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    @Override
    public void close() {
        model = null;
    }
}
