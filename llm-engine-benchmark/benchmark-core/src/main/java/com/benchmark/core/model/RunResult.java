package com.benchmark.core;

public record RunResult(
        EngineType engineType,
        String modelRef,
        String promptText,
        String responseText,
        long preparationTimeMs,
        long requestTimeMs,
        int tokensGenerated,
        double tokensPerSecond,
        ResourceUsage resourceUsage,
        long retrievalTimeMs,
        int chunksRetrieved
) {

    /**
     * The preparation duration is the client-side time spent in ensureReady.
     * It is not the model's internal load duration.
     *
     * The request duration is measured with the same monotonic client clock
     * by every runner and covers request construction, HTTP round trip and
     * response parsing. It is intentionally broader than generation alone.
     * On a cold Ollama run, preparation can also include downloading a
     * missing model; warm runs are the comparable case for vLLM and Ollama.
     */
    public static RunResult of(
            EngineType engineType,
            String modelRef,
            String promptText,
            String responseText,
            long preparationTimeMs,
            long requestTimeMs,
            int tokensGenerated
    ) {
        double tokensPerSecond = requestTimeMs > 0
                ? (tokensGenerated * 1000.0) / requestTimeMs
                : 0.0;
        return new RunResult(engineType, modelRef, promptText, responseText, preparationTimeMs, requestTimeMs, tokensGenerated, tokensPerSecond, null, -1L, 0);
    }

    public RunResult withResourceUsage(ResourceUsage usage) {
        return new RunResult(engineType, modelRef, promptText, responseText, preparationTimeMs, requestTimeMs, tokensGenerated, tokensPerSecond, usage, retrievalTimeMs, chunksRetrieved);
    }

    public RunResult withRagInfo(long retrievalTimeMs, int chunksRetrieved) {
        return new RunResult(engineType, modelRef, promptText, responseText, preparationTimeMs, requestTimeMs, tokensGenerated, tokensPerSecond, resourceUsage, retrievalTimeMs, chunksRetrieved);
    }
}