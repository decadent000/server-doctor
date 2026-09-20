package com.serverdoctor.util;

import java.io.File;
import java.util.Locale;

public final class JdkToolLocator {

    private JdkToolLocator() {
    }

    public static String find(String toolName) {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("windows");

        String executable = windows ? toolName + ".exe" : toolName;
        File javaHome = new File(System.getProperty("java.home"));

        File direct = new File(new File(javaHome, "bin"), executable);
        if (direct.isFile()) {
            return direct.getAbsolutePath();
        }

        // JDK 8 中 java.home 常指向 <jdk>/jre，工具位于 <jdk>/bin。
        File parent = javaHome.getParentFile();
        if (parent != null) {
            File parentBin = new File(new File(parent, "bin"), executable);
            if (parentBin.isFile()) {
                return parentBin.getAbsolutePath();
            }
        }

        // 最后交给系统 PATH 解析。
        return executable;
    }
}
