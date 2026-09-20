package com.serverdoctor.model;

public class JvmMetrics {

    private final boolean available;
    private final String message;
    private final double heapCapacityKb;
    private final double heapUsedKb;
    private final double metaspaceCapacityKb;
    private final double metaspaceUsedKb;
    private final long youngGcCount;
    private final double youngGcTimeSeconds;
    private final long fullGcCount;
    private final double fullGcTimeSeconds;
    private final double totalGcTimeSeconds;

    public JvmMetrics(boolean available,
                      String message,
                      double heapCapacityKb,
                      double heapUsedKb,
                      double metaspaceCapacityKb,
                      double metaspaceUsedKb,
                      long youngGcCount,
                      double youngGcTimeSeconds,
                      long fullGcCount,
                      double fullGcTimeSeconds,
                      double totalGcTimeSeconds) {
        this.available = available;
        this.message = message;
        this.heapCapacityKb = heapCapacityKb;
        this.heapUsedKb = heapUsedKb;
        this.metaspaceCapacityKb = metaspaceCapacityKb;
        this.metaspaceUsedKb = metaspaceUsedKb;
        this.youngGcCount = youngGcCount;
        this.youngGcTimeSeconds = youngGcTimeSeconds;
        this.fullGcCount = fullGcCount;
        this.fullGcTimeSeconds = fullGcTimeSeconds;
        this.totalGcTimeSeconds = totalGcTimeSeconds;
    }

    public static JvmMetrics unavailable(String message) {
        return new JvmMetrics(false, message, -1, -1, -1, -1, -1, -1, -1, -1, -1);
    }

    public boolean isAvailable() {
        return available;
    }

    public String getMessage() {
        return message;
    }

    public double getHeapCapacityKb() {
        return heapCapacityKb;
    }

    public double getHeapUsedKb() {
        return heapUsedKb;
    }

    public double getHeapUsagePercent() {
        return heapCapacityKb > 0 ? heapUsedKb / heapCapacityKb * 100D : -1D;
    }

    public double getMetaspaceCapacityKb() {
        return metaspaceCapacityKb;
    }

    public double getMetaspaceUsedKb() {
        return metaspaceUsedKb;
    }

    public double getMetaspaceUsagePercent() {
        return metaspaceCapacityKb > 0 ? metaspaceUsedKb / metaspaceCapacityKb * 100D : -1D;
    }

    public long getYoungGcCount() {
        return youngGcCount;
    }

    public double getYoungGcTimeSeconds() {
        return youngGcTimeSeconds;
    }

    public long getFullGcCount() {
        return fullGcCount;
    }

    public double getFullGcTimeSeconds() {
        return fullGcTimeSeconds;
    }

    public double getTotalGcTimeSeconds() {
        return totalGcTimeSeconds;
    }
}
