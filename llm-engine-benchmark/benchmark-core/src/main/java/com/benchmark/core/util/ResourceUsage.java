package com.benchmark.core;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import com.sun.management.OperatingSystemMXBean;

public record ResourceUsage(
        long heapUsedMb,
        long heapDeltaMb,
        double processCpuTimeMs,
        long gcCount,
        long gcTimeMs,
        int availableProcessors
) {
    public static Snapshot snapshot() {
        return new Snapshot();
    }

    public static final class Snapshot {
        private final long heapUsedBefore;
        private final long cpuTimeBefore;
        private final long gcCountBefore;
        private final long gcTimeBefore;

        private Snapshot() {
            this.heapUsedBefore = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            this.cpuTimeBefore = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
            long count = 0;
            long time = 0;
            for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
                count += gcBean.getCollectionCount();
                time += gcBean.getCollectionTime();
            }
            this.gcCountBefore = count;
            this.gcTimeBefore = time;
        }

        public ResourceUsage diff() {
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            long heapUsedAfter = memoryMXBean.getHeapMemoryUsage().getUsed();
            long cpuTimeAfter = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
            long gcCountAfter = 0;
            long gcTimeAfter = 0;
            for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCountAfter += gcBean.getCollectionCount();
                gcTimeAfter += gcBean.getCollectionTime();
            }
            return new ResourceUsage(
                    heapUsedAfter / (1024 * 1024),
                    (heapUsedAfter - heapUsedBefore) / (1024 * 1024),
                    (cpuTimeAfter - cpuTimeBefore) / 1_000_000.0,
                    gcCountAfter - gcCountBefore,
                    gcTimeAfter - gcTimeBefore,
                    Runtime.getRuntime().availableProcessors()
            );
        }
    }
}