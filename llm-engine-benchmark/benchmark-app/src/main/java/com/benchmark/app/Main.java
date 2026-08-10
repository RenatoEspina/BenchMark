package com.benchmark.app;

import com.benchmark.core.EngineRegistry;
import com.benchmark.core.EngineRunner;
import com.benchmark.core.EngineType;
import com.benchmark.core.ModelSpec;
import com.benchmark.core.RunResult;
import com.benchmark.core.ResourceUsage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public final class Main {

    private static final Path DEFAULT_WORK_DIR = Path.of("./models");
    
    private static final Set<String> KNOWN_OPTION_KEYS = Set.of(
        "engine", "model", "prompt", "workdir", "max-tokens", "temperature", "system-prompt",
        "rag", "rag-corpus", "rag-topk", "rag-chunk-size", "rag-chunk-overlap");

    private static void applyExtraSystemProperties(Map<String, String> options) {
        for (Map.Entry<String, String> entry : options.entrySet()) {
            if (!KNOWN_OPTION_KEYS.contains(entry.getKey())) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            runFromArgs(args);
            return;
        }
        runMenu();
    }

    private static void runMenu() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> startBenchmark(scanner);
                case "2" -> deleteModels(scanner);
                case "3" -> running = false;
                default -> System.out.println("Opcion invalida.");
            }
        }
        System.out.println("Hasta luego.");
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("=== LLM Engine Benchmark ===");
        System.out.println("1) Iniciar benchmark");
        System.out.println("2) Borrar modelos descargados");
        System.out.println("3) Salir");
        System.out.print("Elegi una opcion: ");
    }

    private static void startBenchmark(Scanner scanner) {
        List<EngineType> engines = EngineRegistry.availableEngines().stream()
                .sorted()
                .toList();
        if (engines.isEmpty()) {
            System.out.println("No hay engines disponibles en el classpath.");
            return;
        }

        System.out.println("Engines disponibles:");
        for (int i = 0; i < engines.size(); i++) {
            System.out.printf("  %d) %s%n", i + 1, engines.get(i));
        }
        System.out.print("Elegi un engine: ");
        int engineIndex = readInt(scanner.nextLine().trim(), -1) - 1;
        if (engineIndex < 0 || engineIndex >= engines.size()) {
            System.out.println("Opcion invalida.");
            return;
        }
        EngineType engineType = engines.get(engineIndex);

        System.out.print("Referencia del modelo (repo HF, ruta local o URL .gguf): ");
        String modelRef = scanner.nextLine().trim();
        if (modelRef.isBlank()) {
            System.out.println("El modelo es requerido.");
            return;
        }

        System.out.print("Prompt (enter para el de ejemplo \"Cual es la capital de Chile?\"): ");
        String prompt = scanner.nextLine().trim();
        if (prompt.isBlank()) {
            prompt = "Cual es la capital de Chile?";
        }

        System.out.print("Max tokens (enter para 256): ");
        int maxTokens = readInt(scanner.nextLine().trim(), 256);

        System.out.print("Temperature (enter para 0.0): ");
        float temperature = readFloat(scanner.nextLine().trim(), 0.0f);

        ModelSpec spec = new ModelSpec(engineType, modelRef, DEFAULT_WORK_DIR, null, maxTokens, temperature);
        try (EngineRunner runner = EngineRegistry.create(engineType)) {
            System.out.println("Preparando engine " + engineType + " con modelo " + modelRef);
            ResourceUsage.Snapshot snapshot = ResourceUsage.snapshot();
            ResourceUsage.CpuSampler sampler = ResourceUsage.CpuSampler.start(50);
            long overallStartNanos = System.nanoTime();
            RunResult result = runner.run(spec, prompt);
            long generationStartNanos = overallStartNanos + result.loadTimeMs() * 1_000_000L;
            ResourceUsage.GenerationCpuStats genStats = sampler.stopAndSummarize(generationStartNanos);
            printResult(result.withResourceUsage(snapshot.diff(genStats)), inProcess(engineType));  
        } catch (Exception e) {
            System.out.println("Error ejecutando el benchmark: " + e.getMessage());
        }
    }

    private static void deleteModels(Scanner scanner) {
        if (!Files.isDirectory(DEFAULT_WORK_DIR)) {
            System.out.println("No hay modelos descargados en " + DEFAULT_WORK_DIR);
            return;
        }
        List<Path> entries;
        try (var stream = Files.list(DEFAULT_WORK_DIR)) {
            entries = stream.sorted().toList();
        } catch (IOException e) {
            System.out.println("No se pudo leer " + DEFAULT_WORK_DIR + ": " + e.getMessage());
            return;
        }
        if (entries.isEmpty()) {
            System.out.println("No hay modelos descargados en " + DEFAULT_WORK_DIR);
            return;
        }

        System.out.println("Modelos descargados:");
        for (int i = 0; i < entries.size(); i++) {
            System.out.printf("  %d) %s%n", i + 1, entries.get(i).getFileName());
        }
        System.out.printf("  %d) Borrar todos%n", entries.size() + 1);
        System.out.print("Elegi que borrar (enter para cancelar): ");
        String choice = scanner.nextLine().trim();
        if (choice.isBlank()) {
            return;
        }
        int index = readInt(choice, -1) - 1;
        if (index == entries.size()) {
            entries.forEach(Main::deleteRecursively);
            System.out.println("Modelos borrados.");
            return;
        }
        if (index < 0 || index >= entries.size()) {
            System.out.println("Opcion invalida.");
            return;
        }
        deleteRecursively(entries.get(index));
        System.out.println("Borrado: " + entries.get(index).getFileName());
    }

    private static void deleteRecursively(Path path) {
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    System.out.println("No se pudo borrar " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.out.println("No se pudo borrar " + path + ": " + e.getMessage());
        }
    }

    private static void runFromArgs(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        applyExtraSystemProperties(options);

        EngineType engineType = EngineType.valueOf(options.getOrDefault("engine", "JLAMA").toUpperCase());
        String modelRef = options.getOrDefault("model", "tjake/Llama-3.2-1B-Instruct-JQ4");
        String prompt = options.getOrDefault("prompt", "Cual es la capital de Chile?");
        Path workDir = Path.of(options.getOrDefault("workdir", DEFAULT_WORK_DIR.toString()));
        int maxTokens = readInt(options.getOrDefault("max-tokens", ""), 256);
        float temperature = readFloat(options.getOrDefault("temperature", ""), 0.0f);

        boolean ragEnabled = Boolean.parseBoolean(options.getOrDefault("rag", "false"));
        int ragTopK = readInt(options.getOrDefault("rag-topk", ""), 3);
        int ragChunkSize = readInt(options.getOrDefault("rag-chunk-size", ""), 500);
        int ragChunkOverlap = readInt(options.getOrDefault("rag-chunk-overlap", ""), 50);

        ModelSpec spec = new ModelSpec(engineType, modelRef, workDir, options.get("system-prompt"), maxTokens, temperature);

        String effectivePrompt = prompt;
        long retrievalTimeMs = -1;
        int chunksRetrieved = 0;
        if (ragEnabled) {
            String ragCorpus = options.get("rag-corpus");
            if (ragCorpus == null || ragCorpus.isBlank()) {
                System.out.println("--rag=true requiere --rag-corpus=<directorio>");
                return;
            }
            long ragStart = System.currentTimeMillis();
            var corpusChunks = com.benchmark.core.rag.RagCorpus.load(Path.of(ragCorpus), ragChunkSize, ragChunkOverlap);
            var retriever = new com.benchmark.core.rag.RagRetriever(corpusChunks);
            var retrieved = retriever.retrieve(prompt, ragTopK);
            effectivePrompt = com.benchmark.core.rag.RagPromptBuilder.build(prompt, retrieved);
            retrievalTimeMs = System.currentTimeMillis() - ragStart;
            chunksRetrieved = retrieved.size();
        }

        try (EngineRunner runner = EngineRegistry.create(engineType)) {
            System.out.println("Preparando engine " + engineType + " con modelo " + modelRef);
            ResourceUsage.Snapshot snapshot = ResourceUsage.snapshot();
            ResourceUsage.CpuSampler sampler = ResourceUsage.CpuSampler.start(50);
            long overallStartNanos = System.nanoTime();
            RunResult result = runner.run(spec, effectivePrompt);
            long generationStartNanos = overallStartNanos + result.loadTimeMs() * 1_000_000L;
            ResourceUsage.GenerationCpuStats genStats = sampler.stopAndSummarize(generationStartNanos);
            RunResult withRag = result.withRagInfo(retrievalTimeMs, chunksRetrieved);
            printResult(withRag.withResourceUsage(snapshot.diff(genStats)), inProcess(engineType));
        }
    }

    private static boolean inProcess(EngineType type) {
        return switch (type) {
            return type ==EngineType.JLAMA;
        };
    }

    private static void printResult(RunResult result, boolean inProcess) {
        System.out.println();
        System.out.println("Engine: " + result.engineType());
        System.out.println("Modelo: " + result.modelRef());
        System.out.println("Prompt: " + result.promptText());
        System.out.println("Respuesta: " + result.responseText());
        System.out.println("Carga: " + result.loadTimeMs() + " ms");
        System.out.println("Generacion: " + result.generateTimeMs() + " ms");
        System.out.println("Tokens (aprox): " + result.tokensGenerated());
        System.out.println("Tokens/seg (aprox): " + String.format("%.2f", result.tokensPerSecond()));
        ResourceUsage usage = result.resourceUsage();
        if (usage != null) {
            String scope = inProcess ? "proceso del engine" : "solo overhead de benchmark-app (engine externo)";
            System.out.println("Recursos (" + scope + "):");
            System.out.println("  Heap usado: " + usage.heapUsedMb() + " MB (delta " + usage.heapDeltaMb() + " MB)");
            if (usage.rssMb() >= 0) {
                System.out.println("  RAM real (RSS): " + usage.rssMb() + " MB (delta " + usage.rssDeltaMb() + " MB, pico " + usage.rssPeakMb() + " MB)");
            } else {
                System.out.println("  RAM real (RSS): no disponible en este sistema operativo");
            }
            System.out.println("  CPU proceso (carga+generacion): " + String.format("%.1f", usage.processCpuTimeMs()) + " ms (" + String.format("%.1f", usage.cpuPercent()) + "% CPU)");
            if (inProcess) {
                if (usage.generationSampleCount() > 0) {
                    System.out.println("  CPU% promedio (generacion): " + String.format("%.1f", usage.cpuPercentAvgGeneration()) + " %");
                    System.out.println("  CPU% pico (generacion): " + String.format("%.1f", usage.cpuPercentPeakGeneration()) + " %");
                } else {
                System.out.println("  CPU% generacion: sin muestras (generacion demasiado corta)");
                }
            }   
            System.out.println("  GC: " + usage.gcCount() + " colecciones, " + usage.gcTimeMs() + " ms");
            System.out.println("  CPUs disponibles: " + usage.availableProcessors());
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new java.util.HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                String[] parts = arg.substring(2).split("=", 2);
                map.put(parts[0], parts.length > 1 ? parts[1] : "true");
            }
        }
        return map;
    }

    private static int readInt(String text, int fallback) {
        try {
            return text.isBlank() ? fallback : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float readFloat(String text, float fallback) {
        try {
            return text.isBlank() ? fallback : Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}