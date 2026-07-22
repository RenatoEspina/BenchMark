package com.benchmark.app;

import com.benchmark.core.EngineRegistry;
import com.benchmark.core.EngineRunner;
import com.benchmark.core.EngineType;
import com.benchmark.core.ModelSpec;
import com.benchmark.core.RunResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public final class Main {

    private static final Path DEFAULT_WORK_DIR = Path.of("./models");

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
            runner.ensureReady(spec);
            ResourceUsage.Snapshot snapshot = ResourceUsage.snapshot();
            RunResult result = runner.run(spec, prompt);
            printResult(result.withResourceUsage(snapshot.diff()), inProcess(engineType));
        } catch (Exception e) {
            System.out.println("Error ejecutando el benchmark: " + e.getMessage());
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

        EngineType engineType = EngineType.valueOf(options.getOrDefault("engine", "JLAMA").toUpperCase());
        String modelRef = options.getOrDefault("model", "tjake/Llama-3.2-1B-Instruct-JQ4");
        String prompt = options.getOrDefault("prompt", "Cual es la capital de Chile?");
        Path workDir = Path.of(options.getOrDefault("workdir", DEFAULT_WORK_DIR.toString()));
        int maxTokens = Integer.parseInt(options.getOrDefault("max-tokens", "256"));
        float temperature = Float.parseFloat(options.getOrDefault("temperature", "0.0"));

        ModelSpec spec = new ModelSpec(engineType, modelRef, workDir, options.get("system-prompt"), maxTokens, temperature);

        try (EngineRunner runner = EngineRegistry.create(engineType)) {
            System.out.println("Preparando engine " + engineType + " con modelo " + modelRef);
            runner.ensureReady(spec);
            ResourceUsage.Snapshot snapshot = ResourceUsage.snapshot();
            RunResult result = runner.run(spec, prompt);
            printResult(result.withResourceUsage(snapshot.diff()), inProcess(engineType));
        }
    }

    private static boolean inProcess(EngineType type) {
        return switch (type) {
            case JLAMA, LLAMA3_JAVA, GPULLAMA3_JAVA, DELIVERANCE -> true;
            default -> false;
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
            System.out.println("  CPU proceso: " + String.format("%.1f", usage.processCpuTimeMs()) + " ms");
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
