# Server Doctor

一个面向 Java 生产环境的轻量级诊断工具，目标是自动采集服务器与 JVM 信息、识别常见风险，并生成 HTML 诊断报告。

## 当前版本

V0.1

已支持：

- CPU 使用率采集
- 内存使用率采集
- 磁盘使用率采集
- Java 进程发现
- Java 进程 CPU / 内存 / 启动命令采集
- 单 Java 进程时自动执行 jstack
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

```bash
java -jar target/server-doctor-0.1.0.jar
```

运行后会在当前目录生成：

```text
jstack.txt
server-doctor-report.html
```

其中 `jstack.txt` 只会在检测到恰好一个 Java 进程且 jstack 可执行时生成。

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

生产环境使用前建议先在测试环境验证。
