package com.serverdoctor.collector;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.List;

public class SystemCollector {

    private final SystemInfo systemInfo = new SystemInfo();

    public double getCpuUsage() {
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        long[] oldTicks = processor.getSystemCpuLoadTicks();

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0D;
        }

        return processor.getSystemCpuLoadBetweenTicks(oldTicks) * 100D;
    }

    public double getMemoryUsage() {
        GlobalMemory memory = systemInfo.getHardware().getMemory();
        long total = memory.getTotal();
        long available = memory.getAvailable();

        if (total <= 0) {
            return 0D;
        }

        return (double) (total - available) / total * 100D;
    }

    public List<DiskInfo> getDiskUsage() {
        List<DiskInfo> result = new ArrayList<DiskInfo>();
        OperatingSystem os = systemInfo.getOperatingSystem();

        for (OSFileStore store : os.getFileSystem().getFileStores()) {
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();

            if (total <= 0) {
                continue;
            }

            double usage = (double) (total - usable) / total * 100D;
            result.add(new DiskInfo(store.getMount(), total, usable, usage));
        }

        return result;
    }

    public static class DiskInfo {

        private final String mount;
        private final long total;
        private final long usable;
        private final double usage;

        public DiskInfo(String mount, long total, long usable, double usage) {
            this.mount = mount;
            this.total = total;
            this.usable = usable;
            this.usage = usage;
        }

        public String getMount() {
            return mount;
        }

        public long getTotal() {
            return total;
        }

        public long getUsable() {
            return usable;
        }

        public double getUsage() {
            return usage;
        }
    }
}
