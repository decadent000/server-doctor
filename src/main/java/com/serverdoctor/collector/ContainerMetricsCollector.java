package com.serverdoctor.collector;

import com.serverdoctor.model.ContainerMetrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class ContainerMetricsCollector {

    private static final File CGROUP_ROOT = new File("/sys/fs/cgroup");

    public ContainerMetrics collect() {
        if (!isLinux()) {
            return ContainerMetrics.unavailable("cgroup指标仅支持Linux");
        }

        try {
            if (new File(CGROUP_ROOT, "cgroup.controllers").isFile()) {
                return collectV2();
            }

            if (CGROUP_ROOT.isDirectory()) {
                return collectV1();
            }

            return ContainerMetrics.unavailable("/sys/fs/cgroup不存在");

        } catch (Exception e) {
            return ContainerMetrics.unavailable("cgroup采集失败：" + e.getMessage());
        }
    }

    private ContainerMetrics collectV2() throws Exception {
        File base = resolveV2Path();

        long memoryUsage = readLong(new File(base, "memory.current"), -1L);
        long memoryLimit = readLimit(new File(base, "memory.max"));
        String cpuMax = readFirstLine(new File(base, "cpu.max"));
        double quotaCores = parseV2CpuQuota(cpuMax);

        String cpuset = readFirstNonBlank(
                new File(base, "cpuset.cpus.effective"),
                new File(base, "cpuset.cpus")
        );

        Map<String, Long> cpuStat = readKeyLongs(new File(base, "cpu.stat"));
        long periods = value(cpuStat, "nr_periods");
        long throttled = value(cpuStat, "nr_throttled");
        long throttledUsec = value(cpuStat, "throttled_usec");
        double throttledMillis = throttledUsec < 0 ? -1D : throttledUsec / 1000D;

        long pidsCurrent = readLong(new File(base, "pids.current"), -1L);
        long pidsMax = readLimit(new File(base, "pids.max"));

        Map<String, Long> memoryEvents = readKeyLongs(new File(base, "memory.events"));
        long oom = value(memoryEvents, "oom");
        long oomKill = value(memoryEvents, "oom_kill");

        return new ContainerMetrics(
                true,
                base.getAbsolutePath(),
                "v2",
                memoryUsage,
                memoryLimit,
                quotaCores,
                cpuset,
                periods,
                throttled,
                throttledMillis,
                pidsCurrent,
                pidsMax,
                oom,
                oomKill,
                -1L
        );
    }

    private ContainerMetrics collectV1() throws Exception {
        Map<String, String> paths = readV1ControllerPaths();

        File memoryBase = resolveV1Controller(
                new String[]{"/sys/fs/cgroup/memory"},
                paths.get("memory")
        );

        File cpuBase = resolveV1Controller(
                new String[]{"/sys/fs/cgroup/cpu,cpuacct", "/sys/fs/cgroup/cpu"},
                firstNonBlank(paths.get("cpu"), paths.get("cpuacct"))
        );

        File cpusetBase = resolveV1Controller(
                new String[]{"/sys/fs/cgroup/cpuset"},
                paths.get("cpuset")
        );

        File pidsBase = resolveV1Controller(
                new String[]{"/sys/fs/cgroup/pids"},
                paths.get("pids")
        );

        long memoryUsage = readLong(file(memoryBase, "memory.usage_in_bytes"), -1L);
        long memoryLimit = normalizeV1MemoryLimit(
                readLong(file(memoryBase, "memory.limit_in_bytes"), -1L)
        );

        long quota = readLong(file(cpuBase, "cpu.cfs_quota_us"), -1L);
        long period = readLong(file(cpuBase, "cpu.cfs_period_us"), -1L);
        double quotaCores = quota > 0 && period > 0 ? quota / (double) period : -1D;

        String cpuset = readFirstLine(file(cpusetBase, "cpuset.cpus"));

        Map<String, Long> cpuStat = readKeyLongs(file(cpuBase, "cpu.stat"));
        long periods = value(cpuStat, "nr_periods");
        long throttled = value(cpuStat, "nr_throttled");
        long throttledNanos = value(cpuStat, "throttled_time");
        double throttledMillis = throttledNanos < 0 ? -1D : throttledNanos / 1000000D;

        long pidsCurrent = readLong(file(pidsBase, "pids.current"), -1L);
        long pidsMax = readLimit(file(pidsBase, "pids.max"));

        long failCount = readLong(file(memoryBase, "memory.failcnt"), -1L);

        return new ContainerMetrics(
                true,
                "cgroup v1",
                "v1",
                memoryUsage,
                memoryLimit,
                quotaCores,
                cpuset,
                periods,
                throttled,
                throttledMillis,
                pidsCurrent,
                pidsMax,
                -1L,
                -1L,
                failCount
        );
    }

    private File resolveV2Path() throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader("/proc/self/cgroup"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("0::")) {
                    String relative = line.substring(3).trim();
                    File resolved = appendRelative(CGROUP_ROOT, relative);
                    if (resolved.isDirectory()) {
                        return resolved;
                    }
                }
            }
        } finally {
            reader.close();
        }
        return CGROUP_ROOT;
    }

    private Map<String, String> readV1ControllerPaths() throws Exception {
        Map<String, String> result = new HashMap<String, String>();
        BufferedReader reader = new BufferedReader(new FileReader("/proc/self/cgroup"));

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":", 3);
                if (parts.length != 3) {
                    continue;
                }

                String[] controllers = parts[1].split(",");
                for (String controller : controllers) {
                    if (!controller.trim().isEmpty()) {
                        result.put(controller.trim(), parts[2].trim());
                    }
                }
            }
        } finally {
            reader.close();
        }

        return result;
    }

    private File resolveV1Controller(String[] roots, String relative) {
        for (String rootPath : roots) {
            File root = new File(rootPath);
            if (!root.isDirectory()) {
                continue;
            }

            File resolved = appendRelative(root, relative);
            if (resolved.isDirectory()) {
                return resolved;
            }

            return root;
        }

        return null;
    }

    private File appendRelative(File root, String relative) {
        if (root == null || relative == null || relative.isEmpty() || "/".equals(relative)) {
            return root;
        }

        String normalized = relative.startsWith("/")
                ? relative.substring(1)
                : relative;

        return new File(root, normalized);
    }

    private File file(File parent, String child) {
        return parent == null ? null : new File(parent, child);
    }

    private long readLong(File file, long defaultValue) {
        String value = readFirstLine(file);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long readLimit(File file) {
        String value = readFirstLine(file);
        if (value == null || value.isEmpty() || "max".equalsIgnoreCase(value.trim())) {
            return -1L;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private long normalizeV1MemoryLimit(long value) {
        if (value < 0 || value >= (1L << 60)) {
            return -1L;
        }
        return value;
    }

    private double parseV2CpuQuota(String cpuMax) {
        if (cpuMax == null || cpuMax.isEmpty()) {
            return -1D;
        }

        String[] parts = cpuMax.trim().split("\\s+");
        if (parts.length < 2 || "max".equals(parts[0])) {
            return -1D;
        }

        try {
            long quota = Long.parseLong(parts[0]);
            long period = Long.parseLong(parts[1]);
            return quota > 0 && period > 0 ? quota / (double) period : -1D;
        } catch (NumberFormatException e) {
            return -1D;
        }
    }

    private Map<String, Long> readKeyLongs(File file) {
        Map<String, Long> result = new HashMap<String, Long>();
        if (file == null || !file.isFile()) {
            return result;
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 2) {
                        continue;
                    }
                    try {
                        result.put(parts[0], Long.parseLong(parts[1]));
                    } catch (NumberFormatException ignore) {
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Exception ignore) {
        }

        return result;
    }

    private long value(Map<String, Long> values, String key) {
        Long value = values.get(key);
        return value == null ? -1L : value;
    }

    private String readFirstNonBlank(File... files) {
        for (File file : files) {
            String value = readFirstLine(file);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private String readFirstLine(File file) {
        if (file == null || !file.isFile()) {
            return "";
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            try {
                String line = reader.readLine();
                return line == null ? "" : line.trim();
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            return "";
        }
    }

    private String firstNonBlank(String a, String b) {
        return a != null && !a.trim().isEmpty() ? a : b;
    }

    private boolean isLinux() {
        return System.getProperty("os.name", "")
                .toLowerCase()
                .contains("linux");
    }
}
