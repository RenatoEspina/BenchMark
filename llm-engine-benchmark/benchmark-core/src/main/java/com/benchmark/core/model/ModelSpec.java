package com.benchmark.core.model;

import java.nio.file.Path;

public record ModelSpec(
        EngineType engineType,
        String modelRef,
        Path workDir,
        String systemPrompt,
        int maxTokens,
        float temperature
) {
    public ModelSpec {
        if (engineType == null) {
            throw new IllegalArgumentException("engineType requerido");
        }
        if (modelRef == null || modelRef.isBlank()) {
            throw new IllegalArgumentException("modelRef requerido");
        }
        if (workDir == null) {
            throw new IllegalArgumentException("workDir requerido");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens debe ser mayor a 0");
        }
    }

    public static ModelSpec of(EngineType engineType, String modelRef, Path workDir) {
        return new ModelSpec(engineType, modelRef, workDir, null, 256, 0.0f);
    }
}
