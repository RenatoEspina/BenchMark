package com.benchmark.core;

/**
 * Common monotonic-clock conversions used by every engine runner.
 */
public final class BenchmarkTiming {

    private BenchmarkTiming() {
    }

    public static long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}