# Server Doctor

面向 Java 生产环境的轻量级诊断工具。

## 当前版本

**V0.2.1 / JDK 8+**

本版本根据真实生产报告修正诊断准确性：

- 固定输出包名为 `target/server-doctor.jar`
- 通过 `jcmd VM.flags` 获取实际 InitialHeapSize / MaxHeapSize / GC
- 通过 `jcmd VM.command_line` 获取 JVM 真正识别的有效参数
- 自动识别 jar 后面的 `-Xms/-Xmx/-XX/-D/-agentlib/-javaagent` 等疑似失效 JVM 参数
- Heap 使用率优先按实际 MaxHeapSize 计算
- Metaspace 改为显示 Used / 当前 Capacity，不再把 Capacity 当最大值
- 过滤 `ServerSocketChannelImpl.accept0` 等常见 native IO 等待，避免误报持续热点
- 相同调用栈增加 `IO_WAIT / WAITING / COMPUTE_CANDIDATE` 分类
- 线程总数 >= 1000 时给出关注提示
- 明确说明 RUNNABLE 不等于 CPU 繁忙

## 编译

```bash
mvn clean package
```

固定生成：

```text
target/server-doctor.jar
```

## 推荐运行

```bash
java -Xms32m -Xmx128m \
  -jar /u01/soft/logs/moc-temp/server-doctor.jar \
  --pid 1 \
  --output /u01/soft/logs/moc-temp
```

默认采集 3 次 jstack，间隔 5 秒。

## 报告重点

### JVM实际生效配置

V0.2.1 会区分：

```text
命令字符串里写了什么
        ↓
JVM实际上识别了什么
        ↓
VM.flags最终实际值是什么
```

例如如果启动命令是：

```bash
java -jar app.jar -Xmx550m
```

工具会提示 `-Xmx550m` 位于 jar 后面，通常不会作为 JVM 参数生效，并展示实际 `MaxHeapSize`。

### 线程分类

jstack 中的 RUNNABLE 不等于正在消耗 CPU。

V0.2.1 会把相同栈大致分成：

- `IO_WAIT`：socket read、epoll、accept 等 native IO 等待
- `WAITING`：park、wait、sleep 等
- `COMPUTE_CANDIDATE`：未命中常见等待规则的 RUNNABLE
- `OTHER`

持续 RUNNABLE 候选也会过滤常见 IO 等待栈。

## 下一阶段建议顺序

### V0.3：Docker/cgroup + 真实线程CPU关联

优先级最高：

- 容器 CPU 使用率
- 容器 Memory usage / limit
- CPU quota / cpuset
- CPU throttling
- OOM / memory.events
- PIDs 数量与限制
- 容器与宿主机指标区分
- 对目标 JVM 线程进行短周期 CPU 采样
- 把 Linux TID 与 jstack `nid=0x...` 自动关联
- 报告真正的高CPU线程及其Java调用栈

这是当前定位 A* CPU 99% 最关键的一步。

### V0.4：GC趋势分析

- 连续采集 jstat 增量，而不是只看进程启动以来累计值
- Young GC / Full GC 每分钟增量
- GC时间占比
- Heap used变化趋势
- Old区增长趋势
- 可选读取 GC log 做停顿时间分布

### V0.5：Redis诊断

采用可选配置，不把账号密码写入报告：

- PING与连接延迟
- connected_clients / maxclients
- blocked_clients
- used_memory / maxmemory
- evicted_keys
- rejected_connections
- instantaneous_ops_per_sec
- slowlog
- Cluster状态、slot与节点健康
- 连接数异常增长提示

### 后续

1. 日志异常聚类
2. MySQL / Oracle 诊断
3. Heap Dump 辅助分析（仅手工触发）
4. AI 辅助根因分析
5. Web / Agent 模式

AI 和 Web/Agent 放后面，先保证底层采集数据可靠，否则只会把不准确的数据包装得更漂亮。
