# Server Doctor

面向 Java 生产环境的轻量级诊断工具。V0.2 重点增加多次线程栈分析、JVM Heap/GC 指标和可配置报告目录。

## 当前版本

**V0.2.0 / JDK 8+**

### 已支持

- 系统 CPU、内存、磁盘采集
- Java 进程发现与自身 PID 自动排除
- `--pid` 指定目标 JVM
- `--output` 指定报告输出根目录
- `SERVER_DOCTOR_OUTPUT` 环境变量配置输出目录
- 每次运行自动创建独立时间戳诊断目录
- 默认连续采集 3 次 jstack
- `--samples` 调整采样次数
- `--interval` 调整采样间隔
- RUNNABLE / BLOCKED / WAITING / TIMED_WAITING 线程统计
- 相同调用栈聚合
- 持续 RUNNABLE 热点候选识别
- Java 级死锁检测
- 通过 `jstat -gc` 采集 Heap / Metaspace / Young GC / Full GC 指标
- 增强 HTML 诊断报告

> “持续 RUNNABLE 热点候选”来自多次 jstack 的稳定状态与调用栈，不等同于真实 CPU profiling。CPU 问题仍建议结合 `top -H`、`pidstat` 或 async-profiler 进一步确认。

## 编译

```bash
mvn clean package
```

生成：

```text
target/server-doctor-0.2.0.jar
```

## 推荐运行方式

你的 moc-ohtc 容器中业务 JVM 为 PID 1，宿主机日志目录：

```text
/u01/soft/logs -> /u01/soft/logs
```

所以推荐直接：

```bash
java -Xms32m -Xmx128m \
  -jar /tmp/server-doctor.jar \
  --pid 1 \
  --output /u01/soft/logs/moc-temp
```

程序会自动创建类似：

```text
/u01/soft/logs/moc-temp/
└── server-doctor-20260920-114500-pid1/
    ├── jstack-1.txt
    ├── jstack-2.txt
    ├── jstack-3.txt
    ├── jstack.txt
    └── server-doctor-report.html
```

因为 `/u01/soft/logs` 已映射到宿主机，所以文件管理器可以直接看到这些文件，无需再执行 `docker cp`。

## 参数

```text
--pid <PID>
    指定目标Java进程。

--output <目录>
    指定输出根目录。
    未指定时依次使用：
    1. SERVER_DOCTOR_OUTPUT 环境变量
    2. 当前工作目录

--samples <1-10>
    jstack采样次数，默认3。

--interval <1-60>
    两次jstack之间的间隔秒数，默认5。

--help
    查看帮助。
```

例如：

```bash
java -jar server-doctor-0.2.0.jar \
  --pid 1 \
  --output /u01/soft/logs/moc-temp \
  --samples 3 \
  --interval 5
```

## Docker 使用

宿主机：

```bash
docker cp target/server-doctor-0.2.0.jar moc-ohtc:/tmp/server-doctor.jar
docker exec -it moc-ohtc bash
```

容器内：

```bash
java -Xms32m -Xmx128m \
  -jar /tmp/server-doctor.jar \
  --pid 1 \
  --output /u01/soft/logs/moc-temp
```

## V0.2 线程分析逻辑

默认流程：

```text
系统资源
   ↓
目标JVM
   ↓
jstat -gc
   ↓
jstack #1
   ↓ 5秒
jstack #2
   ↓ 5秒
jstack #3
   ↓
线程状态统计
   ↓
相同调用栈聚合
   ↓
持续RUNNABLE候选
   ↓
死锁检测
   ↓
HTML报告
```

持续 RUNNABLE 候选会过滤常见的空闲等待调用，例如 `Unsafe.park`、`Object.wait`、`epollWait`、socket read/accept 等，以减少明显误报。

## 输出报告包含

- 系统 CPU / Memory / Disk
- 目标 Java 进程
- Heap 使用量与使用率
- Metaspace
- Young GC 次数与累计耗时
- Full GC 次数与累计耗时
- 每次 jstack 的线程状态统计
- 持续 RUNNABLE 热点候选
- 相同调用栈聚合
- Java 级死锁结果
- CPU / 内存 / 磁盘 / Heap / 线程规则诊断

## 注意

- 运行用户必须有权限 attach 到目标 JVM。
- 容器中需要有 `jstack`；Heap/GC 采集还需要 `jstat`。
- 多次 jstack 会产生一定诊断开销，生产环境建议先在测试环境验证；高负载情况下可使用较大的 `--interval`。
- 当前 V0.2 不把“持续 RUNNABLE”直接判定为 CPU 热点，只标记为候选。
- 如果存在多个 Java 进程，建议始终显式指定 `--pid`。

## 后续方向

- Docker 容器指标
- Redis 诊断
- MySQL / Oracle 诊断
- GC 日志趋势分析
- Heap Dump 辅助分析
- 日志异常聚类
- AI 辅助根因分析
- Web / Agent 模式
