# Server Doctor

一个面向 Java 生产环境的轻量级诊断工具，目标是自动采集服务器与 JVM 信息、识别常见风险，并生成 HTML 诊断报告。

## 当前版本

V0.1

已支持：

- CPU 使用率采集
- 内存使用率采集
- 磁盘使用率采集
- Java 进程发现
- 自动排除 Server Doctor 自身 Java 进程
- 支持通过 `--pid` 指定需要诊断的 JVM
- Java 进程 CPU / 内存 / 启动命令采集
- 排除自身后仅有一个 Java 进程时自动执行 jstack
- 多 Java 进程时不自动误选目标
- CPU / 内存 / 磁盘基础规则诊断
- HTML 诊断报告生成

## 环境要求

- JDK 8+
- Maven 3.6+

## 编译

```bash
mvn clean package
```

生成：

```text
target/server-doctor-0.1.0.jar
```

## 运行

### 自动模式

```bash
java -jar target/server-doctor-0.1.0.jar
```

Server Doctor 会自动排除自己的 Java 进程。如果排除自身后恰好只剩一个 Java 进程，会自动选择该进程执行 jstack。

### 指定 Java 进程

推荐生产环境显式指定目标 PID：

```bash
java -jar target/server-doctor-0.1.0.jar --pid 1
```

也支持：

```bash
java -jar target/server-doctor-0.1.0.jar --pid=1
```

查看帮助：

```bash
java -jar target/server-doctor-0.1.0.jar --help
```

如果服务器或容器中存在多个 Java 进程且未指定 `--pid`，Server Doctor 只列出进程，不会自动执行 jstack，以避免误诊断。

运行后会在当前目录生成：

```text
jstack.txt
server-doctor-report.html
```

其中 `jstack.txt` 仅在成功选择目标 Java 进程并执行 jstack 后生成。

## Docker 使用示例

假设业务 Java 程序在容器内 PID 为 1：

```bash
docker cp target/server-doctor-0.1.0.jar <container-id>:/tmp/server-doctor.jar
docker exec -it <container-id> sh
cd /tmp
java -jar server-doctor.jar --pid 1
```

Server Doctor 自身启动后也会成为一个 Java 进程，但会自动识别并排除自身 PID。

## 项目结构

```text
server-doctor/
├── pom.xml
└── src/main/java/com/serverdoctor/
    ├── ServerDoctorApplication.java
    ├── analyzer/
    │   └── DiagnosticAnalyzer.java
    ├── collector/
    │   ├── JavaProcessCollector.java
    │   ├── SystemCollector.java
    │   └── ThreadDumpCollector.java
    ├── model/
    │   └── DiagnosticResult.java
    └── report/
        └── HtmlReportGenerator.java
```

## 后续计划

V0.2 计划增加：

- 连续多次 jstack 采样
- RUNNABLE / BLOCKED 线程统计
- 相同调用栈聚合
- 持续热点线程识别
- Java 死锁检测
- JVM Heap / GC 指标采集
- 更完整的 HTML 线程分析报告

后续版本计划扩展：

- Docker 诊断
- Redis 诊断
- MySQL / Oracle 诊断
- 日志分析
- AI 辅助根因分析
- 脱敏后的诊断报告导出

## 注意

jstack 必须存在于运行 Server Doctor 的 JDK 中，并且当前用户需要有权限 attach 到目标 Java 进程。

在 Docker / Kubernetes 环境中，建议直接在目标 Java 容器内运行，或者确保 Server Doctor 与目标 JVM 处于可互相访问的 PID namespace 中。

生产环境使用前建议先在测试环境验证。
