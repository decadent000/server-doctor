package com.serverdoctor.report;

import com.serverdoctor.collector.JavaProcessCollector;
import com.serverdoctor.collector.SystemCollector;
import com.serverdoctor.model.DiagnosticResult;
import com.serverdoctor.model.JvmFlags;
import com.serverdoctor.model.JvmMetrics;
import com.serverdoctor.model.ThreadAnalysis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class HtmlReportGenerator {

    public File generate(File outputDir,
                         List<DiagnosticResult> results,
                         double cpu,
                         double memory,
                         List<SystemCollector.DiskInfo> disks,
                         JavaProcessCollector.JavaProcessInfo target,
                         JvmMetrics jvmMetrics,
                         JvmFlags jvmFlags,
                         ThreadAnalysis threadAnalysis,
                         int intervalSeconds) throws IOException {

        File file = new File(outputDir, "server-doctor-report.html");
        FileWriter writer = new FileWriter(file);

        try {
            writer.write("<!DOCTYPE html>");
            writer.write("<html><head><meta charset='UTF-8'>");
            writer.write("<meta name='viewport' content='width=device-width, initial-scale=1'>");
            writer.write("<title>Server Doctor V0.2.1</title>");
            writer.write("<style>");
            writer.write("body{font-family:Arial,'Microsoft YaHei',sans-serif;margin:36px;background:#f5f5f5;color:#222;}");
            writer.write(".card{background:#fff;padding:20px;margin:16px 0;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,.08);}");
            writer.write(".HIGH{color:#c62828}.WARN{color:#ef6c00}.INFO{color:#1565c0}.OK{color:#2e7d32}");
            writer.write("table{border-collapse:collapse;width:100%;margin-top:10px;}th,td{border:1px solid #ddd;padding:8px;text-align:left;vertical-align:top;}th{background:#f3f3f3;}");
            writer.write("pre{white-space:pre-wrap;word-break:break-word;background:#f7f7f7;padding:12px;border-radius:6px;overflow:auto;}");
            writer.write(".muted{color:#666}.metric{font-size:24px;font-weight:bold;}.tag{padding:2px 6px;border-radius:4px;background:#eee;font-size:12px;}");
            writer.write("</style></head><body>");

            writer.write("<h1>Server Doctor V0.2.1 诊断报告</h1>");
            writer.write("<p class='muted'>生成时间：" +
                    escape(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())) + "</p>");

            writeSummary(writer, results, target);
            writeSystem(writer, cpu, memory, disks);
            writeJvm(writer, jvmMetrics, jvmFlags);
            writeThreadAnalysis(writer, threadAnalysis, intervalSeconds);
            writeDiagnostics(writer, results);
            writeArtifacts(writer, threadAnalysis);

            writer.write("</body></html>");
        } finally {
            writer.close();
        }

        return file;
    }

    private void writeSummary(FileWriter writer,
                              List<DiagnosticResult> results,
                              JavaProcessCollector.JavaProcessInfo target) throws IOException {

        writer.write("<div class='card'><h2>诊断概要</h2>");
        writer.write("<p>异常/关注项：<b>" + results.size() + "</b></p>");

        if (target != null) {
            writer.write("<p>目标PID：<b>" + target.getPid() + "</b></p>");
            writer.write("<p>目标命令：<code>" + escape(target.getCommandLine()) + "</code></p>");
        } else {
            writer.write("<p class='WARN'>本次未选择目标Java进程，只生成系统级检查。</p>");
        }

        writer.write("</div>");
    }

    private void writeSystem(FileWriter writer,
                             double cpu,
                             double memory,
                             List<SystemCollector.DiskInfo> disks) throws IOException {

        writer.write("<div class='card'><h2>系统资源</h2>");
        writer.write("<p>CPU：<span class='metric'>" + format(cpu) + "%</span></p>");
        writer.write("<p>Memory：<span class='metric'>" + format(memory) + "%</span></p>");
        writer.write("<table><tr><th>挂载点</th><th>使用率</th><th>可用GB</th><th>总容量GB</th></tr>");

        for (SystemCollector.DiskInfo disk : disks) {
            writer.write("<tr><td>" + escape(disk.getMount()) + "</td><td>" +
                    format(disk.getUsage()) + "%</td><td>" +
                    format(toGb(disk.getUsable())) + "</td><td>" +
                    format(toGb(disk.getTotal())) + "</td></tr>");
        }

        writer.write("</table></div>");
    }

    private void writeJvm(FileWriter writer,
                          JvmMetrics metrics,
                          JvmFlags flags) throws IOException {

        writer.write("<div class='card'><h2>JVM Heap / GC / Effective Flags</h2>");

        if (metrics == null || !metrics.isAvailable()) {
            writer.write("<p class='WARN'>未能采集jstat指标：" +
                    escape(metrics == null ? "无数据" : metrics.getMessage()) + "</p>");
        } else {
            writer.write("<table>");
            writer.write("<tr><th>指标</th><th>值</th><th>说明</th></tr>");
            writer.write("<tr><td>Heap当前使用</td><td>" +
                    format(toMb(metrics.getHeapUsedKb())) + " MB</td><td>jstat各Heap区当前已使用量合计</td></tr>");
            writer.write("<tr><td>Heap当前区容量</td><td>" +
                    format(toMb(metrics.getHeapCapacityKb())) + " MB</td><td>jstat各Heap区当前容量合计，不代表-Xmx</td></tr>");
            writer.write("<tr><td>Metaspace</td><td>" +
                    format(toMb(metrics.getMetaspaceUsedKb())) + " MB / " +
                    format(toMb(metrics.getMetaspaceCapacityKb())) +
                    " MB</td><td>Used / 当前Capacity，不把后者当作最大上限</td></tr>");
            writer.write("<tr><td>Young GC</td><td>" +
                    metrics.getYoungGcCount() + " 次 / " +
                    format(metrics.getYoungGcTimeSeconds()) + " 秒</td><td>进程启动以来累计值</td></tr>");
            writer.write("<tr><td>Full GC</td><td>" +
                    metrics.getFullGcCount() + " 次 / " +
                    format(metrics.getFullGcTimeSeconds()) + " 秒</td><td>进程启动以来累计值</td></tr>");
            writer.write("<tr><td>总GC时间</td><td>" +
                    format(metrics.getTotalGcTimeSeconds()) + " 秒</td><td>进程启动以来累计值</td></tr>");
            writer.write("</table>");
        }

        writer.write("<h3>实际生效JVM参数</h3>");

        if (flags == null || !flags.isAvailable()) {
            writer.write("<p class='WARN'>未能通过jcmd采集VM.flags：" +
                    escape(flags == null ? "无数据" : flags.getMessage()) + "</p>");
        } else {
            writer.write("<table><tr><th>指标</th><th>实际值</th></tr>");
            writer.write("<tr><td>InitialHeapSize</td><td>" +
                    format(toMbBytes(flags.getInitialHeapSizeBytes())) + " MB</td></tr>");
            writer.write("<tr><td>MaxHeapSize</td><td>" +
                    format(toMbBytes(flags.getMaxHeapSizeBytes())) + " MB</td></tr>");
            writer.write("<tr><td>GC</td><td>" +
                    escape(flags.getGarbageCollector()) + "</td></tr>");
            writer.write("<tr><td>JVM有效参数</td><td><code>" +
                    escape(flags.getEffectiveJvmArgs()) + "</code></td></tr>");
            writer.write("<tr><td>Java Command</td><td><code>" +
                    escape(flags.getJavaCommand()) + "</code></td></tr>");
            writer.write("</table>");

            if (metrics != null && metrics.isAvailable() && flags.getMaxHeapSizeBytes() > 0) {
                double actualUsage = metrics.getHeapUsedKb() * 1024D
                        / flags.getMaxHeapSizeBytes() * 100D;
                writer.write("<p>Heap当前使用 / 实际MaxHeap：<b>" +
                        format(actualUsage) + "%</b></p>");
            }
        }

        writer.write("</div>");
    }

    private void writeThreadAnalysis(FileWriter writer,
                                     ThreadAnalysis analysis,
                                     int intervalSeconds) throws IOException {

        writer.write("<div class='card'><h2>线程分析</h2>");

        if (analysis == null) {
            writer.write("<p>未进行线程分析。</p></div>");
            return;
        }

        writer.write("<p>采样次数：" + analysis.getSampleCount() +
                "，采样间隔：" + intervalSeconds + " 秒。</p>");
        writer.write("<p class='muted'>注意：JVM里的RUNNABLE包含native socket/epoll/accept等IO等待，因此不能把RUNNABLE数量直接等同于CPU繁忙线程。</p>");

        if (analysis.isDeadlockDetected()) {
            writer.write("<p class='HIGH'><b>检测到Java级死锁</b>，出现于样本：" +
                    escape(String.valueOf(analysis.getDeadlockSamples())) + "</p>");
        } else {
            writer.write("<p class='OK'>未在jstack输出中发现Java级死锁标记。</p>");
        }

        writer.write("<h3>线程状态</h3>");
        writer.write("<table><tr><th>样本</th><th>总线程</th><th>RUNNABLE</th><th>BLOCKED</th><th>WAITING</th><th>TIMED_WAITING</th><th>UNKNOWN</th></tr>");

        for (ThreadAnalysis.SampleSummary summary : analysis.getSampleSummaries()) {
            writer.write("<tr><td>#" + summary.getSampleIndex() + "</td><td>" +
                    summary.getTotalThreads() + "</td><td>" +
                    summary.getStateCount("RUNNABLE") + "</td><td>" +
                    summary.getStateCount("BLOCKED") + "</td><td>" +
                    summary.getStateCount("WAITING") + "</td><td>" +
                    summary.getStateCount("TIMED_WAITING") + "</td><td>" +
                    summary.getStateCount("UNKNOWN") + "</td></tr>");
        }

        writer.write("</table>");

        writer.write("<h3>持续RUNNABLE计算候选</h3>");
        if (analysis.getPersistentRunnableThreads().isEmpty()) {
            writer.write("<p>未发现满足当前规则的持续RUNNABLE计算候选。</p>");
        } else {
            int limit = Math.min(10, analysis.getPersistentRunnableThreads().size());

            for (int i = 0; i < limit; i++) {
                ThreadAnalysis.PersistentThread thread =
                        analysis.getPersistentRunnableThreads().get(i);

                writer.write("<h4>" + escape(thread.getName()) +
                        " <span class='muted'>nid=" + escape(thread.getNid()) + "</span></h4>");
                writer.write("<p>RUNNABLE命中：" + thread.getSampleHits() + "/" +
                        analysis.getSampleCount() + "；稳定顶部调用：" +
                        thread.getStableTopFrameHits() + "/" + analysis.getSampleCount() + "</p>");
                writer.write("<p><b>顶部调用：</b><code>" +
                        escape(thread.getTopFrame()) + "</code></p>");
                writer.write("<pre>" + escape(join(thread.getStack())) + "</pre>");
            }
        }

        writer.write("<h3>相同调用栈聚合（最后一次采样）</h3>");
        if (analysis.getStackGroups().isEmpty()) {
            writer.write("<p>没有出现次数达到2次的相同调用栈。</p>");
        } else {
            writer.write("<table><tr><th>分类</th><th>状态</th><th>线程数</th><th>顶部调用</th></tr>");
            for (ThreadAnalysis.StackGroup group : analysis.getStackGroups()) {
                writer.write("<tr><td><span class='tag'>" +
                        escape(group.getCategory()) + "</span></td><td>" +
                        escape(group.getState()) + "</td><td>" +
                        group.getCount() + "</td><td><code>" +
                        escape(group.getTopFrame()) + "</code></td></tr>");
            }
            writer.write("</table>");
        }

        writer.write("</div>");
    }

    private void writeDiagnostics(FileWriter writer,
                                  List<DiagnosticResult> results) throws IOException {

        writer.write("<div class='card'><h2>诊断结论</h2>");

        if (results.isEmpty()) {
            writer.write("<p class='OK'>当前规则未发现达到阈值的异常。</p>");
        }

        for (DiagnosticResult result : results) {
            writer.write("<div>");
            writer.write("<h3 class='" + escape(result.getLevel()) + "'>" +
                    escape(result.getLevel()) + " - " + escape(result.getTitle()) + "</h3>");
            writer.write("<p><b>模块：</b>" + escape(result.getModule()) + "</p>");
            writer.write("<p><b>详情：</b>" + escape(result.getDetail()) + "</p>");
            writer.write("<p><b>建议：</b>" + escape(result.getSuggestion()) + "</p>");
            writer.write("</div><hr>");
        }

        writer.write("</div>");
    }

    private void writeArtifacts(FileWriter writer,
                                ThreadAnalysis analysis) throws IOException {

        writer.write("<div class='card'><h2>本次生成文件</h2>");
        writer.write("<ul><li>server-doctor-report.html</li>");

        if (analysis != null) {
            writer.write("<li>jstack.txt（最后一次采样，兼容旧用法）</li>");
            for (int i = 1; i <= analysis.getSampleCount(); i++) {
                writer.write("<li>jstack-" + i + ".txt</li>");
            }
        }

        writer.write("</ul></div>");
    }

    private String join(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private double toGb(long bytes) {
        return bytes / 1024D / 1024D / 1024D;
    }

    private double toMb(double kb) {
        return kb / 1024D;
    }

    private double toMbBytes(long bytes) {
        return bytes <= 0 ? -1D : bytes / 1024D / 1024D;
    }

    private String format(double value) {
        return String.format("%.2f", value);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
