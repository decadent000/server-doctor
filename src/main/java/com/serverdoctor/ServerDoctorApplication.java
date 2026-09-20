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
import java.util.List;

public class ServerDoctorApplication {

    public static void main(String[] args) throws Exception {
        printBanner();

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
                processCollector.findJavaProcesses();

        if (javaProcesses.isEmpty()) {
            System.out.println("未发现Java进程。");
        }

        for (JavaProcessCollector.JavaProcessInfo process : javaProcesses) {
            System.out.println();
            System.out.println("发现Java进程：");
            System.out.println("PID: " + process.getPid());
            System.out.printf("CPU(进程生命周期平均值): %.2f%%%n", process.getCpu());
            System.out.println("Memory: " + process.getMemory() / 1024L / 1024L + " MB");
            System.out.println("Command: " + safe(process.getCommandLine()));
        }

        DiagnosticAnalyzer analyzer = new DiagnosticAnalyzer();
        List<DiagnosticResult> results =
                analyzer.analyzeSystem(cpu, memory, disks);

        if (javaProcesses.size() == 1) {
            collectThreadDump(javaProcesses.get(0));
        } else if (javaProcesses.size() > 1) {
            System.out.println();
            System.out.println("检测到多个Java进程，V0.1不会自动选择目标进程执行jstack。");
        }

        HtmlReportGenerator generator = new HtmlReportGenerator();
        File report = generator.generate(results);

        System.out.println();
        System.out.println("================================");
        System.out.println("诊断完成");
        System.out.println("发现异常：" + results.size());
        System.out.println("报告：" + report.getAbsolutePath());
        System.out.println("================================");
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
}
