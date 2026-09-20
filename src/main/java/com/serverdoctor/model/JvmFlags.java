package com.serverdoctor.model;

public class JvmFlags {

    private final boolean available;
    private final String message;
    private final long initialHeapSizeBytes;
    private final long maxHeapSizeBytes;
    private final String garbageCollector;
    private final String effectiveJvmArgs;
    private final String javaCommand;

    public JvmFlags(boolean available,
                    String message,
                    long initialHeapSizeBytes,
                    long maxHeapSizeBytes,
                    String garbageCollector,
                    String effectiveJvmArgs,
                    String javaCommand) {
        this.available = available;
        this.message = message;
        this.initialHeapSizeBytes = initialHeapSizeBytes;
        this.maxHeapSizeBytes = maxHeapSizeBytes;
        this.garbageCollector = garbageCollector;
        this.effectiveJvmArgs = effectiveJvmArgs;
        this.javaCommand = javaCommand;
    }

    public static JvmFlags unavailable(String message) {
        return new JvmFlags(false, message, -1L, -1L, "", "", "");
    }

    public boolean isAvailable() {
        return available;
    }

    public String getMessage() {
        return message;
    }

    public long getInitialHeapSizeBytes() {
        return initialHeapSizeBytes;
    }

    public long getMaxHeapSizeBytes() {
        return maxHeapSizeBytes;
    }

    public String getGarbageCollector() {
        return garbageCollector;
    }

    public String getEffectiveJvmArgs() {
        return effectiveJvmArgs;
    }

    public String getJavaCommand() {
        return javaCommand;
    }
}
