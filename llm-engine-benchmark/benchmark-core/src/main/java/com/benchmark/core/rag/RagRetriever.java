package com.benchmark.core.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class RagRetriever {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9áéíóúñÁÉÍÓÚÑ]+");

    private final List<RagChunk> chunks;
    private final List<Map<String, Double>> chunkVectors;
    private final Map<String, Double> idf;

    public RagRetriever(List<RagChunk> chunks) {
        this.chunks = chunks;
        List<Map<String, Integer>> termFrequencies = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();

        for (RagChunk chunk : chunks) {
            Map<String, Integer> tf = countTerms(chunk.text());
            termFrequencies.add(tf);
            for (String term : tf.keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        this.idf = new HashMap<>();
        int totalDocs = chunks.size();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            idf.put(entry.getKey(), Math.log((double) totalDocs / entry.getValue()) + 1.0);
        }

        this.chunkVectors = new ArrayList<>();
        for (Map<String, Integer> tf : termFrequencies) {
            chunkVectors.add(toTfIdfVector(tf));
        }
    }

    private Map<String, Integer> countTerms(String text) {
        Map<String, Integer> counts = new HashMap<>();
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            counts.merge(matcher.group(), 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Double> toTfIdfVector(Map<String, Integer> tf) {
        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : tf.entrySet()) {
            vector.put(entry.getKey(), entry.getValue() * idf.getOrDefault(entry.getKey(), 0.0));
        }
        return vector;
    }

    public List<RetrievedChunk> retrieve(String query, int topK) {
        Map<String, Double> queryVector = toTfIdfVector(countTerms(query));
        List<RetrievedChunk> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            double score = cosineSimilarity(queryVector, chunkVectors.get(i));
            if (score > 0) {
                scored.add(new RetrievedChunk(chunks.get(i), score));
            }
        }
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return scored.subList(0, Math.min(topK, scored.size()));
    }

    private double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0.0;
        for (Map.Entry<String, Double> entry : a.entrySet()) {
            Double bValue = b.get(entry.getKey());
            if (bValue != null) {
                dot += entry.getValue() * bValue;
            }
        }
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        return (normA == 0.0 || normB == 0.0) ? 0.0 : dot / (normA * normB);
    }
}