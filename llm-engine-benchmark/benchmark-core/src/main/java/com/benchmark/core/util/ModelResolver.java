package com.benchmark.core.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ModelResolver {

    private ModelResolver() {
    }

    public static Path resolve(String modelRef, Path workDir) throws IOException, InterruptedException {
        if (modelRef.startsWith("http://") || modelRef.startsWith("https://")) {
            return downloadIfMissing(modelRef, workDir);
        }
        Path localPath = Path.of(modelRef);
        if (Files.exists(localPath)) {
            return localPath;
        }
        Path inWorkDir = workDir.resolve(modelRef);
        if (Files.exists(inWorkDir)) {
            return inWorkDir;
        }
        throw new IOException("No se encontro el modelo: " + modelRef);
    }

    private static Path downloadIfMissing(String url, Path workDir) throws IOException, InterruptedException {
        Files.createDirectories(workDir);
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        Path target = workDir.resolve(fileName);
        if (Files.exists(target) && Files.size(target) > 0) {
            return target;
        }
        Path partial = workDir.resolve(fileName + ".part");
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(partial));
        if (response.statusCode() / 100 != 2) {
            Files.deleteIfExists(partial);
            throw new IOException("Descarga fallida (" + response.statusCode() + "): " + url);
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }
}
