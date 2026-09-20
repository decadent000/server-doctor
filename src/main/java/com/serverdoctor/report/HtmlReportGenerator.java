package com.serverdoctor.report;

import com.serverdoctor.model.DiagnosticResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class HtmlReportGenerator {

    public File generate(List<DiagnosticResult> results) throws IOException {
        File file = new File("server-doctor-report.html");

        FileWriter writer = new FileWriter(file);
        try {
            writer.write("<!DOCTYPE html>");
            writer.write("<html><head><meta charset='UTF-8'>");
            writer.write("<meta name='viewport' content='width=device-width, initial-scale=1'>");
            writer.write("<title>Server Doctor</title>");
            writer.write("<style>");
            writer.write("body{font-family:Arial,'Microsoft YaHei',sans-serif;margin:40px;background:#f5f5f5;color:#222;}");
            writer.write(".summary,.card{background:#fff;padding:20px;margin:15px 0;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,.08);}");
            writer.write(".HIGH{color:#c62828}.WARN{color:#ef6c00}.OK{color:#2e7d32}");
            writer.write("code{background:#f0f0f0;padding:2px 6px;border-radius:4px;}");
            writer.write("</style></head><body>");

            writer.write("<h1>Server Doctor 诊断报告</h1>");
            writer.write("<div class='summary'>");

            if (results.isEmpty()) {
                writer.write("<h2 class='OK'>未发现达到告警阈值的系统异常</h2>");
                writer.write("<p>本结论仅代表当前V0.1已实现的基础检查项。</p>");
            } else {
                writer.write("<h2>发现 " + results.size() + " 个异常项</h2>");
            }

            writer.write("</div>");

            for (DiagnosticResult result : results) {
                writer.write("<div class='card'>");
                writer.write("<h2 class='" + escape(result.getLevel()) + "'>");
                writer.write(escape(result.getLevel()) + " - " + escape(result.getTitle()));
                writer.write("</h2>");
                writer.write("<p><b>模块：</b>" + escape(result.getModule()) + "</p>");
                writer.write("<p><b>详情：</b>" + escape(result.getDetail()) + "</p>");
                writer.write("<p><b>建议：</b>" + escape(result.getSuggestion()) + "</p>");
                writer.write("</div>");
            }

            writer.write("</body></html>");
        } finally {
            writer.close();
        }

        return file;
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
