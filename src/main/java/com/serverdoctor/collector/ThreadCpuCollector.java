package com.serverdoctor.collector;

import com.serverdoctor.analyzer.ThreadDumpAnalyzer;
import com.serverdoctor.model.ThreadCpuAnalysis;
import com.serverdoctor.model.ThreadSnapshot;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ThreadCpuCollector {

    private static final int MAX_THREADS_IN_REPORT = 20;

    public ThreadCpuAnalysis collect(int pid,
                                     int sampleMillis,
                                     String jstackDump) {

        if (!isLinux()) {
            return ThreadCpuAnalysis.unavailable(
                    "线程CPU采样目前仅支持Linux /proc",
                    sampleMillis
            );
        }

        File taskDir = new File("/proc/" + pid + "/task");
        if (!taskDir.isDirectory()) {
            return ThreadCpuAnalysis.unavailable(
                    "无法访问 " + taskDir.getAbsolutePath(),
                    sampleMillis
            );
        }

        try {
            long clockTicks = detectClockTicks();
            Map<Long, Long> first = readThreadTicks(taskDir);

            long start = System.nanoTime();
            Thread.sleep(sampleMillis);
            long end = System.nanoTime();

            Map<Long, Long> second = readThreadTicks(taskDir);
            double elapsedSeconds = (end - start) / 1000000000D;

            if (elapsedSeconds <= 0D || clockTicks <= 0L) {
                return ThreadCpuAnalysis.unavailable(
                        "CPU采样时间或CLK_TCK无效",
                        sampleMillis
                );
            }

            Map<Long, ThreadSnapshot> snapshots = mapSnapshots(jstackDump);
            List<ThreadCpuAnalysis.HotThread> threads =
                    new ArrayList<ThreadCpuAnalysis.HotThread>();

            for (Map.Entry<Long, Long> entry : second.entrySet()) {
                Long before = first.get(entry.getKey());
                if (before == null) {
                    continue;
                }

                long delta = entry.getValue() - before;
                if (delta < 0L) {
                    continue;
                }

                double cpuPercent = delta * 100D / clockTicks / elapsedSeconds;
                ThreadSnapshot snapshot = snapshots.get(entry.getKey());

                threads.add(toHotThread(entry.getKey(), cpuPercent, snapshot));
            }

            Collections.sort(threads, new Comparator<ThreadCpuAnalysis.HotThread>() {
                @Override
                public int compare(ThreadCpuAnalysis.HotThread a,
                                   ThreadCpuAnalysis.HotThread b) {
                    return Double.compare(b.getCpuPercent(), a.getCpuPercent());
                }
            });

            if (threads.size() > MAX_THREADS_IN_REPORT) {
                threads = new ArrayList<ThreadCpuAnalysis.HotThread>(
                        threads.subList(0, MAX_THREADS_IN_REPORT)
                );
            }

            return new ThreadCpuAnalysis(
                    true,
                    "/proc/<pid>/task短周期CPU采样",
                    sampleMillis,
                    clockTicks,
                    threads
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ThreadCpuAnalysis.unavailable("线程CPU采样被中断", sampleMillis);
        } catch (Exception e) {
            return ThreadCpuAnalysis.unavailable(
                    "线程CPU采样失败：" + e.getMessage(),
                    sampleMillis
            );
        }
    }

    private Map<Long, Long> readThreadTicks(File taskDir) {
        Map<Long, Long> result = new HashMap<Long, Long>();
        File[] tids = taskDir.listFiles();

        if (tids == null) {
            return result;
        }

        for (File tidDir : tids) {
            if (!tidDir.isDirectory()) {
                continue;
            }

            long tid;
            try {
                tid = Long.parseLong(tidDir.getName());
            } catch (NumberFormatException e) {
                continue;
            }

            Long ticks = readStatTicks(new File(tidDir, "stat"));
            if (ticks != null) {
                result.put(tid, ticks);
            }
        }

        return result;
    }

    private Long readStatTicks(File statFile) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(statFile));
            String line;
            try {
                line = reader.readLine();
            } finally {
                reader.close();
            }

            if (line == null) {
                return null;
            }

            int closingParen = line.lastIndexOf(')');
            if (closingParen < 0 || closingParen + 2 >= line.length()) {
                return null;
            }

            String rest = line.substring(closingParen + 2).trim();
            String[] fields = rest.split("\\s+");

            // rest从/proc stat的第3列state开始：
            // field 14(utime) -> index 11，field 15(stime) -> index 12。
            if (fields.length <= 12) {
                return null;
            }

            long utime = Long.parseLong(fields[11]);
            long stime = Long.parseLong(fields[12]);

            return utime + stime;

        } catch (Exception e) {
            return null;
        }
    }

    private long detectClockTicks() {
        try {
            ProcessBuilder builder = new ProcessBuilder("getconf", "CLK_TCK");
            builder.redirectErrorStream(true);
            Process process = builder.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            );

            String line = reader.readLine();
            boolean finished = process.waitFor(3L, TimeUnit.SECONDS);
            reader.close();

            if (finished && process.exitValue() == 0 && line != null) {
                long value = Long.parseLong(line.trim());
                if (value > 0L) {
                    return value;
                }
            }
        } catch (Exception ignore) {
        }

        // Linux绝大多数环境为100；getconf不可用时使用保守回退值。
        return 100L;
    }

    private Map<Long, ThreadSnapshot> mapSnapshots(String dump) {
        Map<Long, ThreadSnapshot> result = new HashMap<Long, ThreadSnapshot>();

        if (dump == null || dump.trim().isEmpty()) {
            return result;
        }

        List<ThreadSnapshot> snapshots =
                new ThreadDumpAnalyzer().parseSnapshots(dump);

        for (ThreadSnapshot snapshot : snapshots) {
            Long tid = nativeIdToTid(snapshot.getNid());
            if (tid != null) {
                result.put(tid, snapshot);
            }
        }

        return result;
    }

    private Long nativeIdToTid(String nid) {
        if (nid == null || nid.trim().isEmpty()) {
            return null;
        }

        String value = nid.trim();

        try {
            if (value.startsWith("0x") || value.startsWith("0X")) {
                return Long.parseLong(value.substring(2), 16);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ThreadCpuAnalysis.HotThread toHotThread(
            long tid,
            double cpuPercent,
            ThreadSnapshot snapshot) {

        String nidHex = "0x" + Long.toHexString(tid);

        if (snapshot == null) {
            return new ThreadCpuAnalysis.HotThread(
                    tid,
                    nidHex,
                    cpuPercent,
                    false,
                    "",
                    "",
                    "",
                    Collections.<String>emptyList()
            );
        }

        String topFrame = snapshot.getFrames().isEmpty()
                ? ""
                : snapshot.getFrames().get(0);

        List<String> stack = snapshot.getFrames().size() <= 12
                ? snapshot.getFrames()
                : new ArrayList<String>(snapshot.getFrames().subList(0, 12));

        return new ThreadCpuAnalysis.HotThread(
                tid,
                nidHex,
                cpuPercent,
                true,
                snapshot.getName(),
                snapshot.getState(),
                topFrame,
                stack
        );
    }

    private boolean isLinux() {
        return System.getProperty("os.name", "")
                .toLowerCase()
                .contains("linux");
    }
}
