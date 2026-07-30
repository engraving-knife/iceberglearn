# 提交 3958：Flink: Fix monitor source rate limit for sub-second intervals (#16979)

## 提交信息

- **序号**：3958 / 4088
- **哈希**：9ca2a4b024e9cb060eab31aba62c57933bc558f4
- **短哈希**：9ca2a4b02
- **日期**：2026-06-27 21:27:48 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Fix monitor source rate limit for sub-second intervals (#16979)
- **PR/Issue**：#16979

## 总体目的

本提交修复了 Flink 维护 API 中 monitor source 的速率限制在亚秒级间隔下失效的 bug。问题根因是 `Duration.getSeconds()` 会将亚秒级的速率限制值向下取整为 0，导致计算出的速率变为无穷大（`1.0 / 0 = Infinity`），从而完全关闭了速率限制。

没有速率限制时，monitor source 会在循环中不断调用 `table.refresh()`，每个 job 占用一个完整的 CPU 核心。CI 环境以 `-DtestParallelism=auto` 运行测试（每个 fork 启动一个 MiniCluster），这些 busy-loop 的 source 耗尽了所有 CPU 核心，导致运行在 timer 上的 converter 触发太慢，最终测试因超时而失败（flaky test）。

## 如何达成设计目的

修复方案非常简洁：将速率计算从 `getSeconds()` 改为基于 `toMillis()`，避免亚秒级间隔被截断为 0。新增一个 `@VisibleForTesting` 的静态方法 `monitorRatePerSecond(long rateLimitMillis)` 封装计算逻辑，使速率 `= 1000.0 / rateLimitMillis`，这样 50ms 间隔对应 20 次/秒，100ms 对应 10 次/秒，60s 默认值仍为 1/60 次/秒。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/TableMaintenance.java` (+14/-1 lines)

**修改目的**：修复速率计算逻辑。

**工作逻辑**：
```java
// 旧代码：getSeconds() 对亚秒级返回 0，导致 perSecond(Infinity)
RateLimiterStrategy.perSecond(1.0 / rateLimit.getSeconds())

// 新代码：使用毫秒计算，避免截断
RateLimiterStrategy.perSecond(monitorRatePerSecond(rateLimit.toMillis()))
```
新增方法：
```java
@VisibleForTesting
static double monitorRatePerSecond(long rateLimitMillis) {
  return 1000.0 / rateLimitMillis;
}
```

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/api/TestTableMaintenance.java` (+8/-0 lines)

**修改目的**：验证亚秒级速率计算的正确性。

**工作逻辑**：新增 `testMonitorRatePerSecond` 测试，验证 50ms → 20.0、100ms → 10.0、60000ms → 1/60，确保不会产生无穷大速率。

## 总结

本提交修复了一个由 Java `Duration.getSeconds()` 截断行为引发的隐蔽 bug，该 bug 导致亚秒级速率限制完全失效，进而造成 CI 测试 flaky。修复方案简洁直接，通过使用毫秒精度计算速率解决了问题。
