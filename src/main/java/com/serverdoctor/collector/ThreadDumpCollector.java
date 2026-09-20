package com.serverdoctor.collector;

import com.serverdoctor.util.JdkToolLocator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class ThreadDumpCollector {

    private static final long JSTACK_TIMEOUT_SECONDS = 20L;

    public String collect(int pid) {
        StringBuilder result = new StringBuilder();

        try {
            String jstack = JdkToolLocator.find("jstack");

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
}
