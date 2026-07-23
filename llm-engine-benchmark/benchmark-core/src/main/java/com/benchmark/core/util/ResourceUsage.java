package com.benchmark.core;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record ResourceUsage(
        long heapUsedMb,
        long heapDeltaMb,
        double processCpuTimeMs,
        long gcCount,
        long gcTimeMs,
        int availableProcessors,
        long rssMb,
        long rssDeltaMb,
        long rssPeakMb
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

    public static final class Snapshot {
        private final long heapUsedBefore;
        private final long cpuTimeBefore;
        private final long gcCountBefore;
        private final long gcTimeBefore;
        private final long rssBeforeKb;

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
            long[] afterRssPeak = rssAndPeakKb();
            long rssAfterKb = afterRssPeak[0];
            long peakKb = afterRssPeak[1];
            long rssMb = rssAfterKb < 0 ? -1 : rssAfterKb / 1024;
            long rssDeltaMb = (rssBeforeKb < 0 || rssAfterKb < 0) ? -1 : (rssAfterKb - rssBeforeKb) / 1024;
            long rssPeakMb = peakKb < 0 ? -1 : peakKb / 1024;
            return new ResourceUsage(
                    heapUsedAfter / (1024 * 1024),
                    (heapUsedAfter - heapUsedBefore) / (1024 * 1024),
                    (cpuTimeAfter - cpuTimeBefore) / 1_000_000.0,
                    gcCountAfter - gcCountBefore,
                    gcTimeAfter - gcTimeBefore,
                    Runtime.getRuntime().availableProcessors(),
                    rssMb,
                    rssDeltaMb,
                    rssPeakMb
            );
        }
    }
}