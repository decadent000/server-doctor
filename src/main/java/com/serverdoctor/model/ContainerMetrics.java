package com.serverdoctor.model;

public class ContainerMetrics {

    private final boolean available;
    private final String message;
    private final String cgroupVersion;
    private final long memoryUsageBytes;
    private final long memoryLimitBytes;
    private final double cpuQuotaCores;
    private final String cpusetCpus;
    private final long cpuPeriods;
    private final long cpuThrottledPeriods;
    private final double cpuThrottledTimeMillis;
    private final long pidsCurrent;
    private final long pidsMax;
    private final long oomCount;
    private final long oomKillCount;
    private final long memoryFailCount;

    public ContainerMetrics(boolean available,
                            String message,
                            String cgroupVersion,
                            long memoryUsageBytes,
                            long memoryLimitBytes,
                            double cpuQuotaCores,
                            String cpusetCpus,
                            long cpuPeriods,
                            long cpuThrottledPeriods,
                            double cpuThrottledTimeMillis,
                            long pidsCurrent,
                            long pidsMax,
                            long oomCount,
                            long oomKillCount,
                            long memoryFailCount) {
        this.available = available;
        this.message = message;
        this.cgroupVersion = cgroupVersion;
        this.memoryUsageBytes = memoryUsageBytes;
        this.memoryLimitBytes = memoryLimitBytes;
        this.cpuQuotaCores = cpuQuotaCores;
        this.cpusetCpus = cpusetCpus;
        this.cpuPeriods = cpuPeriods;
        this.cpuThrottledPeriods = cpuThrottledPeriods;
        this.cpuThrottledTimeMillis = cpuThrottledTimeMillis;
        this.pidsCurrent = pidsCurrent;
        this.pidsMax = pidsMax;
        this.oomCount = oomCount;
        this.oomKillCount = oomKillCount;
        this.memoryFailCount = memoryFailCount;
    }

    public static ContainerMetrics unavailable(String message) {
        return new ContainerMetrics(
                false, message, "unknown",
                -1L, -1L, -1D, "",
                -1L, -1L, -1D,
                -1L, -1L, -1L, -1L, -1L
        );
    }

    public boolean isAvailable() {
        return available;
    }

    public String getMessage() {
        return message;
    }

    public String getCgroupVersion() {
        return cgroupVersion;
    }

    public long getMemoryUsageBytes() {
        return memoryUsageBytes;
    }

    public long getMemoryLimitBytes() {
        return memoryLimitBytes;
    }

    public double getMemoryUsagePercent() {
        if (memoryUsageBytes < 0 || memoryLimitBytes <= 0) {
            return -1D;
        }
        return memoryUsageBytes * 100D / memoryLimitBytes;
    }

    public double getCpuQuotaCores() {
        return cpuQuotaCores;
    }

    public String getCpusetCpus() {
        return cpusetCpus;
    }

    public long getCpuPeriods() {
        return cpuPeriods;
    }

    public long getCpuThrottledPeriods() {
        return cpuThrottledPeriods;
    }

    public double getCpuThrottleRatioPercent() {
        if (cpuPeriods <= 0 || cpuThrottledPeriods < 0) {
            return -1D;
        }
        return cpuThrottledPeriods * 100D / cpuPeriods;
    }

    public double getCpuThrottledTimeMillis() {
        return cpuThrottledTimeMillis;
    }

    public long getPidsCurrent() {
        return pidsCurrent;
    }

    public long getPidsMax() {
        return pidsMax;
    }

    public double getPidsUsagePercent() {
        if (pidsCurrent < 0 || pidsMax <= 0) {
            return -1D;
        }
        return pidsCurrent * 100D / pidsMax;
    }

    public long getOomCount() {
        return oomCount;
    }

    public long getOomKillCount() {
        return oomKillCount;
    }

    public long getMemoryFailCount() {
        return memoryFailCount;
    }
}
