package com.benchmark.core;

public interface EngineRunner extends AutoCloseable {

    EngineType type();

    void ensureReady(ModelSpec spec) throws Exception;

    RunResult run(ModelSpec spec, String prompt) throws Exception;

    @Override
    default void close() throws Exception {
    }
}
