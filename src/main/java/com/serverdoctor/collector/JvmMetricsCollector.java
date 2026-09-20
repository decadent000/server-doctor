package com.serverdoctor.collector;

import com.serverdoctor.model.JvmMetrics;
import com.serverdoctor.util.JdkToolLocator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class JvmMetricsCollector {

    private static final long TIMEOUT_SECONDS = 15L;

    public JvmMetrics collect(int pid) {
        try {
            String jstat = JdkToolLocator.find("jstat");
            ProcessBuilder builder = new ProcessBuilder(jstat, "-gc", String.valueOf(pid));
            builder.redirectErrorStream(true);

            Process process = builder.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            );

            String header = null;
            String data = null;
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                if (header == null && trimmed.contains("S0C") && trimmed.contains("FGC")) {
                    header = trimmed;
                    continue;
                }

                if (header != null) {
                    data = trimmed;
                    break;
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.close();
                return JvmMetrics.unavailable("jstat执行超时");
            }

            reader.close();

            if (process.exitValue() != 0 || header == null || data == null) {
                return JvmMetrics.unavailable("无法解析jstat -gc输出，退出码=" + process.exitValue());
            }

            Map<String, Double> values = parse(header, data);

            double heapCapacity = value(values, "S0C")
                    + value(values, "S1C")
                    + value(values, "EC")
                    + value(values, "OC");

            double heapUsed = value(values, "S0U")
                    + value(values, "S1U")
                    + value(values, "EU")
                    + value(values, "OU");

            double metaspaceCapacity = value(values, "MC");
            double metaspaceUsed = value(values, "MU");

            return new JvmMetrics(
                    true,
                    "jstat -gc",
                    heapCapacity,
                    heapUsed,
                    metaspaceCapacity,
                    metaspaceUsed,
                    (long) value(values, "YGC"),
                    value(values, "YGCT"),
                    (long) value(values, "FGC"),
                    value(values, "FGCT"),
                    value(values, "GCT")
            );

        } catch (Exception e) {
            return JvmMetrics.unavailable("JVM指标采集失败：" + e.getMessage());
        }
    }

    private Map<String, Double> parse(String header, String data) {
        String[] names = header.trim().split("\\s+");
        String[] values = data.trim().split("\\s+");

        Map<String, Double> result = new HashMap<String, Double>();
        int size = Math.min(names.length, values.length);

        for (int i = 0; i < size; i++) {
            try {
                result.put(names[i], Double.parseDouble(values[i]));
            } catch (NumberFormatException ignore) {
                // 某些JDK列可能显示为“-”，跳过即可。
            }
        }

        return result;
    }

    private double value(Map<String, Double> values, String key) {
        Double value = values.get(key);
        return value == null ? 0D : value;
    }
}
