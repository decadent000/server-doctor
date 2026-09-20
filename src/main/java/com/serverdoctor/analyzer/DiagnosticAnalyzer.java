package com.serverdoctor.analyzer;

import com.serverdoctor.collector.JavaProcessCollector;
import com.serverdoctor.collector.SystemCollector;
import com.serverdoctor.model.ContainerMetrics;
import com.serverdoctor.model.DiagnosticResult;
import com.serverdoctor.model.JvmFlags;
import com.serverdoctor.model.JvmMetrics;
import com.serverdoctor.model.ThreadAnalysis;
import com.serverdoctor.model.ThreadCpuAnalysis;

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

    public List<DiagnosticResult> analyzeContainer(ContainerMetrics metrics) {
        List<DiagnosticResult> results = new ArrayList<DiagnosticResult>();

        if (metrics == null || !metrics.isAvailable()) {
            return results;
        }

        double memoryUsage = metrics.getMemoryUsagePercent();
        if (memoryUsage >= 95D) {
            results.add(new DiagnosticResult(
                    "HIGH",
                    "CGROUP",
                    "容器内存接近限制",
                    String.format("容器Memory使用率 %.2f%%", memoryUsage),
                    "建议确认memory limit是否合理，并检查JVM Heap、Direct Memory、线程栈和其他native内存。"
            ));
        } else if (memoryUsage >= 85D) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "CGROUP",
                    "容器内存使用率偏高",
                    String.format("容器Memory使用率 %.2f%%", memoryUsage),
                    "建议持续观察容器内存趋势，避免触发OOM Kill。"
            ));
        }

        double throttleRatio = metrics.getCpuThrottleRatioPercent();
        if (throttleRatio >= 50D) {
            results.add(new DiagnosticResult(
                    "HIGH",
                    "CGROUP",
                    "容器CPU累计节流比例很高",
                    String.format("nr_throttled / nr_periods = %.2f%%", throttleRatio),
                    "该指标为累计值。建议检查CPU quota是否过低，并结合当前CPU负载判断是否长期受限。"
            ));
        } else if (throttleRatio >= 20D) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "CGROUP",
                    "容器存在较多CPU节流",
                    String.format("nr_throttled / nr_periods = %.2f%%", throttleRatio),
                    "该指标为累计值。建议检查CPU quota与业务峰值CPU需求。"
            ));
        }

        double pidsUsage = metrics.getPidsUsagePercent();
        if (pidsUsage >= 90D) {
            results.add(new DiagnosticResult(
                    "HIGH",
                    "CGROUP",
                    "容器PID数量接近限制",
                    String.format("pids使用率 %.2f%%", pidsUsage),
                    "线程也会占用PID配额。建议检查线程总数和pids.max。"
            ));
        } else if (pidsUsage >= 75D) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "CGROUP",
                    "容器PID数量偏高",
                    String.format("pids使用率 %.2f%%", pidsUsage),
                    "建议检查Java线程数、子进程数量与pids.max配置。"
            ));
        }

        if (metrics.getOomKillCount() > 0) {
            results.add(new DiagnosticResult(
                    "HIGH",
                    "CGROUP",
                    "容器发生过OOM Kill",
                    "memory.events oom_kill=" + metrics.getOomKillCount(),
                    "建议检查容器memory limit、JVM MaxHeap、Direct Memory与历史内存峰值。"
            ));
        } else if (metrics.getOomCount() > 0) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "CGROUP",
                    "容器发生过OOM事件",
                    "memory.events oom=" + metrics.getOomCount(),
                    "建议检查容器内存限制和历史内存压力。"
            ));
        }

        if (metrics.getMemoryFailCount() > 0) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "CGROUP",
                    "cgroup v1内存限制触发过失败计数",
                    "memory.failcnt=" + metrics.getMemoryFailCount(),
                    "建议核对容器内存限制及历史峰值。"
            ));
        }

        return results;
    }

    public List<DiagnosticResult> analyzeThreadCpu(ThreadCpuAnalysis analysis) {
        List<DiagnosticResult> results = new ArrayList<DiagnosticResult>();

        if (analysis == null || !analysis.isAvailable()) {
            return results;
        }

        ThreadCpuAnalysis.HotThread hottest = analysis.getHottestThread();
        if (hottest == null) {
            return results;
        }

        String identity = hottest.isMappedToJavaThread()
                ? hottest.getThreadName() + " -> " + hottest.getTopFrame()
                : "TID=" + hottest.getTid();

        if (hottest.getCpuPercent() >= 80D) {
            results.add(new DiagnosticResult(
                    "HIGH",
                    "THREAD_CPU",
                    "发现单线程高CPU",
                    String.format("%s，短周期CPU %.2f%%", identity, hottest.getCpuPercent()),
                    "已通过Linux TID与jstack nid关联。建议优先检查该线程调用栈和对应业务循环/计算逻辑。"
            ));
        } else if (hottest.getCpuPercent() >= 50D) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "THREAD_CPU",
                    "发现较高CPU线程",
                    String.format("%s，短周期CPU %.2f%%", identity, hottest.getCpuPercent()),
                    "该值来自短周期/proc采样，建议在问题持续时重复执行以确认稳定性。"
            ));
        }

        return results;
    }

    public List<DiagnosticResult> analyzeJvm(JvmMetrics metrics, JvmFlags flags) {
        List<DiagnosticResult> results = new ArrayList<DiagnosticResult>();

        if (metrics == null || !metrics.isAvailable()) {
            return results;
        }

        double heapUsage = -1D;

        if (flags != null && flags.isAvailable() && flags.getMaxHeapSizeBytes() > 0) {
            heapUsage = metrics.getHeapUsedKb() * 1024D
                    / flags.getMaxHeapSizeBytes() * 100D;
        } else if (metrics.getHeapCapacityKb() > 0) {
            heapUsage = metrics.getHeapUsagePercent();
        }

        if (heapUsage >= 95D) {
            results.add(new DiagnosticResult(
                    "HIGH",
                    "JVM",
                    "JVM Heap使用率严重偏高",
                    String.format("当前Heap约占实际最大Heap %.2f%%", heapUsage),
                    "建议结合GC日志、Full GC次数、对象分配速度和Heap Dump继续分析。"
            ));
        } else if (heapUsage >= 85D) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "JVM",
                    "JVM Heap使用率偏高",
                    String.format("当前Heap约占实际最大Heap %.2f%%", heapUsage),
                    "建议持续观察Heap与GC趋势，确认是否存在持续增长。"
            ));
        }

        return results;
    }

    public List<DiagnosticResult> analyzeJvmCommandLine(
            JavaProcessCollector.JavaProcessInfo target,
            JvmFlags flags) {

        List<DiagnosticResult> results = new ArrayList<DiagnosticResult>();

        if (target == null || target.getCommandLine() == null) {
            return results;
        }

        List<String> misplaced = findJvmOptionsAfterJar(target.getCommandLine());

        if (!misplaced.isEmpty()) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "JVM_CONFIG",
                    "发现疑似放在应用参数区的JVM参数",
                    "这些参数位于目标jar之后，通常不会作为JVM参数生效：" + misplaced,
                    "请把-Xms/-Xmx/-XX/-D/-agentlib/-javaagent等JVM参数移动到-jar之前，并在重启后用jcmd <pid> VM.flags确认实际生效值。"
            ));
        }

        if (flags != null && flags.isAvailable()
                && flags.getMaxHeapSizeBytes() > 0
                && target.getCommandLine().contains("-Xmx")
                && (flags.getEffectiveJvmArgs() == null
                    || !flags.getEffectiveJvmArgs().contains("-Xmx"))) {

            results.add(new DiagnosticResult(
                    "WARN",
                    "JVM_CONFIG",
                    "命令行存在-Xmx，但JVM有效参数中未发现-Xmx",
                    "实际MaxHeapSize=" + formatMb(flags.getMaxHeapSizeBytes())
                            + " MB；JVM有效参数=" + safe(flags.getEffectiveJvmArgs()),
                    "这通常说明-Xmx位置错误或启动脚本未按预期传递。建议检查Docker CMD/ENTRYPOINT和启动脚本参数顺序。"
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
                    "发现持续RUNNABLE计算候选",
                    detail.toString(),
                    "已过滤常见park/socket/epoll/accept等等待栈；优先以V0.3的真实线程CPU采样结果为准。"
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

            if (last.getTotalThreads() >= 1000) {
                results.add(new DiagnosticResult(
                        "WARN",
                        "THREAD",
                        "Java线程总数较高",
                        "最后一次采样线程总数=" + last.getTotalThreads(),
                        "线程多不等于异常，但会增加线程栈内存和调度成本。建议确认连接线程、PCB线程、线程池上限和是否存在持续创建未回收的线程。"
                ));
            }
        }

        return results;
    }

    private List<String> findJvmOptionsAfterJar(String commandLine) {
        List<String> result = new ArrayList<String>();
        String[] tokens = commandLine.trim().split("\\s+");

        int jarIndex = -1;
        for (int i = 0; i < tokens.length; i++) {
            String token = stripQuotes(tokens[i]);
            if (token.endsWith(".jar")) {
                jarIndex = i;
                break;
            }
        }

        if (jarIndex < 0) {
            return result;
        }

        for (int i = jarIndex + 1; i < tokens.length; i++) {
            String token = stripQuotes(tokens[i]);

            if (token.startsWith("-Xms")
                    || token.startsWith("-Xmx")
                    || token.startsWith("-XX:")
                    || token.startsWith("-D")
                    || token.startsWith("-agentlib:")
                    || token.startsWith("-javaagent:")) {
                result.add(token);
            }
        }

        return result;
    }

    private String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }

        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "(empty)" : value;
    }

    private String formatMb(long bytes) {
        return String.format("%.2f", bytes / 1024D / 1024D);
    }

    private void analyzeCpu(double cpu, List<DiagnosticResult> results) {
        if (cpu >= 90D) {
            results.add(new DiagnosticResult(
                    "HIGH",
                    "CPU",
                    "CPU使用率严重过高",
                    String.format("当前CPU使用率 %.2f%%", cpu),
                    "建议优先查看V0.3真实线程CPU采样及对应Java调用栈。"
            ));
        } else if (cpu >= 70D) {
            results.add(new DiagnosticResult(
                    "WARN",
                    "CPU",
                    "CPU使用率偏高",
                    String.format("当前CPU使用率 %.2f%%", cpu),
                    "建议持续观察CPU趋势并检查真实线程CPU采样。"
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
