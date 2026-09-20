package com.serverdoctor.collector;

import com.serverdoctor.model.JvmFlags;
import com.serverdoctor.util.JdkToolLocator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JvmFlagsCollector {

    private static final long TIMEOUT_SECONDS = 15L;
    private static final Pattern INITIAL_HEAP =
            Pattern.compile("-XX:InitialHeapSize=([0-9]+)");
    private static final Pattern MAX_HEAP =
            Pattern.compile("-XX:MaxHeapSize=([0-9]+)");

    public JvmFlags collect(int pid) {
        try {
            String jcmd = JdkToolLocator.find("jcmd");

            CommandResult flagsResult = run(jcmd, String.valueOf(pid), "VM.flags");
            CommandResult commandLineResult = run(jcmd, String.valueOf(pid), "VM.command_line");

            if (!flagsResult.success) {
                return JvmFlags.unavailable("jcmd VM.flags执行失败：" + flagsResult.output);
            }

            long initialHeap = extractLong(INITIAL_HEAP, flagsResult.output);
            long maxHeap = extractLong(MAX_HEAP, flagsResult.output);
            String gc = detectGc(flagsResult.output);

            String effectiveJvmArgs = "";
            String javaCommand = "";

            if (commandLineResult.success) {
                String[] lines = commandLineResult.output.split("\\r?\\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("jvm_args:")) {
                        effectiveJvmArgs = trimmed.substring("jvm_args:".length()).trim();
                    } else if (trimmed.startsWith("java_command:")) {
                        javaCommand = trimmed.substring("java_command:".length()).trim();
                    }
                }
            }

            return new JvmFlags(
                    true,
                    "jcmd",
                    initialHeap,
                    maxHeap,
                    gc,
                    effectiveJvmArgs,
                    javaCommand
            );

        } catch (Exception e) {
            return JvmFlags.unavailable("JVM flags采集失败：" + e.getMessage());
        }
    }

    private CommandResult run(String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        Process process = builder.start();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
        );

        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append(System.lineSeparator());
        }

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            reader.close();
            return new CommandResult(false, "执行超时");
        }

        reader.close();
        return new CommandResult(process.exitValue() == 0, output.toString().trim());
    }

    private long extractLong(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return -1L;
        }

        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private String detectGc(String flags) {
        if (flags.contains("-XX:+UseG1GC")) {
            return "G1 GC";
        }
        if (flags.contains("-XX:+UseConcMarkSweepGC")) {
            return "CMS GC";
        }
        if (flags.contains("-XX:+UseParallelGC")) {
            return "Parallel GC";
        }
        if (flags.contains("-XX:+UseSerialGC")) {
            return "Serial GC";
        }
        return "未识别";
    }

    private static class CommandResult {

        private final boolean success;
        private final String output;

        private CommandResult(boolean success, String output) {
            this.success = success;
            this.output = output;
        }
    }
}
