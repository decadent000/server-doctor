package com.serverdoctor.util;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class OutputDirectoryManager {

    private OutputDirectoryManager() {
    }

    public static File createRunDirectory(String outputRoot, Integer pid) throws IOException {
        File root = new File(outputRoot).getCanonicalFile();

        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("无法创建输出根目录：" + root.getAbsolutePath());
        }
        if (!root.isDirectory()) {
            throw new IOException("输出路径不是目录：" + root.getAbsolutePath());
        }
        if (!root.canWrite()) {
            throw new IOException("输出目录没有写权限：" + root.getAbsolutePath());
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String baseName = "server-doctor-" + timestamp + (pid == null ? "" : "-pid" + pid);

        File runDir = new File(root, baseName);
        int suffix = 1;
        while (runDir.exists()) {
            runDir = new File(root, baseName + "-" + suffix++);
        }

        if (!runDir.mkdirs()) {
            throw new IOException("无法创建本次诊断目录：" + runDir.getAbsolutePath());
        }

        return runDir.getCanonicalFile();
    }
}
