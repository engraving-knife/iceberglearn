# 提交 1248：AWS: Fix S3InputStream retry policy (#11335)

## 提交信息

- **序号**：1248 / 4088
- **哈希**：bbbfd1e2f14e62703d2dc5f1cb95631ab10f6d72
- **短哈希**：bbbfd1e2f
- **日期**：2024-10-17（Thu Oct 17 09:48:54 2024 -0400）
- **作者**：Edgar Rodriguez <edgar.rodriguez@airbnb.com>
- **提交说明**：AWS: Fix S3InputStream retry policy (#11335)
- **PR/Issue**：#11335

## 总体目的

修复 `S3InputStream` 的重试策略 bug。原实现在 Failsafe `RetryPolicy` 上把"重新打开底层 S3 对象流"的逻辑挂到了 `onFailure` 回调上，但 Failsafe 中 `onFailure` 是在**所有重试都失败后**（即最终失败时）才触发，而非每次重试之前触发。这意味着：当读取过程中遇到 `SSLException`/`SocketTimeoutException`/`SocketException` 等可重试异常时，Failsafe 会立即用同一个已经坏掉的底层流做下一次重试，并不会重新打开流，导致重试实际上必然再次失败——重试机制形同虚设。最终所有重试耗尽后才调用 `onFailure` 里的 `openStream(true)`，但此时已经没有下一次重试了，重新打开的流也用不上。

正确做法是把"重新打开流"挂到 `onRetry` 回调（每次重试之前触发），让每次重试都基于一个 freshly reopened 的 S3 对象流。本提交就是做这个修复，并补全最终失败的日志记录，便于排查。

## 如何达成设计目的

1. 把可重试异常列表抽成静态常量 `RETRYABLE_EXCEPTIONS`，便于复用与阅读。
2. 把 `onFailure(failure -> openStream(true))` 改为 `onRetry(e -> resetForRetry())`，其中 `resetForRetry()` 是新增的 `@VisibleForTesting` 方法，内部调用 `openStream(true)` 重新打开底层流。`onRetry` 在每次重试之前被调用，确保下一次读取基于新流。
3. 新增 `onFailure` 回调，但只用于记录 ERROR 日志（不再做 reopen），便于在重试耗尽后留痕。
4. 在 `onRetry` 中也记录 WARN 日志，包含 attempt count，便于观测重试行为。
5. 抽出 `resetForRetry()` 方法并标注 `@VisibleForTesting`，让测试可以覆盖并统计重试次数。
6. 在 `TestFlakyS3InputStream` 中通过子类化 `S3InputStream` 并 override `resetForRetry()`，用 `AtomicInteger` 计数，断言重试成功场景下 `resetForRetry` 被调用的次数等于重试次数；重试耗尽场景下被调用次数等于 `maxRetries`；不可重试异常场景下不被调用。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3InputStream.java`

**修改目的**：修正重试回调挂载点，使重试真正生效。

**工作逻辑**：

新增静态常量：

```java
private static final List<Class<? extends Throwable>> RETRYABLE_EXCEPTIONS =
    ImmutableList.of(SSLException.class, SocketTimeoutException.class, SocketException.class);
```

`RetryPolicy` 构造从：

```java
RetryPolicy.builder()
    .handle(ImmutableList.of(SSLException.class, SocketTimeoutException.class, SocketException.class))
    .onFailure(failure -> openStream(true))
    .withMaxRetries(3)
    .build();
```

改为：

```java
RetryPolicy.builder()
    .handle(RETRYABLE_EXCEPTIONS)
    .onRetry(e -> {
        LOG.warn("Retrying read from S3, reopening stream (attempt {})", e.getAttemptCount());
        resetForRetry();
    })
    .onFailure(e -> LOG.error(
        "Failed to read from S3 input stream after exhausting all retries", e.getException()))
    .withMaxRetries(3)
    .build();
```

新增方法：

```java
@VisibleForTesting
void resetForRetry() throws IOException {
    openStream(true);
}
```

`openStream(true)` 中 `true` 表示 force reopen（关闭当前流并重新发起 `GetObject` 请求，定位到当前 `pos`）。

关键点：Failsafe 的 `onRetry` 在每次决定重试之后、执行下一次同步调用之前触发，所以 `resetForRetry()` 会在下一次 `read`/`seek` 实际读取之前完成流的重建；而原来的 `onFailure` 是在最终失败（无下次重试）时触发，reopen 没有任何作用。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3InputStream.java`

**修改目的**：抽出 `newInputStream` 工厂方法，让子类（`TestFlakyS3InputStream`）可以替换为带计数的版本。

**工作逻辑**：

新增可被 override 的工厂方法：

```java
S3InputStream newInputStream(S3Client s3Client, S3URI uri) {
  return new S3InputStream(s3Client, uri);
}
```

把 `testRead`、`testRangeRead`、`testClose`、`testSeek` 中所有 `new S3InputStream(s3Client, uri)` / `new S3InputStream(s3, uri)` 调用替换为 `newInputStream(s3Client, uri)` / `newInputStream(s3, uri)`。这样子类只需 override `newInputStream` 返回一个包装了 `resetForRetry` 计数的子类实例，所有测试路径都自动使用包装版本。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestFlakyS3InputStream.java`

**修改目的**：验证 `resetForRetry` 在重试时被正确调用。

**工作逻辑**：

- 新增字段 `AtomicInteger resetForRetryCounter`，并在 `@BeforeEach` 中重置。
- Override `newInputStream` 返回匿名子类，该子类 override `resetForRetry()`：先 `incrementAndGet()`，再 `super.resetForRetry()`。
- 在每个重试测试中追加断言：
  - `testReadWithFlakyStreamRetrySucceed`（3 次失败后成功，maxRetries=3）：`resetForRetryCounter.get() == 2`（重试 2 次，第 3 次成功）。
  - `testReadWithFlakyStreamRetryFails`（5 次失败，maxRetries=3）：`resetForRetryCounter.get() == 3`（重试 3 次都失败，最终抛异常）。
  - `testReadWithFlakyStreamNonRetryableException`（不可重试异常）：`resetForRetryCounter.get() == 0`（不重试）。
  - `testSeekWithFlakyStreamRetrySucceed`：`== 2`。
  - `testSeekWithFlakyStreamRetryFails`：`== 3`。
  - `testSeekWithFlakyStreamNonRetryableException`：`== 0`。

## 小结

- **成效**：修复了 S3InputStream 重试机制的关键 bug——重试时底层流未被重新打开导致重试必然失败。修复后，遇到瞬时网络异常（SSL/Socket 超时等）时会重新发起 `GetObject` 请求并从断点继续读取，显著提升读路径的鲁棒性，尤其对长读取和大对象场景。
- **影响范围**：仅 `aws` 模块的 `S3InputStream` 主代码与对应测试，属于读路径行为修复。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个高价值 bug fix，**强烈建议回迁**到 1.4.x。1.4.x 若沿用旧 `onFailure` 实现，S3 读取在网络抖动下几乎一定会失败，影响所有使用 `S3FileIO` 的作业。
  - 回迁时需注意：1.4.x 的 `S3InputStream` 若与 main 有差异（例如 `openStream` 签名、`RetryPolicy` 来源），需要相应调整；本提交依赖 Failsafe 的 `onRetry`/`onFailure` 语义，1.4.x 的 Failsafe 版本需支持这两个回调（一般 2.x 即可）。
  - 测试需要 `TestFlakyS3InputStream` 与 `TestS3InputStream` 配合，回迁时建议整组回迁。
  - `@VisibleForTesting` 来自 `org.apache.iceberg.relocated.com.google.common.annotations`，1.4.x 上同样可用。
