package com.benchmark.engine.gpullama3;

import com.benchmark.core.EngineRunner;
import com.benchmark.core.EngineType;
import com.benchmark.core.ModelResolver;
import com.benchmark.core.ModelSpec;
import com.benchmark.core.RunResult;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.gpullama3.GPULlama3ChatModel;
import dev.langchain4j.model.output.TokenUsage;

import java.nio.file.Path;

public final class GPULlama3JavaEngineRunner implements EngineRunner {

    private static final String DEFAULT_SYSTEM_PROMPT = "Eres un asistente conciso.";
    private static final boolean DEFAULT_ON_GPU = Boolean.parseBoolean(System.getProperty("gpullama3.onGPU", "true"));
    
    private static final int HARD_CONTEXT_LIMIT = 8192;
    
    private GPULlama3ChatModel model;
    private int requestedMaxTokens; 
    @Override
    public EngineType type() {
        return EngineType.GPULLAMA3_JAVA;
    }

    @Override
    public void ensureReady(ModelSpec spec) throws Exception {

        Path ggufPath = ModelResolver.resolve(spec.modelRef(), spec.workDir());

        model = GPULlama3ChatModel.builder()
                .modelPath(ggufPath)
                .temperature((double) spec.temperature())
                .maxTokens(spec.maxTokens())
                .onGPU(DEFAULT_ON_GPU)
                .build();

    }

    @Override
    public RunResult run(ModelSpec spec, String prompt) throws Exception {

        long loadStart = System.currentTimeMillis();
        ensureReady(spec);
        long loadTimeMs = System.currentTimeMillis() - loadStart;

        String systemPrompt = spec.systemPrompt() != null
                            ? spec.systemPrompt()
                            : DEFAULT_SYSTEM_PROMPT;

        ChatRequest request = ChatRequest.builder()
                            .messages(
                            SystemMessage.from(systemPrompt),
                            UserMessage.from(prompt)
                            ).build();

        long generateStart = System.currentTimeMillis();

        ChatResponse response = model.chat(request);

        long generateTimeMs = System.currentTimeMillis() - generateStart;

        String responseText = response.aiMessage().text();

        TokenUsage tokenUsage = null;

        int tokensGenerated = extractTokenCount(response, responseText);

        return RunResult.of(
                type(),
                spec.modelRef(),
                prompt,
                responseText,
                loadTimeMs,
                generateTimeMs,
                tokensGenerated
        );
    }
    
    private String truncateResponseToMaxTokens(String responseText, int maxTokens) {
        String[] words = responseText.split("\\s+");
        int estimatedTokens = (int) Math.ceil(words.length * 1.5);
        
        if (estimatedTokens <= maxTokens) {
            return responseText;
        }
        
        int maxWords = (int) Math.floor(maxTokens / 1.5);
        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < Math.min(maxWords, words.length); i++) {
            truncated.append(words[i]).append(" ");
        }
        
        System.err.println("[GPULlama3 Runner INFO] Respuesta truncada de " + estimatedTokens + 
                          " a ~" + maxTokens + " tokens solicitados");
        return truncated.toString().trim();
    }
    
    private int estimatePromptTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] words = text.split("\\s+");
        return (int) Math.ceil(words.length * 1.5);
    }

    private int extractTokenCount(ChatResponse response, String responseText) {
        if (response.metadata() != null) {
            TokenUsage tokenUsage = response.metadata().tokenUsage();
            if (tokenUsage != null && tokenUsage.outputTokenCount() != null) {
                return tokenUsage.outputTokenCount();
            }
        }
        return estimateTokens(responseText);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.trim().split("\\s+").length * 1.5);
    }

    @Override
    public void close() {
        model = null;
    }
}