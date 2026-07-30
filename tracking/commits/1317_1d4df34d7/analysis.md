# 提交 1317：Core: Log retry sleep time (#11413)

## 提交信息

- **序号**：1317 / 4088
- **哈希**：1d4df34d7bd06f6f69856aec15a1a94ae104c605
- **短哈希**：1d4df34d7
- **日期**：2024-10-31（Thu Oct 31 14:33:14 2024 -0700）
- **作者**：sullis <seans@grubhub.com>
- **提交说明**：Core: Log retry sleep time (#11413)
- **PR/Issue**：#11413

## 总体目的

`core/src/main/java/org/apache/iceberg/util/Tasks.java` 中的 `Task` 内部类实现了 Iceberg 通用的"指数退避重试"机制：任务失败后，按 `minSleepTimeMs * scaleFactor^(attempt-1)`（上限 `maxSleepTimeMs`）计算基础延迟 `delayMs`，再叠加一个 0~10% 范围的随机抖动 `jitter`，最终 `TimeUnit.MILLISECONDS.sleep(delayMs + jitter)` 后再重试。

重试日志原本只输出：

```
Retrying task after failure: <exception message>
```

不包含即将睡眠的时间。这给排查带来困难：

1. **无法判断退避进度**：日志看不到本次重试将等待多久，难以判断是进入了"长退避"阶段（说明重试多次仍失败）还是"短退避"阶段（首次重试）；
2. **无法核对退避算法**：当用户怀疑 `minSleepTimeMs` / `scaleFactor` / `maxSleepTimeMs` 配置不合理（如等待过长导致任务超时、或等待过短导致雪崩）时，没有日志可佐证实际计算出的 sleep 时间；
3. **无法与监控对齐**：运维侧常需要把"重试等待时间"与下游 SLA/超时配置对比，缺乏直接日志依据。

本提交在重试日志中加入 `sleepTimeMs` 字段，并把它抽为局部变量与 `sleep(...)` 调用共享，确保"日志输出的值"与"实际睡眠的值"完全一致。

## 如何达成设计目的

1. 把原 `delayMs + jitter` 表达式的结果抽到局部变量 `int sleepTimeMs = delayMs + jitter;`，使日志输出与 `sleep(...)` 调用引用同一变量，避免两者出现差异；
2. 在 `LOG.warn` 中以 `sleepTimeMs={}` 占位符输出该值，紧跟 `Retrying task after failure:` 之后、原异常 message 之前；
3. `TimeUnit.MILLISECONDS.sleep(...)` 改用 `sleepTimeMs` 单一变量。

整体改动 +4/-2 行，是纯日志可观测性增强，不改变重试/退避逻辑。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/Tasks.java`（修改，+4 -2 行）

**修改目的**：在重试日志中输出实际睡眠时间，提升可观测性；同时把 `sleep` 表达式抽为局部变量保证日志与实际睡眠值一致。

**工作逻辑**：

```java
// 旧
int delayMs =
    Math.min(minSleepTimeMs * Math.pow(scaleFactor, attempt - 1), (double) maxSleepTimeMs);
int jitter = ThreadLocalRandom.current().nextInt(Math.max(1, (int) (delayMs * 0.1)));

LOG.warn("Retrying task after failure: {}", e.getMessage(), e);

try {
  TimeUnit.MILLISECONDS.sleep(delayMs + jitter);
} catch (InterruptedException ie) {
  ...
}

// 新
int delayMs =
    Math.min(minSleepTimeMs * Math.pow(scaleFactor, attempt - 1), (double) maxSleepTimeMs);
int jitter = ThreadLocalRandom.current().nextInt(Math.max(1, (int) (delayMs * 0.1)));
int sleepTimeMs = delayMs + jitter;

LOG.warn(
    "Retrying task after failure: sleepTimeMs={} {}", sleepTimeMs, e.getMessage(), e);

try {
  TimeUnit.MILLISECONDS.sleep(sleepTimeMs);
} catch (InterruptedException ie) {
  ...
}
```

要点：
- `sleepTimeMs` 是 `delayMs`（指数退避基础延迟，cast 为 int）与 `jitter`（0~10% 随机抖动）之和；
- 日志占位符顺序：`sleepTimeMs={}` 在前，`{}`（异常 message）在后，最后附加异常对象 `e`（SLF4J 会把第三个参数当 throwable 打印 stack trace）；
- `sleep(sleepTimeMs)` 与日志引用同一变量，保证日志值与实际睡眠值一致。

## 小结

- **成效**：在 `Tasks.Task` 的重试日志中加入 `sleepTimeMs` 字段，方便排查退避进度与配置合理性；通过抽局部变量确保日志与 `sleep` 调用值一致。纯可观测性增强，不影响重试逻辑、退避算法或并发行为。
- **影响范围**：仅 `core` 模块 `Tasks.java` 一个文件、4 行改动。`Tasks` 是 Iceberg core 的通用任务执行/重试工具，被 commit、manifest 写入、metadata 刷新等多处使用，本变更影响这些路径的重试日志输出格式（新增 `sleepTimeMs=N` 字段）。
- **回迁到 1.4.x 的注意事项**：纯日志类变更，回迁安全。需注意：
  1. 1.4.x 上若有日志解析/告警规则匹配 `Retrying task after failure:` 的旧格式，回迁后规则需更新以兼容新增的 `sleepTimeMs=` 字段（推荐用 contains/正则匹配而非精确匹配）；
  2. `Tasks.java` 中可能存在多处重试代码块（本提交只改了其中一处，即 `Task` 内部类的主要重试路径）；若 1.4.x 上还有其它独立的重试循环（如其它内部类），本提交未同步覆盖，仍会输出旧格式日志；
  3. 行号在 main 分支已偏移（当前在 672 行附近，本提交时在 454 行附近），cherry-pick 时可能产生轻微 context conflict，需手工调整。
