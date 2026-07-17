package com.benchmark.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public final class EngineRegistry {

    private static final Map<EngineType, Supplier<EngineRunner>> FACTORIES = new EnumMap<>(EngineType.class);

    private EngineRegistry() {
    }

    public static void register(EngineType type, Supplier<EngineRunner> factory) {
        FACTORIES.put(type, factory);
    }

    public static EngineRunner create(EngineType type) {
        Supplier<EngineRunner> factory = FACTORIES.get(type);
        if (factory == null) {
            throw new EngineNotAvailableException(type);
        }
        return factory.get();
    }

    public static boolean isAvailable(EngineType type) {
        return FACTORIES.containsKey(type);
    }
}
