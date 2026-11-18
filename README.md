# Server Doctor

Server Doctor 用于检查 Java 进程的堆内存、GC、线程栈和线程 CPU，并将结果保存为 HTML 报告。当前版本为 0.3.0，使用 JDK 8 及以上版本运行。

## 功能

- 采集系统 CPU、内存和磁盘使用情况。
- 查看 JVM 实际生效的堆大小、GC 类型和启动参数，检查放在 `-jar` 后面的疑似无效 JVM 参数。
- 读取 Heap、Metaspace 使用情况及 GC 累计次数、耗时。
- 多次采集线程栈，检查死锁、线程状态、重复调用栈和持续 RUNNABLE 的计算候选。
- 在 Linux 上采样线程 CPU 时间，将线程 TID 与 jstack 中的 `nid` 关联。
- 读取运行环境的 cgroup v1/v2 资源信息，包括内存限制、CPU quota/cpuset、限流计数、PID 配额及 OOM 事件。

## 构建与运行

构建需要 JDK 和 Maven：

```bash
mvn clean package
```

生成文件为 `target/server-doctor.jar`。运行时需要能使用 `jstat`、`jcmd` 和 `jstack`，并有权限访问目标 Java 进程。

```bash
java -Xms32m -Xmx128m -jar target/server-doctor.jar \
  --pid 1234 \
  --output ./doctor-reports
```

将 `1234` 换成目标进程 PID。使用以下命令查看参数：

```bash
java -jar target/server-doctor.jar --help
```

| 参数 | 说明 | 默认值 |
| --- | --- | --- |
| `--pid` | 目标 Java 进程 PID | 仅发现一个候选进程时自动选择，否则需指定 |
| `--output` | 报告输出根目录 | `SERVER_DOCTOR_OUTPUT` 环境变量，未设置时为当前目录 |
| `--samples` | 线程栈采样次数，范围 1–10 | 3 |
| `--interval` | 线程栈采样间隔，单位秒，范围 1–60 | 5 |
| `--cpu-sample-ms` | Linux 线程 CPU 采样窗口，单位毫秒，范围 200–10000 | 1000 |

例如，将 CPU 采样窗口改为两秒：

```bash
java -jar target/server-doctor.jar --pid 1234 \
  --output ./doctor-reports --samples 3 --interval 5 --cpu-sample-ms 2000
```

## 输出文件

每次运行会在输出根目录下创建一个独立目录。默认采样三次时，文件结构如下：

```text
server-doctor-时间-pid1234/
├── jstack-1.txt
├── jstack-2.txt
├── jstack-3.txt
├── jstack.txt
├── thread-cpu.txt
└── server-doctor-report.html
```

`jstack.txt` 保存最后一次线程栈，供旧的使用方式兼容。打开 `server-doctor-report.html` 查看汇总，排查时可结合原始采样文件。

## 如何看待结果

线程 CPU 使用率根据两次采样之间的 `utime + stime` 增量计算，100% 大致表示一个线程在整个采样窗口内占满一个逻辑 CPU。`RUNNABLE` 并不等于高 CPU，socket read、epoll、accept 等 native 等待也可能显示为该状态。

使用时还需注意：

- 线程 CPU 采样依赖 Linux `/proc`；`getconf CLK_TCK` 不可用时按 100 回退。
- cgroup 路径随运行环境而异，采集范围取决于当前进程能访问的挂载和资源文件。
- CPU 限流和 OOM 事件主要是累计值，不代表刚在本次采样窗口中发生。
- 线程可能在采样后退出，部分 TID 因而无法匹配到 Java 线程栈。
- 这是短周期采样工具，不提供 async-profiler 火焰图；报告中的候选和提示需要结合业务现场确认。

后续计划补充 GC 连续采样、Heap/Old 趋势、GC 区间增量与耗时占比，以及 GC 日志分析；这些尚不属于当前仓库版本的功能。
