package com.serverdoctor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThreadCpuAnalysis {

    private final boolean available;
    private final String message;
    private final int sampleMillis;
    private final long clockTicksPerSecond;
    private final List<HotThread> hotThreads;

    public ThreadCpuAnalysis(boolean available,
                             String message,
                             int sampleMillis,
                             long clockTicksPerSecond,
                             List<HotThread> hotThreads) {
        this.available = available;
        this.message = message;
        this.sampleMillis = sampleMillis;
        this.clockTicksPerSecond = clockTicksPerSecond;
        this.hotThreads = Collections.unmodifiableList(new ArrayList<HotThread>(hotThreads));
    }

    public static ThreadCpuAnalysis unavailable(String message, int sampleMillis) {
        return new ThreadCpuAnalysis(
                false,
                message,
                sampleMillis,
                -1L,
                Collections.<HotThread>emptyList()
        );
    }

    public boolean isAvailable() {
        return available;
    }

    public String getMessage() {
        return message;
    }

    public int getSampleMillis() {
        return sampleMillis;
    }

    public long getClockTicksPerSecond() {
        return clockTicksPerSecond;
    }

    public List<HotThread> getHotThreads() {
        return hotThreads;
    }

    public HotThread getHottestThread() {
        return hotThreads.isEmpty() ? null : hotThreads.get(0);
    }

    public static class HotThread {

        private final long tid;
        private final String nidHex;
        private final double cpuPercent;
        private final boolean mappedToJavaThread;
        private final String threadName;
        private final String state;
        private final String topFrame;
        private final List<String> stack;

        public HotThread(long tid,
                         String nidHex,
                         double cpuPercent,
                         boolean mappedToJavaThread,
                         String threadName,
                         String state,
                         String topFrame,
                         List<String> stack) {
            this.tid = tid;
            this.nidHex = nidHex;
            this.cpuPercent = cpuPercent;
            this.mappedToJavaThread = mappedToJavaThread;
            this.threadName = threadName;
            this.state = state;
            this.topFrame = topFrame;
            this.stack = Collections.unmodifiableList(new ArrayList<String>(stack));
        }

        public long getTid() {
            return tid;
        }

        public String getNidHex() {
            return nidHex;
        }

        public double getCpuPercent() {
            return cpuPercent;
        }

        public boolean isMappedToJavaThread() {
            return mappedToJavaThread;
        }

        public String getThreadName() {
            return threadName;
        }

        public String getState() {
            return state;
        }

        public String getTopFrame() {
            return topFrame;
        }

        public List<String> getStack() {
            return stack;
        }
    }
}
