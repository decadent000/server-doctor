package com.serverdoctor.collector;

import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JavaProcessCollector {

    private final SystemInfo systemInfo = new SystemInfo();

    public List<JavaProcessInfo> findJavaProcesses() {
        List<JavaProcessInfo> result = new ArrayList<JavaProcessInfo>();
        OperatingSystem os = systemInfo.getOperatingSystem();

        for (OSProcess process : os.getProcesses()) {
            String name = process.getName();
            String commandLine = process.getCommandLine();

            String normalizedName = name == null ? "" : name.toLowerCase(Locale.ROOT);
            String normalizedCommand = commandLine == null ? "" : commandLine.toLowerCase(Locale.ROOT);

            boolean looksLikeJava = normalizedName.contains("java")
                    || normalizedCommand.startsWith("java ")
                    || normalizedCommand.contains("/java ")
                    || normalizedCommand.contains("\\java.exe ");

            if (!looksLikeJava) {
                continue;
            }

            result.add(new JavaProcessInfo(
                    process.getProcessID(),
                    name,
                    commandLine,
                    process.getResidentSetSize(),
                    process.getProcessCpuLoadCumulative() * 100D
            ));
        }

        return result;
    }

    public static class JavaProcessInfo {

        private final int pid;
        private final String name;
        private final String commandLine;
        private final long memory;
        private final double cpu;

        public JavaProcessInfo(int pid,
                               String name,
                               String commandLine,
                               long memory,
                               double cpu) {
            this.pid = pid;
            this.name = name;
            this.commandLine = commandLine;
            this.memory = memory;
            this.cpu = cpu;
        }

        public int getPid() {
            return pid;
        }

        public String getName() {
            return name;
        }

        public String getCommandLine() {
            return commandLine;
        }

        public long getMemory() {
            return memory;
        }

        public double getCpu() {
            return cpu;
        }
    }
}
