package com.serverdoctor.analyzer;

import com.serverdoctor.collector.SystemCollector;
import com.serverdoctor.model.DiagnosticResult;
import com.serverdoctor.model.JvmMetrics;
import com.serverdoctor.model.ThreadAnalysis;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticAnalyzer {

    public List<DiagnosticResult> analyzeSystem(
            double cpu,
            double memory,
            List<SystemCollector.DiskInfo> disks) {

        List<DiagnosticResult> results = new ArrayList<DiagnosticResult>();

        analyzeCpu(cpu, results);
        analyzeMemory(memory, results);
        analyzeDisks(disks, results);

        return results;
    }

    public List<DiagnosticResult> analyzeJvm(JvmMetrics metrics) {
        List<DiagnosticResult> results = new ArrayList<DiagnosticResult>();

        if (metrics == null || !metrics.isAvailable()) {
            return results;
        }

        double heapUsage = metrics.getHeapUsagePercent();

        if (heapUsage >= 95D) {
            results.add(new DiagnosticResult(
                    "HIGH",
                    "JVM",
                    "JVM Heap使用率严重偏高",
                    String.format("当前Heap使用率 %.2f%%", heapUsage),
                    "建议结合GC日志、Full GC次数、对象分配速度和Heap Dump继续分析。"
            ));
        } else if (heapUsage >= 85D) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "JVM",
                    "JVM Heap使用率偏高",
                    String.format("当前Heap使用率 %.2f%%", heapUsage),
                    "建议持续观察Heap与GC趋势，确认是否存在持续增长。"
            ));
        }

        return results;
    }

    public List<DiagnosticResult> analyzeThreads(ThreadAnalysis analysis) {
        List<DiagnosticResult> results = new ArrayList<DiagnosticResult>();

        if (analysis == null) {
            return results;
        }

        if (analysis.isDeadlockDetected()) {
            results.add(new DiagnosticResult(
                    "HIGH",
                    "THREAD",
                    "检测到Java级死锁",
                    "在线程栈采样中发现死锁标记，样本：" + analysis.getDeadlockSamples(),
                    "建议立即查看对应jstack原始文件中的deadlock段，确认互相等待的锁和业务线程。"
            ));
        }

        if (!analysis.getPersistentRunnableThreads().isEmpty()) {
            StringBuilder detail = new StringBuilder();
            int limit = Math.min(3, analysis.getPersistentRunnableThreads().size());

            for (int i = 0; i < limit; i++) {
                ThreadAnalysis.PersistentThread thread =
                        analysis.getPersistentRunnableThreads().get(i);

                if (i > 0) {
                    detail.append("；");
                }

                detail.append(thread.getName())
                        .append(" -> ")
                        .append(thread.getTopFrame());
            }

            results.add(new DiagnosticResult(
                    "WARN",
                    "THREAD",
                    "发现持续RUNNABLE热点候选",
                    detail.toString(),
                    "该结论来自多次jstack持续RUNNABLE与稳定调用栈，不等同于CPU采样；建议结合top -H、pidstat或async-profiler进一步确认。"
            ));
        }

        ThreadAnalysis.SampleSummary last = analysis.getLastSampleSummary();
        if (last != null) {
            int blocked = last.getStateCount("BLOCKED");
            if (blocked >= 10) {
                results.add(new DiagnosticResult(
                        "WARN",
                        "THREAD",
                        "BLOCKED线程数量较多",
                        "最后一次采样发现 " + blocked + " 个BLOCKED线程。",
                        "建议检查锁竞争、synchronized临界区、数据库/Redis调用外围锁以及线程池任务堆积。"
                ));
            }
        }

        return results;
    }

    private void analyzeCpu(double cpu, List<DiagnosticResult> results) {
        if (cpu >= 90D) {
            results.add(new DiagnosticResult(
                    "HIGH",
                    "CPU",
                    "CPU使用率严重过高",
                    String.format("当前CPU使用率 %.2f%%", cpu),
                    "建议立即检查Java热点线程、死循环、频繁GC以及高计算量任务。"
            ));
        } else if (cpu >= 70D) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "CPU",
                    "CPU使用率偏高",
                    String.format("当前CPU使用率 %.2f%%", cpu),
                    "建议持续观察CPU趋势并检查Java进程CPU占用。"
            ));
        }
    }

    private void analyzeMemory(double memory, List<DiagnosticResult> results) {
        if (memory >= 90D) {
            results.add(new DiagnosticResult(
                    "HIGH",
                    "MEMORY",
                    "系统内存严重不足",
                    String.format("当前内存使用率 %.2f%%", memory),
                    "建议检查JVM Heap、Direct Memory以及其他高内存进程。"
            ));
        } else if (memory >= 80D) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "MEMORY",
                    "系统内存偏高",
                    String.format("当前内存使用率 %.2f%%", memory),
                    "建议检查JVM内存配置及系统剩余内存。"
            ));
        }
    }

    private void analyzeDisks(List<SystemCollector.DiskInfo> disks,
                              List<DiagnosticResult> results) {

        for (SystemCollector.DiskInfo disk : disks) {
            if (disk.getUsage() >= 95D) {
                results.add(new DiagnosticResult(
                        "HIGH",
                        "DISK",
                        "磁盘空间即将耗尽",
                        String.format("%s 使用率 %.2f%%", disk.getMount(), disk.getUsage()),
                        "建议检查应用日志、Docker日志、临时文件以及历史备份。"
                ));
            } else if (disk.getUsage() >= 85D) {
                results.add(new DiagnosticResult(
                        "WARN",
                        "DISK",
                        "磁盘空间偏高",
                        String.format("%s 使用率 %.2f%%", disk.getMount(), disk.getUsage()),
                        "建议提前清理日志并配置日志滚动策略。"
                ));
            }
        }
    }
}
