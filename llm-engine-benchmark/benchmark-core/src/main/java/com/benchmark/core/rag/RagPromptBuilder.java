package com.benchmark.core.rag;

import java.util.List;
import java.util.stream.Collectors;

public final class RagPromptBuilder {

    private RagPromptBuilder() {
    }

    public static String build(String originalPrompt, List<RetrievedChunk> retrievedChunks) {
        if (retrievedChunks.isEmpty()) {
            return originalPrompt;
        }
        String context = retrievedChunks.stream()
                .map(rc -> "[" + rc.chunk().id() + "] " + rc.chunk().text())
                .collect(Collectors.joining("\n\n"));
        return "Contexto:\n" + context + "\n\nPregunta: " + originalPrompt;
    }
}