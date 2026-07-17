package com.benchmark.app;

import com.benchmark.core.EngineRegistry;
import com.benchmark.core.EngineRunner;
import com.benchmark.core.EngineType;
import com.benchmark.core.ModelSpec;
import com.benchmark.core.RunResult;
import com.benchmark.engine.jlama.JlamaEngineRunner;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Main {

    static {
        EngineRegistry.register(EngineType.JLAMA, JlamaEngineRunner::new);
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);

        EngineType engineType = EngineType.valueOf(options.getOrDefault("engine", "JLAMA").toUpperCase());
        String modelRef = options.getOrDefault("model", "tjake/Llama-3.2-1B-Instruct-JQ4");
        String prompt = options.getOrDefault("prompt", "Cual es la capital de Chile?");
        Path workDir = Path.of(options.getOrDefault("workdir", "./models"));
        int maxTokens = Integer.parseInt(options.getOrDefault("max-tokens", "256"));
        float temperature = Float.parseFloat(options.getOrDefault("temperature", "0.0"));

        ModelSpec spec = new ModelSpec(engineType, modelRef, workDir, options.get("system-prompt"), maxTokens, temperature);

        try (EngineRunner runner = EngineRegistry.create(engineType)) {
            System.out.println("Preparando engine " + engineType + " con modelo " + modelRef);
            runner.ensureReady(spec);
            RunResult result = runner.run(spec, prompt);
            printResult(result);
        }
    }

    private static void printResult(RunResult result) {
        System.out.println();
        System.out.println("Engine: " + result.engineType());
        System.out.println("Modelo: " + result.modelRef());
        System.out.println("Prompt: " + result.promptText());
        System.out.println("Respuesta: " + result.responseText());
        System.out.println("Carga: " + result.loadTimeMs() + " ms");
        System.out.println("Generacion: " + result.generateTimeMs() + " ms");
        System.out.println("Tokens (aprox): " + result.tokensGenerated());
        System.out.println("Tokens/seg (aprox): " + String.format("%.2f", result.tokensPerSecond()));
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split("=", 2);
                map.put(parts[0], parts.length > 1 ? parts[1] : "true");
            }
        }
        return map;
    }
}
