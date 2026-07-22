package com.benchmark.core;

public record RunResult(
        EngineType engineType,
        String modelRef,
        String promptText,
        String responseText,
        long loadTimeMs,
        long generateTimeMs,
        int tokensGenerated,
        double tokensPerSecond,
        ResourceUsage resourceUsage
) {
    public static RunResult of(
            EngineType engineType,
            String modelRef,
            String promptText,
            String responseText,
            long loadTimeMs,
            long generateTimeMs,
            int tokensGenerated
    ) {
        double tokensPerSecond = generateTimeMs > 0
                ? (tokensGenerated * 1000.0) / generateTimeMs
                : 0.0;
        return new RunResult(engineType, modelRef, promptText, responseText, loadTimeMs, generateTimeMs, tokensGenerated, tokensPerSecond, null);
    }

    public RunResult withResourceUsage(ResourceUsage usage) {
        return new RunResult(engineType, modelRef, promptText, responseText, loadTimeMs, generateTimeMs, tokensGenerated, tokensPerSecond, usage);
    }
}