package com.serverdoctor;

import com.serverdoctor.analyzer.DiagnosticAnalyzer;
import com.serverdoctor.collector.JavaProcessCollector;
import com.serverdoctor.collector.SystemCollector;
import com.serverdoctor.collector.ThreadDumpCollector;
import com.serverdoctor.model.DiagnosticResult;
import com.serverdoctor.report.HtmlReportGenerator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;

public class ServerDoctorApplication {

    public static void main(String[] args) throws Exception {
        if (containsHelp(args)) {
            printUsage();
            return;
        }

        Integer requestedPid;
        try {
            requestedPid = parseRequestedPid(args);
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误：" + e.getMessage());
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

        DiagnosticAnalyzer analyzer = new DiagnosticAnalyzer();
        List<DiagnosticResult> results =
                analyzer.analyzeSystem(cpu, memory, disks);

        JavaProcessCollector.JavaProcessInfo target =
                selectTargetProcess(javaProcesses, requestedPid, selfPid);

        if (target != null) {
            System.out.println();
            System.out.println("诊断目标：PID=" + target.getPid());
            collectThreadDump(target);
        }

        HtmlReportGenerator generator = new HtmlReportGenerator();
        File report = generator.generate(results);

        System.out.println();
        System.out.println("================================");
        System.out.println("诊断完成");
        System.out.println("发现系统异常：" + results.size());
        System.out.println("报告：" + report.getAbsolutePath());
        System.out.println("================================");
    }

    private static JavaProcessCollector.JavaProcessInfo selectTargetProcess(
            List<JavaProcessCollector.JavaProcessInfo> javaProcesses,
            Integer requestedPid,
            int selfPid) {

        if (requestedPid != null) {
            if (requestedPid <= 0) {
                System.err.println("PID必须大于0。");
                return null;
            }

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
            System.out.println("请使用：java -jar server-doctor-0.1.0.jar --pid <PID>");
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

    private static void collectThreadDump(JavaProcessCollector.JavaProcessInfo process)
            throws IOException {

        System.out.println();
        System.out.println("正在采集线程栈 PID=" + process.getPid());

        ThreadDumpCollector collector = new ThreadDumpCollector();
        String dump = collector.collect(process.getPid());

        File dumpFile = new File("jstack.txt");
        FileWriter writer = new FileWriter(dumpFile);

        try {
            writer.write(dump);
        } finally {
            writer.close();
        }

        System.out.println("线程栈已保存：" + dumpFile.getAbsolutePath());
    }

    private static Integer parseRequestedPid(String[] args) {
        Integer pid = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if ("--pid".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--pid 后必须跟进程号");
                }
                if (pid != null) {
                    throw new IllegalArgumentException("--pid 不能重复指定");
                }
                pid = parsePidValue(args[++i]);
            } else if (arg.startsWith("--pid=")) {
                if (pid != null) {
                    throw new IllegalArgumentException("--pid 不能重复指定");
                }
                pid = parsePidValue(arg.substring("--pid=".length()));
            } else if (!"-h".equals(arg) && !"--help".equals(arg)) {
                throw new IllegalArgumentException("未知参数：" + arg);
            }
        }

        return pid;
    }

    private static int parsePidValue(String value) {
        try {
            int pid = Integer.parseInt(value);
            if (pid <= 0) {
                throw new IllegalArgumentException("PID必须大于0");
            }
            return pid;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效PID：" + value);
        }
    }

    private static boolean containsHelp(String[] args) {
        for (String arg : args) {
            if ("-h".equals(arg) || "--help".equals(arg)) {
                return true;
            }
        }
        return false;
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
        System.out.println("       Server Doctor V0.1");
        System.out.println("================================");
    }

    private static void printUsage() {
        System.out.println("Server Doctor V0.1");
        System.out.println();
        System.out.println("用法：");
        System.out.println("  java -jar server-doctor-0.1.0.jar");
        System.out.println("  java -jar server-doctor-0.1.0.jar --pid 1");
        System.out.println("  java -jar server-doctor-0.1.0.jar --pid=1");
        System.out.println();
        System.out.println("规则：");
        System.out.println("  1. 自动排除Server Doctor自身Java进程；");
        System.out.println("  2. 未指定--pid且仅剩一个Java进程时，自动选择；");
        System.out.println("  3. 未指定--pid且存在多个Java进程时，不自动执行jstack；");
        System.out.println("  4. 多Java进程环境建议显式使用--pid指定目标。");
    }
}
