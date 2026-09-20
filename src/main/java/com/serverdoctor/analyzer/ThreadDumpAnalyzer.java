package com.serverdoctor.analyzer;

import com.serverdoctor.model.ThreadAnalysis;
import com.serverdoctor.model.ThreadSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThreadDumpAnalyzer {

    private static final Pattern NID_PATTERN = Pattern.compile("\\bnid=(0x[0-9a-fA-F]+|[0-9]+)");

    public ThreadAnalysis analyze(List<String> dumps) {
        List<List<ThreadSnapshot>> samples = new ArrayList<List<ThreadSnapshot>>();
        List<ThreadAnalysis.SampleSummary> summaries = new ArrayList<ThreadAnalysis.SampleSummary>();
        List<Integer> deadlockSamples = new ArrayList<Integer>();

        for (int i = 0; i < dumps.size(); i++) {
            String dump = dumps.get(i);
            List<ThreadSnapshot> threads = parseThreads(dump);
            samples.add(threads);
            summaries.add(buildSummary(i + 1, threads));

            if (containsDeadlock(dump)) {
                deadlockSamples.add(i + 1);
            }
        }

        List<ThreadAnalysis.PersistentThread> persistent = findPersistentRunnable(samples);
        List<ThreadAnalysis.StackGroup> groups = samples.isEmpty()
                ? Collections.<ThreadAnalysis.StackGroup>emptyList()
                : aggregateStacks(samples.get(samples.size() - 1));

        return new ThreadAnalysis(
                dumps.size(),
                summaries,
                persistent,
                groups,
                !deadlockSamples.isEmpty(),
                deadlockSamples
        );
    }

    private List<ThreadSnapshot> parseThreads(String dump) {
        List<ThreadSnapshot> threads = new ArrayList<ThreadSnapshot>();

        String currentName = null;
        String currentNid = null;
        String currentState = null;
        List<String> currentFrames = new ArrayList<String>();

        String[] lines = dump.split("\\r?\\n");

        for (String line : lines) {
            if (line.startsWith("\"")) {
                addThread(threads, currentName, currentNid, currentState, currentFrames);

                int secondQuote = line.indexOf('"', 1);
                currentName = secondQuote > 1 ? line.substring(1, secondQuote) : line;
                currentNid = extractNid(line);
                currentState = "UNKNOWN";
                currentFrames = new ArrayList<String>();
                continue;
            }

            if (currentName == null) {
                continue;
            }

            String trimmed = line.trim();

            if (trimmed.startsWith("java.lang.Thread.State:")) {
                String value = trimmed.substring("java.lang.Thread.State:".length()).trim();
                int space = value.indexOf(' ');
                currentState = space > 0 ? value.substring(0, space) : value;
            } else if (trimmed.startsWith("at ")) {
                currentFrames.add(trimmed);
            }
        }

        addThread(threads, currentName, currentNid, currentState, currentFrames);
        return threads;
    }

    private void addThread(List<ThreadSnapshot> threads,
                           String name,
                           String nid,
                           String state,
                           List<String> frames) {
        if (name != null) {
            threads.add(new ThreadSnapshot(
                    name,
                    nid == null ? "" : nid,
                    state == null ? "UNKNOWN" : state,
                    frames == null ? Collections.<String>emptyList() : frames
            ));
        }
    }

    private String extractNid(String header) {
        Matcher matcher = NID_PATTERN.matcher(header);
        return matcher.find() ? matcher.group(1) : "";
    }

    private ThreadAnalysis.SampleSummary buildSummary(int index, List<ThreadSnapshot> threads) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();

        for (ThreadSnapshot thread : threads) {
            String state = thread.getState();
            Integer current = counts.get(state);
            counts.put(state, current == null ? 1 : current + 1);
        }

        return new ThreadAnalysis.SampleSummary(index, threads.size(), counts);
    }

    private List<ThreadAnalysis.PersistentThread> findPersistentRunnable(
            List<List<ThreadSnapshot>> samples) {

        if (samples.size() < 2) {
            return Collections.emptyList();
        }

        Map<String, PersistentAccumulator> accumulators =
                new HashMap<String, PersistentAccumulator>();

        for (List<ThreadSnapshot> sample : samples) {
            for (ThreadSnapshot thread : sample) {
                if (!"RUNNABLE".equals(thread.getState()) || thread.getFrames().isEmpty()) {
                    continue;
                }

                String topFrame = normalizeFrame(thread.getFrames().get(0));
                if (isKnownIdleFrame(topFrame)) {
                    continue;
                }

                String identity = thread.identity();
                PersistentAccumulator accumulator = accumulators.get(identity);

                if (accumulator == null) {
                    accumulator = new PersistentAccumulator(thread);
                    accumulators.put(identity, accumulator);
                }

                accumulator.sampleHits++;
                accumulator.lastThread = thread;

                Integer count = accumulator.topFrameCounts.get(topFrame);
                accumulator.topFrameCounts.put(topFrame, count == null ? 1 : count + 1);
            }
        }

        List<ThreadAnalysis.PersistentThread> result =
                new ArrayList<ThreadAnalysis.PersistentThread>();

        for (PersistentAccumulator accumulator : accumulators.values()) {
            if (accumulator.sampleHits != samples.size()) {
                continue;
            }

            String bestFrame = null;
            int bestHits = 0;

            for (Map.Entry<String, Integer> entry : accumulator.topFrameCounts.entrySet()) {
                if (entry.getValue() > bestHits) {
                    bestFrame = entry.getKey();
                    bestHits = entry.getValue();
                }
            }

            int requiredStableHits = Math.max(2, samples.size() - 1);
            if (bestHits < requiredStableHits) {
                continue;
            }

            result.add(new ThreadAnalysis.PersistentThread(
                    accumulator.lastThread.getName(),
                    accumulator.lastThread.getNid(),
                    accumulator.sampleHits,
                    bestHits,
                    bestFrame,
                    limitFrames(accumulator.lastThread.getFrames(), 12)
            ));
        }

        Collections.sort(result, new Comparator<ThreadAnalysis.PersistentThread>() {
            @Override
            public int compare(ThreadAnalysis.PersistentThread a, ThreadAnalysis.PersistentThread b) {
                int stableCompare = Integer.compare(b.getStableTopFrameHits(), a.getStableTopFrameHits());
                if (stableCompare != 0) {
                    return stableCompare;
                }
                return a.getName().compareTo(b.getName());
            }
        });

        return result;
    }

    private List<ThreadAnalysis.StackGroup> aggregateStacks(List<ThreadSnapshot> threads) {
        Map<String, StackAccumulator> groups = new HashMap<String, StackAccumulator>();

        for (ThreadSnapshot thread : threads) {
            if (thread.getFrames().isEmpty()) {
                continue;
            }

            String signature = thread.getState() + "|" + stackSignature(thread.getFrames(), 8);
            StackAccumulator accumulator = groups.get(signature);

            if (accumulator == null) {
                accumulator = new StackAccumulator(thread);
                groups.put(signature, accumulator);
            }

            accumulator.count++;
        }

        List<ThreadAnalysis.StackGroup> result =
                new ArrayList<ThreadAnalysis.StackGroup>();

        for (StackAccumulator accumulator : groups.values()) {
            if (accumulator.count < 2) {
                continue;
            }

            result.add(new ThreadAnalysis.StackGroup(
                    accumulator.thread.getState(),
                    accumulator.count,
                    normalizeFrame(accumulator.thread.getFrames().get(0)),
                    limitFrames(accumulator.thread.getFrames(), 10)
            ));
        }

        Collections.sort(result, new Comparator<ThreadAnalysis.StackGroup>() {
            @Override
            public int compare(ThreadAnalysis.StackGroup a, ThreadAnalysis.StackGroup b) {
                return Integer.compare(b.getCount(), a.getCount());
            }
        });

        if (result.size() > 15) {
            return new ArrayList<ThreadAnalysis.StackGroup>(result.subList(0, 15));
        }

        return result;
    }

    private String stackSignature(List<String> frames, int limit) {
        StringBuilder builder = new StringBuilder();
        int size = Math.min(limit, frames.size());

        for (int i = 0; i < size; i++) {
            builder.append(normalizeFrame(frames.get(i))).append('\n');
        }

        return builder.toString();
    }

    private String normalizeFrame(String frame) {
        return frame.replaceAll(":\\d+\\)", ")");
    }

    private List<String> limitFrames(List<String> frames, int limit) {
        int size = Math.min(limit, frames.size());
        return new ArrayList<String>(frames.subList(0, size));
    }

    private boolean isKnownIdleFrame(String frame) {
        return frame.contains("sun.misc.Unsafe.park")
                || frame.contains("jdk.internal.misc.Unsafe.park")
                || frame.contains("java.lang.Object.wait")
                || frame.contains("java.lang.Thread.sleep")
                || frame.contains("java.lang.ref.Reference.waitForReferencePendingList")
                || frame.contains("sun.nio.ch.EPollArrayWrapper.epollWait")
                || frame.contains("sun.nio.ch.EPoll.wait")
                || frame.contains("sun.nio.ch.KQueueArrayWrapper.kevent0")
                || frame.contains("sun.nio.ch.KQueue.poll")
                || frame.contains("java.net.PlainSocketImpl.socketAccept")
                || frame.contains("java.net.SocketInputStream.socketRead0")
                || frame.contains("sun.nio.ch.WindowsSelectorImpl$SubSelector.poll0")
                || frame.contains("io.netty.channel.epoll.Native.epollWait")
                || frame.contains("io.netty.channel.kqueue.Native.keventWait");
    }

    private boolean containsDeadlock(String dump) {
        String lower = dump.toLowerCase();
        return lower.contains("found one java-level deadlock")
                || lower.matches("(?s).*found\\s+[1-9][0-9]*\\s+deadlock.*");
    }

    private static class PersistentAccumulator {

        private ThreadSnapshot lastThread;
        private int sampleHits;
        private final Map<String, Integer> topFrameCounts = new HashMap<String, Integer>();

        private PersistentAccumulator(ThreadSnapshot thread) {
            this.lastThread = thread;
        }
    }

    private static class StackAccumulator {

        private final ThreadSnapshot thread;
        private int count;

        private StackAccumulator(ThreadSnapshot thread) {
            this.thread = thread;
        }
    }
}
