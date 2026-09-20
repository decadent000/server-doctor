# Server Doctor

面向 Java 生产环境的轻量级诊断工具。

## 当前版本

**V0.3.0 / JDK 8+**

V0.3 的目标是把“jstack里看起来可疑”升级成“真实CPU线程 + Java调用栈”，并补充 Docker/cgroup 资源边界。

## V0.3 新增

### 1. Docker / cgroup 指标

兼容 cgroup v1 / v2，尽可能采集：

- 容器 Memory usage / limit
- CPU quota
- cpuset
- CPU throttling：nr_periods / nr_throttled / throttled time
- pids.current / pids.max
- cgroup v2 memory.events：oom / oom_kill
- cgroup v1 memory.failcnt

这样可以区分：

```text
宿主机资源看起来正常
        ≠
容器没有被CPU quota / memory limit限制
```

### 2. 真实线程 CPU 与 jstack 自动关联

Linux 下通过：

```text
/proc/<pid>/task/<tid>/stat
```

短周期采样每个 native thread 的 utime + stime。

然后：

```text
Linux TID（十进制）
      ↓
转换成 nid（十六进制）
      ↓
匹配 jstack nid=0x...
      ↓
Java线程名
      ↓
Java调用栈
```

最终报告可以直接出现：

```text
CPU 86.00%
TID 558
nid 0x22e
pcb-process-14

AStarOhtc.findPath(...)
 -> getTimeOptimizedPath(...)
 -> GetQFPathPCB(...)
 -> CalcPath(...)
```

这比仅根据 RUNNABLE 判断热点可靠得多。

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

默认：

```text
jstack采样次数：3
jstack间隔：5秒
真实线程CPU采样窗口：1000ms
```

也可以：

```bash
java -Xms32m -Xmx128m \
  -jar /u01/soft/logs/moc-temp/server-doctor.jar \
  --pid 1 \
  --output /u01/soft/logs/moc-temp \
  --samples 3 \
  --interval 5 \
  --cpu-sample-ms 2000
```

## 输出

每次创建独立目录：

```text
server-doctor-时间-pid1/
├── jstack-1.txt
├── jstack-2.txt
├── jstack-3.txt
├── jstack.txt
├── thread-cpu.txt
└── server-doctor-report.html
```

## 如何理解线程CPU%

线程CPU来自两个采样点之间的CPU time增量：

```text
(utime增量 + stime增量)
-----------------------
CLK_TCK × 实际采样秒数
```

显示的 100% 大致表示该线程在一个逻辑CPU上持续运行整个采样窗口。

这和 jstack 的 RUNNABLE 不一样：

- RUNNABLE 可能实际在 socket read / epoll / accept 等native IO等待。
- thread CPU% 是实际CPU时间增量。
- V0.3 会把 thread CPU 的 Linux TID 与 jstack nid 自动关联。

## V0.2.1能力继续保留

- 实际 InitialHeapSize / MaxHeapSize
- 实际GC类型
- JVM有效参数
- 自动发现 jar 后面的疑似失效 JVM 参数
- Heap实际Max使用率
- Metaspace Used / 当前Capacity
- RUNNABLE IO等待过滤
- 相同调用栈分类
- 线程总数关注
- 死锁检测

## 当前限制

- 真实线程CPU功能依赖 Linux `/proc`。
- `getconf CLK_TCK` 不可用时暂按100回退。
- cgroup数据在不同Docker/Kubernetes和Linux发行版上可能有路径差异；当前已兼容常见cgroup v1/v2布局。
- CPU throttling / OOM事件当前主要是累计值，不代表本次1秒采样窗口内刚发生。
- Linux线程可能在CPU采样后退出，因此少数TID可能无法和最后一次jstack匹配。
- 当前没有使用async-profiler，因此这是低侵入短周期采样，不是完整CPU火焰图。

## 下一步

V0.3经过生产环境验证后，再进入 V0.4：

- jstat连续采样
- Heap / Old区趋势
- Young GC / Full GC增量
- GC耗时占比
- GC日志趋势分析
