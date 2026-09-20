package com.serverdoctor;

import com.serverdoctor.analyzer.DiagnosticAnalyzer;
import com.serverdoctor.analyzer.ThreadDumpAnalyzer;
import com.serverdoctor.collector.JavaProcessCollector;
import com.serverdoctor.collector.JvmMetricsCollector;
import com.serverdoctor.collector.SystemCollector;
import com.serverdoctor.collector.ThreadDumpCollector;
import com.serverdoctor.config.RunConfig;
import com.serverdoctor.model.DiagnosticResult;
import com.serverdoctor.model.JvmMetrics;
import com.serverdoctor.model.ThreadAnalysis;
import com.serverdoctor.report.HtmlReportGenerator;
import com.serverdoctor.util.OutputDirectoryManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class ServerDoctorApplication {

    public static void main(String[] args) throws Exception {
        RunConfig config;

        try {
            config = RunConfig.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误：" + e.getMessage());
            printUsage();
            return;
        }

        if (config.isHelp()) {
            printUsage();
            return;
        }

        printBanner();

        int selfPid = getCurrentPid();
        if (selfPid > 0) {
            System.out.println("Server Doctor PID: " + selfPid + "（自动排除）");
        }

        SystemCollector systemCollector = new SystemCollector();

        System.out.println();
        System.out.println("正在检查服务器...");

        double cpu = systemCollector.getCpuUsage();
        double memory = systemCollector.getMemoryUsage();
        List<SystemCollector.DiskInfo> disks = systemCollector.getDiskUsage();

        System.out.printf("CPU: %.2f%%%n", cpu);
        System.out.printf("Memory: %.2f%%%n", memory);

        for (SystemCollector.DiskInfo disk : disks) {
            System.out.printf(
                    "Disk %s: %.2f%% (可用 %.2f GB / 总计 %.2f GB)%n",
                    disk.getMount(),
                    disk.getUsage(),
                    toGb(disk.getUsable()),
                    toGb(disk.getTotal())
            );
        }

        System.out.println();
        System.out.println("正在查找Java进程...");

        JavaProcessCollector processCollector = new JavaProcessCollector();
        List<JavaProcessCollector.JavaProcessInfo> javaProcesses =
                processCollector.findJavaProcesses(selfPid);

        if (javaProcesses.isEmpty()) {
            System.out.println("排除Server Doctor自身后，未发现其他Java进程。");
        }

        for (JavaProcessCollector.JavaProcessInfo process : javaProcesses) {
            printJavaProcess(process);
        }

        JavaProcessCollector.JavaProcessInfo target =
                selectTargetProcess(javaProcesses, config.getPid(), selfPid);

        File runDir = OutputDirectoryManager.createRunDirectory(
                config.getOutputRoot(),
                target == null ? null : target.getPid()
        );

        System.out.println();
        System.out.println("本次诊断目录：" + runDir.getAbsolutePath());

        DiagnosticAnalyzer analyzer = new DiagnosticAnalyzer();
        List<DiagnosticResult> results =
                new ArrayList<DiagnosticResult>(analyzer.analyzeSystem(cpu, memory, disks));

        JvmMetrics jvmMetrics = null;
        ThreadAnalysis threadAnalysis = null;

        if (target != null) {
            System.out.println();
            System.out.println("诊断目标：PID=" + target.getPid());

            jvmMetrics = new JvmMetricsCollector().collect(target.getPid());
            printJvmMetrics(jvmMetrics);
            results.addAll(analyzer.analyzeJvm(jvmMetrics));

            List<String> dumps = collectThreadDumps(
                    target,
                    runDir,
                    config.getSamples(),
                    config.getIntervalSeconds()
            );

            threadAnalysis = new ThreadDumpAnalyzer().analyze(dumps);
            results.addAll(analyzer.analyzeThreads(threadAnalysis));

            printThreadSummary(threadAnalysis);
        }

        HtmlReportGenerator generator = new HtmlReportGenerator();
        File report = generator.generate(
                runDir,
                results,
                cpu,
                memory,
                disks,
                target,
                jvmMetrics,
                threadAnalysis,
                config.getIntervalSeconds()
        );

        System.out.println();
        System.out.println("================================");
        System.out.println("诊断完成");
        System.out.println("发现异常/关注项：" + results.size());
        System.out.println("诊断目录：" + runDir.getAbsolutePath());
        System.out.println("HTML报告：" + report.getAbsolutePath());
        System.out.println("================================");
    }

    private static List<String> collectThreadDumps(
            JavaProcessCollector.JavaProcessInfo process,
            File runDir,
            int samples,
            int intervalSeconds) throws IOException, InterruptedException {

        List<String> dumps = new ArrayList<String>();
        ThreadDumpCollector collector = new ThreadDumpCollector();

        for (int i = 1; i <= samples; i++) {
            System.out.println();
            System.out.println("正在采集线程栈 " + i + "/" + samples + "，PID=" + process.getPid());

            String dump = collector.collect(process.getPid());
            dumps.add(dump);

            File sampleFile = new File(runDir, "jstack-" + i + ".txt");
            writeText(sampleFile, dump);
            System.out.println("已保存：" + sampleFile.getAbsolutePath());

            if (i < samples) {
                System.out.println("等待 " + intervalSeconds + " 秒后进行下一次采样...");
                Thread.sleep(intervalSeconds * 1000L);
            }
        }

        if (!dumps.isEmpty()) {
            File compatibilityFile = new File(runDir, "jstack.txt");
            writeText(compatibilityFile, dumps.get(dumps.size() - 1));
        }

        return dumps;
    }

    private static void writeText(File file, String text) throws IOException {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(text == null ? "" : text);
        } finally {
            writer.close();
        }
    }

    private static JavaProcessCollector.JavaProcessInfo selectTargetProcess(
            List<JavaProcessCollector.JavaProcessInfo> javaProcesses,
            Integer requestedPid,
            int selfPid) {

        if (requestedPid != null) {
            if (selfPid > 0 && requestedPid == selfPid) {
                System.err.println("不能诊断Server Doctor自身进程 PID=" + selfPid);
                return null;
            }

            for (JavaProcessCollector.JavaProcessInfo process : javaProcesses) {
                if (process.getPid() == requestedPid) {
                    return process;
                }
            }

            System.err.println();
            System.err.println("未发现指定的Java进程 PID=" + requestedPid + "。");
            System.err.println("请确认PID是否正确，以及当前用户是否有权限查看该进程。");
            return null;
        }

        if (javaProcesses.size() == 1) {
            System.out.println();
            System.out.println("排除Server Doctor自身后仅发现一个Java进程，将自动选择。");
            return javaProcesses.get(0);
        }

        if (javaProcesses.size() > 1) {
            System.out.println();
            System.out.println("检测到多个Java进程，为避免误诊断，不自动执行jstack。");
            System.out.println("请使用：java -jar server-doctor-0.2.0.jar --pid <PID>");
        }

        return null;
    }

    private static void printJavaProcess(JavaProcessCollector.JavaProcessInfo process) {
        System.out.println();
        System.out.println("发现Java进程：");
        System.out.println("PID: " + process.getPid());
        System.out.printf("CPU(进程生命周期平均值): %.2f%%%n", process.getCpu());
        System.out.println("Memory: " + process.getMemory() / 1024L / 1024L + " MB");
        System.out.println("Command: " + safe(process.getCommandLine()));
    }

    private static void printJvmMetrics(JvmMetrics metrics) {
        System.out.println();
        System.out.println("正在采集JVM Heap / GC指标...");

        if (metrics == null || !metrics.isAvailable()) {
            System.out.println("JVM指标不可用：" +
                    (metrics == null ? "无数据" : metrics.getMessage()));
            return;
        }

        System.out.printf(
                "Heap: %.2f MB / %.2f MB (%.2f%%)%n",
                metrics.getHeapUsedKb() / 1024D,
                metrics.getHeapCapacityKb() / 1024D,
                metrics.getHeapUsagePercent()
        );

        System.out.println(
                "Young GC: " + metrics.getYoungGcCount() +
                        " 次，耗时 " + metrics.getYoungGcTimeSeconds() + " 秒"
        );

        System.out.println(
                "Full GC: " + metrics.getFullGcCount() +
                        " 次，耗时 " + metrics.getFullGcTimeSeconds() + " 秒"
        );
    }

    private static void printThreadSummary(ThreadAnalysis analysis) {
        System.out.println();
        System.out.println("线程分析完成：");

        ThreadAnalysis.SampleSummary last = analysis.getLastSampleSummary();
        if (last != null) {
            System.out.println("最后一次采样线程总数：" + last.getTotalThreads());
            System.out.println("RUNNABLE：" + last.getStateCount("RUNNABLE"));
            System.out.println("BLOCKED：" + last.getStateCount("BLOCKED"));
            System.out.println("WAITING：" + last.getStateCount("WAITING"));
            System.out.println("TIMED_WAITING：" + last.getStateCount("TIMED_WAITING"));
        }

        System.out.println("持续RUNNABLE热点候选：" +
                analysis.getPersistentRunnableThreads().size());

        System.out.println("死锁：" +
                (analysis.isDeadlockDetected() ? "检测到" : "未发现"));
    }

    private static int getCurrentPid() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int separator = runtimeName.indexOf('@');

        String pidText = separator > 0
                ? runtimeName.substring(0, separator)
                : runtimeName;

        try {
            return Integer.parseInt(pidText);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double toGb(long bytes) {
        return bytes / 1024D / 1024D / 1024D;
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "(unknown)" : value;
    }

    private static void printBanner() {
        System.out.println("================================");
        System.out.println("       Server Doctor V0.2");
        System.out.println("================================");
    }

    private static void printUsage() {
        System.out.println("Server Doctor V0.2");
        System.out.println();
        System.out.println("用法：");
        System.out.println("  java -jar server-doctor-0.2.0.jar --pid 1");
        System.out.println("  java -jar server-doctor-0.2.0.jar --pid 1 --output /u01/soft/logs/moc-temp");
        System.out.println("  java -jar server-doctor-0.2.0.jar --pid 1 --samples 3 --interval 5");
        System.out.println();
        System.out.println("参数：");
        System.out.println("  --pid       指定目标Java进程PID");
        System.out.println("  --output    输出根目录；也可使用环境变量SERVER_DOCTOR_OUTPUT");
        System.out.println("  --samples   jstack采样次数，默认3，范围1-10");
        System.out.println("  --interval  jstack采样间隔秒数，默认5，范围1-60");
        System.out.println("  --help      显示帮助");
        System.out.println();
        System.out.println("输出：");
        System.out.println("  每次诊断会在output目录下创建独立的server-doctor-时间-pid目录。");
    }
}
