package com.benchmark.core;

public class EngineNotAvailableException extends RuntimeException {

    public EngineNotAvailableException(EngineType type) {
        super("El engine " + type + " todavia no esta implementado en este benchmark");
    }
}
