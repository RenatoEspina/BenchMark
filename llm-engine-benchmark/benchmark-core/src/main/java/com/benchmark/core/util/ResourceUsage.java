package com.benchmark.core;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ResourceUsage(
        long heapUsedMb,
        long heapDeltaMb,
        double processCpuTimeMs,
        double cpuPercent,
        long gcCount,
        long gcTimeMs,
        int availableProcessors,
        long rssMb,
        long rssDeltaMb,
        long rssPeakMb,
        double cpuPercentAvgGeneration,
        double cpuPercentPeakGeneration,
        int generationSampleCount
) {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    public static Snapshot snapshot() {
        return new Snapshot();
    }

    private static long readProcSelfStatusKb(String key) {
        Path statusPath = Path.of("/proc/self/status");
        if (!Files.isReadable(statusPath)) {
            return -1;
        }
        try {
            for (String line : Files.readAllLines(statusPath)) {
                if (line.startsWith(key)) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        return Long.parseLong(parts[1]);
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
        }
        return -1;
    }

    private static long[] linuxRssPeakKb() {
        return new long[] { readProcSelfStatusKb("VmRSS:"), readProcSelfStatusKb("VmHWM:") };
    }

    private static long[] unixPsRssPeakKb() {
        try {
            long pid = ProcessHandle.current().pid();
            Process proc = new ProcessBuilder("ps", "-o", "rss=", "-p", String.valueOf(pid)).start();
            String output;
            try (var in = proc.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            proc.waitFor();
            return new long[] { Long.parseLong(output), -1 };
        } catch (Exception e) {
            return new long[] { -1, -1 };
        }
    }

    private static long[] windowsRssPeakKb() {
        try {
            long pid = ProcessHandle.current().pid();
            Process proc = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "$p = Get-Process -Id " + pid + "; Write-Output ($p.WorkingSet64.ToString() + ',' + $p.PeakWorkingSet64.ToString())"
            ).start();
            String output;
            try (var in = proc.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            proc.waitFor();
            String[] parts = output.split(",");
            long ws = Long.parseLong(parts[0].trim());
            long peak = Long.parseLong(parts[1].trim());
            return new long[] { ws / 1024, peak / 1024 };
        } catch (Exception e) {
            return new long[] { -1, -1 };
        }
    }

    private static long[] rssAndPeakKb() {
        if (IS_WINDOWS) {
            return windowsRssPeakKb();
        }
        long[] linux = linuxRssPeakKb();
        if (linux[0] >= 0) {
            return linux;
        }
        return unixPsRssPeakKb();
    }

    public record GenerationCpuStats(double cpuPercentAvg, double cpuPercentPeak, int sampleCount) {
        public static final GenerationCpuStats EMPTY = new GenerationCpuStats(0.0, 0.0, 0);
    }

    public static final class CpuSampler {
        private final Thread thread;
        private final List<double[]> samples = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean running = true;

        private CpuSampler(long intervalMs) {
            this.thread = new Thread(() -> sampleLoop(intervalMs), "resource-usage-cpu-sampler");
            this.thread.setDaemon(true);
        }

        public static CpuSampler start(long intervalMs) {
            CpuSampler sampler = new CpuSampler(intervalMs);
            sampler.thread.start();
            return sampler;
        }

        private void sampleLoop(long intervalMs) {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            long prevCpu = osBean.getProcessCpuTime();
            long prevWallNanos = System.nanoTime();
            while (running) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                long nowCpu = osBean.getProcessCpuTime();
                long nowWallNanos = System.nanoTime();
                long deltaCpuNanos = nowCpu - prevCpu;
                long deltaWallNanos = nowWallNanos - prevWallNanos;
                double cpuPercent = deltaWallNanos > 0 ? (deltaCpuNanos * 100.0) / deltaWallNanos : 0.0;
                samples.add(new double[] { nowWallNanos, cpuPercent });
                prevCpu = nowCpu;
                prevWallNanos = nowWallNanos;
            }
        }

        public GenerationCpuStats stopAndSummarize(long generationStartNanos) {
            running = false;
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<Double> genSamples = new ArrayList<>();
            synchronized (samples) {
                for (double[] s : samples) {
                    if (s[0] >= generationStartNanos) {
                        genSamples.add(s[1]);
                    }
                }
            }
            if (genSamples.isEmpty()) {
                return GenerationCpuStats.EMPTY;
            }
            double avg = genSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double peak = genSamples.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            return new GenerationCpuStats(avg, peak, genSamples.size());
        }
    }

    public static final class Snapshot {
        private final long heapUsedBefore;
        private final long cpuTimeBefore;
        private final long gcCountBefore;
        private final long gcTimeBefore;
        private final long rssBeforeKb;
        private final long wallStartNanos;

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
            this.rssBeforeKb = rssAndPeakKb()[0];
            this.wallStartNanos = System.nanoTime();
        }

        public ResourceUsage diff(GenerationCpuStats generationCpuStats) {
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            long heapUsedAfter = memoryMXBean.getHeapMemoryUsage().getUsed();
            long cpuTimeAfter = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
            long gcCountAfter = 0;
            long gcTimeAfter = 0;
            for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCountAfter += gcBean.getCollectionCount();
                gcTimeAfter += gcBean.getCollectionTime();
            }
            long[] afterRssPeak = rssAndPeakKb();
            long rssAfterKb = afterRssPeak[0];
            long peakKb = afterRssPeak[1];
            long rssMb = rssAfterKb < 0 ? -1 : rssAfterKb / 1024;
            long rssDeltaMb = (rssBeforeKb < 0 || rssAfterKb < 0) ? -1 : (rssAfterKb - rssBeforeKb) / 1024;
            long rssPeakMb = peakKb < 0 ? -1 : peakKb / 1024;

            double cpuTimeMs = (cpuTimeAfter - cpuTimeBefore) / 1_000_000.0;
            double wallTimeMs = (System.nanoTime() - wallStartNanos) / 1_000_000.0;
            double cpuPercent = wallTimeMs > 0 ? (cpuTimeMs / wallTimeMs) * 100.0 : 0.0;

            return new ResourceUsage(
                    heapUsedAfter / (1024 * 1024),
                    (heapUsedAfter - heapUsedBefore) / (1024 * 1024),
                    cpuTimeMs,
                    cpuPercent,
                    gcCountAfter - gcCountBefore,
                    gcTimeAfter - gcTimeBefore,
                    Runtime.getRuntime().availableProcessors(),
                    rssMb,
                    rssDeltaMb,
                    rssPeakMb,
                    generationCpuStats.cpuPercentAvg(),
                    generationCpuStats.cpuPercentPeak(),
                    generationCpuStats.sampleCount()
            );
        }

        public long wallStartNanos() {
            return wallStartNanos;
        }
    }
}