package com.benchmark.engine.deliverance;

import com.fasterxml.jackson.datatype.guava.GuavaModule;

import com.benchmark.core.EngineRunner;
import com.benchmark.core.EngineType;
import com.benchmark.core.ModelSpec;
import com.benchmark.core.RunResult;

import io.teknek.deliverance.generator.GeneratorParameters;
import io.teknek.deliverance.generator.Response;
import io.teknek.deliverance.model.AutoModelForCausaLm;
import io.teknek.deliverance.model.CausalLanguageModel;
import io.teknek.deliverance.model.DoNothingGenerateEvent;
import io.teknek.deliverance.safetensors.fetch.ModelFetcher;
import io.teknek.deliverance.safetensors.prompt.PromptContext;

import java.util.UUID;

public final class DeliveranceEngineRunner implements EngineRunner {

    private static final String DEFAULT_SYSTEM_PROMPT = "Eres un asistente conciso.";

    // Registramos Guava explicitly e imprimimos cualquier error para no tener fallas silenciosas.
    static {
        System.out.println("[DELIVERANCE-INIT] Iniciando inyección de GuavaModule...");
        try {
            io.teknek.deliverance.JsonUtils.om.registerModule(new GuavaModule());
            System.out.println("[DELIVERANCE-INIT] Módulo registrado en Deliverance core.");
        } catch (Throwable t) {
            System.err.println("[DELIVERANCE-INIT] Error en core: " + t.getMessage());
        }

        try {
            io.teknek.deliverance.safetensors.JsonUtils.om.registerModule(new GuavaModule());
            System.out.println("[DELIVERANCE-INIT] Módulo registrado en Safetensors.");
        } catch (Throwable t) {
            System.err.println("[DELIVERANCE-INIT] Error en Safetensors: " + t.getMessage());
        }
    }

    private CausalLanguageModel model;

    @Override
    public EngineType type() {
        return EngineType.DELIVERANCE;
    }

    @Override
    public void ensureReady(ModelSpec spec) throws Exception {
        if (model != null) {
            return;
        }
        ModelFetcher fetcher = resolveFetcher(spec);
        model = AutoModelForCausaLm.newBuilder(fetcher).build();
    }

    @Override
    public RunResult run(ModelSpec spec, String prompt) throws Exception {
        long loadStart = System.currentTimeMillis();
        ensureReady(spec);
        long loadTimeMs = System.currentTimeMillis() - loadStart;

        PromptContext ctx = buildPromptContext(spec, prompt);
        GeneratorParameters params = new GeneratorParameters()
                .withTemperature(spec.temperature())
                .withMaxTokens(spec.maxTokens());

        long generateStart = System.currentTimeMillis();
        Response response = model.generate(UUID.randomUUID(), ctx, params, new DoNothingGenerateEvent());
        long generateTimeMs = System.currentTimeMillis() - generateStart;

        int tokensGenerated = response.generatedTokens.size();
        return RunResult.of(type(), spec.modelRef(), ctx.getPrompt(), response.responseText, loadTimeMs, generateTimeMs, tokensGenerated);
    }

    private ModelFetcher resolveFetcher(ModelSpec spec) {
        String modelRef = spec.modelRef();
        int slash = modelRef.indexOf('/');
        if (slash <= 0 || slash == modelRef.length() - 1) {
            throw new IllegalArgumentException("modelRef de Deliverance debe tener el formato owner/name: " + modelRef);
        }
        String owner = modelRef.substring(0, slash);
        String name = modelRef.substring(slash + 1);
        ModelFetcher fetcher = new ModelFetcher(owner, name);
        fetcher.setBaseDir(spec.workDir());
        return fetcher;
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

    @Override
    public void close() throws Exception {
        if (model != null) {
            model.close();
            model = null;
        }
    }
}