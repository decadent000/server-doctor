package com.serverdoctor.config;

public final class RunConfig {

    private Integer pid;
    private String outputRoot;
    private int samples = 3;
    private int intervalSeconds = 5;
    private int cpuSampleMillis = 1000;
    private boolean help;

    private RunConfig() {
    }

    public static RunConfig parse(String[] args) {
        RunConfig config = new RunConfig();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if ("-h".equals(arg) || "--help".equals(arg)) {
                config.help = true;
                continue;
            }

            if ("--pid".equals(arg)) {
                i = requireNext(args, i, "--pid");
                config.pid = parsePositiveInt(args[i], "PID", 1, Integer.MAX_VALUE);
            } else if (arg.startsWith("--pid=")) {
                config.pid = parsePositiveInt(arg.substring("--pid=".length()), "PID", 1, Integer.MAX_VALUE);
            } else if ("--output".equals(arg)) {
                i = requireNext(args, i, "--output");
                config.outputRoot = requireNonBlank(args[i], "--output");
            } else if (arg.startsWith("--output=")) {
                config.outputRoot = requireNonBlank(arg.substring("--output=".length()), "--output");
            } else if ("--samples".equals(arg)) {
                i = requireNext(args, i, "--samples");
                config.samples = parsePositiveInt(args[i], "samples", 1, 10);
            } else if (arg.startsWith("--samples=")) {
                config.samples = parsePositiveInt(arg.substring("--samples=".length()), "samples", 1, 10);
            } else if ("--interval".equals(arg)) {
                i = requireNext(args, i, "--interval");
                config.intervalSeconds = parsePositiveInt(args[i], "interval", 1, 60);
            } else if (arg.startsWith("--interval=")) {
                config.intervalSeconds = parsePositiveInt(arg.substring("--interval=".length()), "interval", 1, 60);
            } else if ("--cpu-sample-ms".equals(arg)) {
                i = requireNext(args, i, "--cpu-sample-ms");
                config.cpuSampleMillis = parsePositiveInt(args[i], "cpu-sample-ms", 200, 10000);
            } else if (arg.startsWith("--cpu-sample-ms=")) {
                config.cpuSampleMillis = parsePositiveInt(
                        arg.substring("--cpu-sample-ms=".length()),
                        "cpu-sample-ms",
                        200,
                        10000
                );
            } else {
                throw new IllegalArgumentException("未知参数：" + arg);
            }
        }

        if (config.outputRoot == null) {
            String env = System.getenv("SERVER_DOCTOR_OUTPUT");
            if (env != null && !env.trim().isEmpty()) {
                config.outputRoot = env.trim();
            } else {
                config.outputRoot = System.getProperty("user.dir");
            }
        }

        return config;
    }

    private static int requireNext(String[] args, int index, String option) {
        int next = index + 1;
        if (next >= args.length) {
            throw new IllegalArgumentException(option + " 后必须跟参数值");
        }
        return next;
    }

    private static String requireNonBlank(String value, String option) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(option + " 不能为空");
        }
        return value.trim();
    }

    private static int parsePositiveInt(String value, String name, int min, int max) {
        try {
            int result = Integer.parseInt(value);
            if (result < min || result > max) {
                throw new IllegalArgumentException(name + " 必须在 " + min + " 到 " + max + " 之间");
            }
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效" + name + "：" + value);
        }
    }

    public Integer getPid() {
        return pid;
    }

    public String getOutputRoot() {
        return outputRoot;
    }

    public int getSamples() {
        return samples;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public int getCpuSampleMillis() {
        return cpuSampleMillis;
    }

    public boolean isHelp() {
        return help;
    }
}
