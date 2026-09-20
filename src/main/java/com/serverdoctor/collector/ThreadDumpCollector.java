package com.serverdoctor.collector;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ThreadDumpCollector {

    private static final long JSTACK_TIMEOUT_SECONDS = 20L;

    public String collect(int pid) {
        StringBuilder result = new StringBuilder();

        try {
            String jstack = findJstackExecutable();

            if (jstack == null) {
                return "jstack执行失败：未在当前JDK中找到jstack命令。";
            }

            ProcessBuilder builder = new ProcessBuilder(jstack, String.valueOf(pid));
            builder.redirectErrorStream(true);

            Process process = builder.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            );

            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append(System.lineSeparator());
            }

            boolean finished = process.waitFor(JSTACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                result.append(System.lineSeparator())
                        .append("jstack执行超时，已终止。");
            } else if (process.exitValue() != 0) {
                result.append(System.lineSeparator())
                        .append("jstack退出码：")
                        .append(process.exitValue());
            }

            reader.close();

        } catch (Exception e) {
            result.append("jstack执行失败：").append(e.getMessage());
        }

        return result.toString();
    }

    private String findJstackExecutable() {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("windows");

        String executable = windows ? "jstack.exe" : "jstack";
        File javaHome = new File(System.getProperty("java.home"));

        File direct = new File(new File(javaHome, "bin"), executable);
        if (direct.isFile()) {
            return direct.getAbsolutePath();
        }

        // JDK 8 中 java.home 经常指向 <jdk>/jre，而 jstack 位于 <jdk>/bin。
        File parent = javaHome.getParentFile();
        if (parent != null) {
            File parentBin = new File(new File(parent, "bin"), executable);
            if (parentBin.isFile()) {
                return parentBin.getAbsolutePath();
            }
        }

        // 最后尝试系统 PATH。
        return executable;
    }
}
