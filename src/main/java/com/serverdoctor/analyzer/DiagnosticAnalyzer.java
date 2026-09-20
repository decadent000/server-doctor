package com.serverdoctor.analyzer;

import com.serverdoctor.collector.SystemCollector;
import com.serverdoctor.model.DiagnosticResult;

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
