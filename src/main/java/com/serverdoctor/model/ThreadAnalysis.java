package com.serverdoctor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ThreadAnalysis {

    private final int sampleCount;
    private final List<SampleSummary> sampleSummaries;
    private final List<PersistentThread> persistentRunnableThreads;
    private final List<StackGroup> stackGroups;
    private final boolean deadlockDetected;
    private final List<Integer> deadlockSamples;

    public ThreadAnalysis(int sampleCount,
                          List<SampleSummary> sampleSummaries,
                          List<PersistentThread> persistentRunnableThreads,
                          List<StackGroup> stackGroups,
                          boolean deadlockDetected,
                          List<Integer> deadlockSamples) {
        this.sampleCount = sampleCount;
        this.sampleSummaries = Collections.unmodifiableList(new ArrayList<SampleSummary>(sampleSummaries));
        this.persistentRunnableThreads = Collections.unmodifiableList(new ArrayList<PersistentThread>(persistentRunnableThreads));
        this.stackGroups = Collections.unmodifiableList(new ArrayList<StackGroup>(stackGroups));
        this.deadlockDetected = deadlockDetected;
        this.deadlockSamples = Collections.unmodifiableList(new ArrayList<Integer>(deadlockSamples));
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public List<SampleSummary> getSampleSummaries() {
        return sampleSummaries;
    }

    public List<PersistentThread> getPersistentRunnableThreads() {
        return persistentRunnableThreads;
    }

    public List<StackGroup> getStackGroups() {
        return stackGroups;
    }

    public boolean isDeadlockDetected() {
        return deadlockDetected;
    }

    public List<Integer> getDeadlockSamples() {
        return deadlockSamples;
    }

    public SampleSummary getLastSampleSummary() {
        if (sampleSummaries.isEmpty()) {
            return null;
        }
        return sampleSummaries.get(sampleSummaries.size() - 1);
    }

    public static class SampleSummary {

        private final int sampleIndex;
        private final int totalThreads;
        private final Map<String, Integer> stateCounts;

        public SampleSummary(int sampleIndex, int totalThreads, Map<String, Integer> stateCounts) {
            this.sampleIndex = sampleIndex;
            this.totalThreads = totalThreads;
            this.stateCounts = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(stateCounts));
        }

        public int getSampleIndex() {
            return sampleIndex;
        }

        public int getTotalThreads() {
            return totalThreads;
        }

        public Map<String, Integer> getStateCounts() {
            return stateCounts;
        }

        public int getStateCount(String state) {
            Integer count = stateCounts.get(state);
            return count == null ? 0 : count;
        }
    }

    public static class PersistentThread {

        private final String name;
        private final String nid;
        private final int sampleHits;
        private final int stableTopFrameHits;
        private final String topFrame;
        private final List<String> stack;

        public PersistentThread(String name,
                                String nid,
                                int sampleHits,
                                int stableTopFrameHits,
                                String topFrame,
                                List<String> stack) {
            this.name = name;
            this.nid = nid;
            this.sampleHits = sampleHits;
            this.stableTopFrameHits = stableTopFrameHits;
            this.topFrame = topFrame;
            this.stack = Collections.unmodifiableList(new ArrayList<String>(stack));
        }

        public String getName() {
            return name;
        }

        public String getNid() {
            return nid;
        }

        public int getSampleHits() {
            return sampleHits;
        }

        public int getStableTopFrameHits() {
            return stableTopFrameHits;
        }

        public String getTopFrame() {
            return topFrame;
        }

        public List<String> getStack() {
            return stack;
        }
    }

    public static class StackGroup {

        private final String state;
        private final int count;
        private final String topFrame;
        private final List<String> stack;

        public StackGroup(String state, int count, String topFrame, List<String> stack) {
            this.state = state;
            this.count = count;
            this.topFrame = topFrame;
            this.stack = Collections.unmodifiableList(new ArrayList<String>(stack));
        }

        public String getState() {
            return state;
        }

        public int getCount() {
            return count;
        }

        public String getTopFrame() {
            return topFrame;
        }

        public List<String> getStack() {
            return stack;
        }
    }
}
