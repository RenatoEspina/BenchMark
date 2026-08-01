package com.benchmark.core.rag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class RagCorpus {

    private RagCorpus() {
    }

    public static List<RagChunk> load(Path corpusDir, int chunkSize, int chunkOverlap) throws IOException {
        if (!Files.isDirectory(corpusDir)) {
            throw new IOException("El directorio de corpus RAG no existe: " + corpusDir);
        }
        List<RagChunk> chunks = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(corpusDir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt") || p.toString().endsWith(".md"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                String content = Files.readString(file);
                chunks.addAll(chunkText(file.getFileName().toString(), content, chunkSize, chunkOverlap));
            }
        }
        if (chunks.isEmpty()) {
            throw new IOException("No se encontraron documentos .txt/.md en " + corpusDir);
        }
        return chunks;
    }

    private static List<RagChunk> chunkText(String sourceFile, String text, int chunkSize, int chunkOverlap) {
        List<RagChunk> result = new ArrayList<>();
        int step = Math.max(1, chunkSize - chunkOverlap);
        int index = 0;
        int chunkNumber = 0;
        while (index < text.length()) {
            int end = Math.min(text.length(), index + chunkSize);
            String piece = text.substring(index, end).trim();
            if (!piece.isBlank()) {
                result.add(new RagChunk(sourceFile + "#" + chunkNumber, sourceFile, piece));
                chunkNumber++;
            }
            index += step;
        }
        return result;
    }
}