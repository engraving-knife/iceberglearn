# 提交 0686：AWS: Close underlying scheduler for DynamoDbLockManager

## 提交信息
- **序号**：0686 / 4088
- **哈希**：d067677df8cf86bb2161f5dea16a078e99540566
- **短哈希**：d067677df
- **日期**：2024-04-15 18:06:10 +0100
- **作者**：Filipe Regadas <oss@regadas.email>
- **提交说明**：AWS: Close underlying scheduler for DynamoDbLockManager (#10132)
- **PR/Issue**：#10132

## 总体目的

本提交修复了 `DynamoDbLockManager` 的资源泄漏问题。`DynamoDbLockManager` 继承自 `LockManagers.BaseLockManager`，而 `BaseLockManager` 内部持有一个静态共享的 `ScheduledExecutorService scheduler`（懒加载的单例调度线程池），用于驱动所有锁的心跳任务。

在原实现中，`DynamoDbLockManager.close()` 方法只关闭了 DynamoDB 客户端、取消所有心跳任务并清空集合，却没有调用 `super.close()`。这导致 `BaseLockManager` 中维护的调度器没有被正确关闭。

虽然 `BaseLockManager.scheduler()` 在创建时使用了 `MoreExecutors.getExitingScheduledExecutorService` 包装（JVM 退出时自动关闭），但在长生命周期的应用（如常驻服务、Spark/Flink 引擎）中，单个 catalog 实例关闭后，调度器线程池仍会驻留，从而造成线程和内存泄漏，并且可能在多次创建/关闭 catalog 实例的场景中累积资源占用。

该问题对于 1.4.x 这类需要长期稳定运行的发布分支尤为重要，因为生产环境下的 catalog 通常会随引擎长时间驻留，多次实例化/关闭会放大资源泄漏的影响。

## 如何达成设计目的

修复策略非常直接：在 `DynamoDbLockManager.close()` 方法的末尾调用 `super.close()`，把关闭调度器的责任委派给父类，从而保证：
1. DynamoDB 客户端（`dynamo`）被关闭；
2. 当前实例持有的所有心跳任务被取消并清理；
3. `BaseLockManager` 中的调度器被父类统一关闭/释放。

同时，方法签名从 `public void close()` 改为 `public void close() throws Exception`，以与父类 `LockManager` 接口（实现 `AutoCloseable`）的签名保持一致，避免编译告警，并允许父类关闭过程中抛出受检异常。

设计上的好处在于：将调度器的生命周期管理集中在 `BaseLockManager` 中，避免每个子类各自实现关闭逻辑，从而遵循 DRY 原则并降低未来新增锁管理器时遗漏关闭调度的风险。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/dynamodb/DynamoDbLockManager.java`

**修改目的**：在 `DynamoDbLockManager` 关闭时，调用父类 `BaseLockManager.close()` 关闭底层共享的调度线程池，避免资源泄漏。

**工作逻辑**：
- 原 `close()` 方法仅做三件事：`dynamo.close()` 关闭 DynamoDB 客户端，遍历所有 `DynamoDbHeartbeat` 调用 `cancel()` 取消心跳任务，`heartbeats.clear()` 清空本地心跳集合。
- 修改后，在上述清理步骤之后新增 `super.close()` 调用。`BaseLockManager.close()`（由 `LockManager` 接口默认实现或父类实现）会负责关闭共享调度器，使线程池不再占用 JVM 线程资源。
- 同时将签名从 `public void close()` 改为 `public void close() throws Exception`，使其与 `AutoCloseable.close()` 的契约一致，允许父类关闭过程中抛出受检异常向上传播。

修改前后对比：
```java
// 修改前
@Override
public void close() {
  dynamo.close();
  heartbeats.values().forEach(DynamoDbHeartbeat::cancel);
  heartbeats.clear();
}

// 修改后
@Override
public void close() throws Exception {
  dynamo.close();
  heartbeats.values().forEach(DynamoDbHeartbeat::cancel);
  heartbeats.clear();

  super.close();
}
```

## 小结
- **成效**：成功修复了 `DynamoDbLockManager` 未关闭底层调度器导致的资源泄漏。改动最小化（+3/-1 行），符合"最小变更、最大收益"的修复原则。
- **影响范围**：仅影响 `aws` 模块下 `DynamoDbLockManager` 的关闭路径。所有使用 DynamoDB 作为锁后端的 AWS catalog 在 `close()` 时会正确释放调度线程池。
- **回迁到 1.4.x 的注意事项**：
  1. 需要确认 1.4.x 分支的 `BaseLockManager` 是否已经存在 `close()` 默认实现（即 `LockManager` 接口的默认 `close` 方法），否则 `super.close()` 调用可能没有实际效果，需要配套检查父类。
  2. 调用方（如 catalog 的 `close()` 链路）需要确认是否处理 `throws Exception`，避免签名变更引发编译错误；若调用方使用 try-with-resources，签名变更不影响。
  3. 由于 `scheduler` 是静态共享的，关闭后若同一 JVM 中其他 `BaseLockManager` 实例仍在使用调度器，可能会引发问题——回迁时需要核实 `BaseLockManager.scheduler` 的关闭语义是否与上游一致（如是否在 `close()` 中重置为 null 供下次重新创建）。
