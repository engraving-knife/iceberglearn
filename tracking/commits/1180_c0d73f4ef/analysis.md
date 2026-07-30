# 提交 1180：API, AWS: Retry S3InputStream reads (#10433)

## 提交信息

- **序号**：1180 / 4088
- **哈希**：c0d73f4ef5c16401bdfd62e1745faf2fbbf62177
- **短哈希**：c0d73f4ef
- **日期**：2024-09-24（Tue Sep 24 10:26:55 2024 -0600）
- **作者**：Amogh Jahagirdar <amogh@apache.org>
- **提交说明**：API, AWS: Retry S3InputStream reads (#10433)
- **PR/Issue**：#10433
- **共同作者**：Jack Ye <yzhaoqin@amazon.com>、Xiaoxuan Li <xioxuan@amazon.com>

## 总体目的

`S3InputStream` 是 `iceberg-aws` 模块中 `S3FileIO` 用来从 S3 读取文件内容的 `SeekableInputStream` 实现，所有从 S3 读取 Iceberg 数据文件/删除文件的 IO 都走这个流。在此提交之前，`S3InputStream.read()` 与 `read(byte[], int, int)` 直接调用底层 `ResponseInputStream` 的 `read`，**没有任何重试机制**。

在生产环境中，S3 读取经常会因为网络抖动、连接复用问题、TLS 握手异常等出现瞬时失败，典型异常包括：
- `java.net.SocketTimeoutException`：socket 读超时
- `java.net.SocketException`：连接被重置/中断
- `javax.net.ssl.SSLException`：SSL/TLS 层异常（如连接被对端关闭）

这些异常大多是瞬时的——重新发起一次 GET 请求（从上次未读完的位置继续 Range 读）通常就能成功。但旧实现一旦遇到这类异常就直接抛出，导致上层读取失败，整个作业可能因为一次瞬时网络抖动而中断。AWS SDK v2 的 `S3Client.getObject` 本身对这类流级读取异常不会自动重试（SDK 的重试主要针对 HTTP 请求级别的失败，流读取阶段的失败已超出其重试范围）。

本提交的目的是**在 `S3InputStream` 的 `read` 路径引入有限次自动重试**，针对上述三类可重试的 socket/SSL 异常进行最多 3 次重试，每次重试前重新打开底层流（从正确位置发起新的 GET Range 请求），从而把瞬时网络故障对用户读取的影响降到最低，提升 `S3FileIO` 的端到端稳定性。

## 如何达成设计目的

引入 [Failsafe](https://failsafe.dev/) 库（`dev.failsafe:failsafe` 3.3.2）作为重试编排工具，对每个 `read` 调用包裹一层 `RetryPolicy`：

1. **重试策略**：定义 `RetryPolicy` 只处理 `SSLException`、`SocketTimeoutException`、`SocketException` 三类异常（通过 `ImmutableList` 传入 `handle(...)`），最大重试次数 3 次（`withMaxRetries(3)`，Failsafe 语义是"最多重试 3 次"，即总尝试 4 次）。
2. **失败时重新打开流**：通过 `onFailure(...)` 回调，在每次重试失败后调用 `openStream(true)`——即以"静默关闭"模式重新打开底层 `ResponseInputStream`。重新打开会基于当前 `pos`（流逻辑位置）发起新的 `GetObjectRequest`（带 Range），从正确位置继续读，避免读到错位数据。
3. **包裹 read 调用**：`read()` 与 `read(b, off, len)` 都改为 `Failsafe.with(retryPolicy).get(() -> stream.read(...))`，让 Failsafe 在抛出可重试异常时自动重试。
4. **异常解包**：Failsafe 把重试耗尽后的异常包装成 `FailsafeException` 抛出，代码 catch 后把内部 `IOException` cause 重新抛出，保持 `InputStream.read` 的 `IOException` 契约不变。
5. **静默关闭**：`closeStream` 与 `openStream` 新增 `closeQuietly` 参数。重试场景下重新打开流时若旧流 close 抛异常，不能让这个异常打断重试流程，因此以 `closeQuietly=true` 模式关闭（记 WARN 日志后吞掉异常），保证重试能继续。

### 重新打开流的位置正确性

`S3InputStream` 维护两个位置：
- `pos`：用户视角的流位置（已读到哪）
- `next`：底层 `stream` 当前位置（底层流读到哪）

正常读取时 `pos == next`。`positionStream()` 在每次 read 前确保底层流定位到 `pos`：如果 `next > pos`（用户 seek 回退过）会重开流；如果 `next < pos`（不应该发生）会重开；只有 `next == pos` 时才复用现有流。

当 read 抛异常被 Failsafe 捕获进入 `onFailure` → `openStream(true)` 时，`openStream` 内部会 `closeStream(true)` 关掉半开的旧流，然后基于当前 `pos` 发起新 GET Range 请求，新流定位到 `pos`。注意此时 `next` 在 `openStream` 里被设回 `pos`（新流从 `pos` 开始），所以重试后下一次 `stream.read()` 是从 `pos` 读，与失败前的语义一致——不会跳过数据也不会重复读已读数据。Failsafe 重试的 lambda 是 `() -> stream.read(...)`，重试时 `stream` 已被 `onFailure` 替换为新流，所以重试读的是新流。

### 为什么用 Failsafe 而不是手写循环

Failsafe 提供声明式的重试策略（异常类型、次数、回调），代码更清晰，且未来可扩展（如加退避策略、超时、断路器）。Iceberg 已在其他地方使用类似模式，引入一个轻量库（failsafe 仅 ~100KB，无传递依赖）成本可控。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3InputStream.java`

**修改目的**：给 `read` 路径加重试，并支持静默重开流。

**改动 1：新增 import**

引入 Failsafe 相关类（`Failsafe`、`FailsafeException`、`RetryPolicy`）、socket/SSL 异常类、Guava `ImmutableList`。

**改动 2：新增 `retryPolicy` 字段**

```java
private RetryPolicy<Object> retryPolicy =
    RetryPolicy.builder()
        .handle(
            ImmutableList.of(
                SSLException.class, SocketTimeoutException.class, SocketException.class))
        .onFailure(failure -> openStream(true))
        .withMaxRetries(3)
        .build();
```

定义为实例字段（非 static final，但实际值是固定常量）。`handle` 列出三类可重试异常，`onFailure` 在每次失败后重新打开流（静默模式），`withMaxRetries(3)` 限定最多 3 次重试。

**改动 3：`read()` 方法包裹 Failsafe**

原实现直接 `return stream.read();`（先做位置记账再 read）。新实现：

```java
public int read() throws IOException {
  Preconditions.checkState(!closed, "Cannot read: already closed");
  positionStream();
  try {
    int bytesRead = Failsafe.with(retryPolicy).get(() -> stream.read());
    pos += 1;
    next += 1;
    readBytes.increment();
    readOperations.increment();
    return bytesRead;
  } catch (FailsafeException ex) {
    if (ex.getCause() instanceof IOException) {
      throw (IOException) ex.getCause();
    }
    throw ex;
  }
}
```

注意位置记账（`pos += 1`、`next += 1`、metrics）移到 `Failsafe.get(...)` 成功返回之后，这样只有真正读到字节才推进位置；重试期间不推进位置，保证重试后从同一位置继续。`FailsafeException` 解包：若 cause 是 `IOException` 就抛出 `IOException`，保持接口契约。

**改动 4：`read(byte[], int, int)` 方法包裹 Failsafe**

与 `read()` 类似，`bytesRead` 来自 `Failsafe.with(retryPolicy).get(() -> stream.read(b, off, len))`，成功后才推进 `pos`/`next` 与 metrics。注意 `bytesRead` 可能为 -1（EOF）或 0，记账逻辑保持原样（`pos += bytesRead` 对 -1 会变成 `pos -= 1`，但这与原实现行为一致，EOF 后的 read 行为本就未定义）。

**改动 5：`close()` 改为 `closeStream(false)`**

```java
public void close() throws IOException {
  super.close();
  closed = true;
  closeStream(false);  // 原来是 closeStream()
}
```

`closeStream` 签名加了 `closeQuietly` 参数，`close()` 传 `false` 表示正常关闭（保留原行为：非 `ConnectionClosedException` 的异常会抛出）。

**改动 6：`openStream()` 拆分出 `openStream(boolean closeQuietly)`**

```java
private void openStream() throws IOException {
  openStream(false);
}

private void openStream(boolean closeQuietly) throws IOException {
  // ... 构建 GetObjectRequest ...
  closeStream(closeQuietly);  // 原来是 closeStream()
  try {
    stream = s3.getObject(requestBuilder.build(), ResponseTransformer.toInputStream());
  } catch (Exception e) {
    // ...
  }
}
```

无参版本保持原行为（非静默），新 `closeQuietly` 版本供 `onFailure` 回调使用。

**改动 7：`closeStream(boolean closeQuietly)`**

```java
private void closeStream(boolean closeQuietly) throws IOException {
  if (stream != null) {
    try {
      stream.close();
    } catch (IOException e) {
      if (closeQuietly) {
        stream = null;
        LOG.warn("An error occurred while closing the stream", e);
        return;
      }
      // 原有逻辑：Apache HTTP client 的 ConnectionClosedException 是预期异常，忽略
      if (!e.getClass().getSimpleName().equals("ConnectionClosedException")) {
        throw e;
      }
    }
    stream = null;
  }
}
```

`closeQuietly=true` 时，任何 `IOException` 都被吞掉（记 WARN 日志，`stream` 置 null，return），保证重试流程不会被关闭旧流时的异常打断。`closeQuietly=false`（正常 close）保持原逻辑：只对 `ConnectionClosedException` 静默，其他异常抛出。

### `gradle/libs.versions.toml` 与 `build.gradle`

**修改目的**：引入 failsafe 依赖。

`libs.versions.toml` 新增：
```toml
failsafe = "3.3.2"
# ...
failsafe = { module = "dev.failsafe:failsafe", version.ref = "failsafe"}
```

`build.gradle` 在 `iceberg-core` 与 `iceberg-aws` 两个项目各加一行 `implementation libs.failsafe`。`iceberg-core` 加 failsafe 是因为后续可能也用到（本提交主要在 aws 模块用，但 core 也一并引入以备复用，且 core 是 aws 的上游依赖之一）。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3InputStream.java`

**修改目的**：把原有 `testRead`、`testSeek`、`testRangeRead` 三个测试方法重构为可接收外部 `S3Client` 参数的版本，供子类复用。

**工作逻辑**：
- 原来的 `public void testRead()` 拆为：保留 `@Test public void testRead()`（调用 `testRead(s3)`），新增 `protected void testRead(S3Client s3Client) throws Exception`（实际测试逻辑，用传入的 client 构造 `S3InputStream`）。
- `testRangeRead`、`testSeek` 同样拆分。
- 新增 `protected S3Client s3Client()` 返回默认的 `s3` 字段。
- 这样子类 `TestFlakyS3InputStream` 可以传入一个"会随机抛异常"的 flaky client，复用父类的全部断言逻辑来验证重试后读取结果正确。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestFlakyS3InputStream.java`（新文件）

**修改目的**：验证 `S3InputStream` 的重试行为。

**核心设计**：
- 继承 `TestS3InputStream`，复用父类的 `testRead`/`testSeek`/`testRangeRead` 断言。
- 定义 `retryableExceptions()`：返回 `SocketTimeoutException` 与 `SSLException` 两个可重试异常。
- 定义 `nonRetryableExceptions()`：返回普通 `IOException`（不在重试列表里，应直接抛出）。
- `flakyStreamClient(AtomicInteger counter, IOException failure)`：用 Mockito `spy` 包装一个 `S3ClientWrapper`，把 `getObject` 的返回包装成 `FlakyInputStream`。`FlakyInputStream` 内部用计数器：每轮 N 次调用中前 N-1 次抛指定异常，第 N 次才真正读，模拟瞬时故障。
- `S3ClientWrapper`：因为 AWS SDK 的 `DefaultS3Client` 是 final 类不能直接 mock，所以包一层 wrapper 委托给真实 client，然后 spy 这个 wrapper。

**测试方法**（均为 `@ParameterizedTest` + `@MethodSource`，对每个异常类型参数化）：
1. `testReadWithFlakyStreamRetrySucceed`：计数器设 3（前 2 次抛、第 3 次成功），断言 `testRead` 能正常完成（重试 2 次后成功，未超过 3 次上限）。
2. `testReadWithFlakyStreamExhaustedRetries`：计数器设 5（需要 4 次重试，超过 3 次上限），断言抛出原始异常类型与消息——验证重试耗尽后正确抛出。
3. `testReadWithFlakyStreamNonRetryableException`：用普通 `IOException`（不可重试），计数器设 3，断言直接抛出原始异常——验证非重试异常不触发重试。
4. `testSeekWithFlakyStreamRetrySucceed` / `testSeekWithFlakyStreamExhaustedRetries` / `testSeekWithFlakyStreamNonRetryableException`：对 `testSeek` 做同样三类验证。

`FlakyInputStream.checkCounter()` 的逻辑：`counter.decrementAndGet() == 0` 时重置计数器并放行（成功），否则抛异常。设 counter=3 意味着前 2 次 read 抛异常（counter 从 3→2→1，都非 0），第 3 次 counter 从 1→0 放行。注意每次"成功"后 counter 重置为 round，所以连续多次 read 都会经历同样的 flaky 模式——这模拟了"每个 read 都可能瞬时失败"的场景。

## 小结

- **成效**：`S3InputStream` 现在对 `SSLException`、`SocketTimeoutException`、`SocketException` 三类瞬时网络异常自动重试最多 3 次（总 4 次尝试），每次重试前从正确位置重新打开底层 S3 流，大幅降低瞬时网络抖动对 S3 读取的影响，提升 `S3FileIO` 端到端稳定性。重试耗尽后正确解包抛出原始 `IOException`，保持接口契约。
- **影响范围**：
  - 主代码：`S3InputStream.java`（read 路径加重试 + close/open 静默模式），约 71 行改动。
  - 测试：新增 `TestFlakyS3InputStream.java`（206 行），重构 `TestS3InputStream.java`（拆分方法供子类复用）。
  - 构建：`build.gradle` 与 `libs.versions.toml` 新增 `dev.failsafe:failsafe:3.3.2` 依赖到 `iceberg-core` 与 `iceberg-aws`。
  - 运行时影响：所有通过 `S3FileIO` 从 S3 读取数据的路径（数据文件、删除文件、元数据文件）都会经过新的重试逻辑。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个**有实际运行时行为变化**的功能改进，对 1.4.x 用户价值较高——S3 读取瞬时失败是生产环境常见痛点，1.4.x 用户同样会遇到。**建议回迁**。
  - 回迁注意点：
    1. **依赖**：需同时回迁 `failsafe` 依赖到 1.4.x 的 `libs.versions.toml` 与 `build.gradle`（core + aws 两个模块），确认 1.4.x 的 Gradle 配置结构与 main 兼容。
    2. **代码冲突**：1.4.x 的 `S3InputStream.java` 可能与 main 有差异（如 1.4.x 是否有其他 S3InputStream 改动），需手工合并，重点保留 `positionStream`、`openStream`、`closeStream` 的位置语义正确性。
    3. **测试基础设施**：`TestFlakyS3InputStream` 依赖 Mockito spy `S3ClientWrapper`，需确认 1.4.x 的 Mockito 版本支持（1.4.x 用 mockito 4.11.0，应支持）。
    4. **行为变化告知**：回迁后 1.4.x 用户的 S3 读取会在瞬时故障时多出最多 3 次内部重试，单次 read 的最坏延迟会增加（重试 + 重新 GET）。对于有严格超时要求的应用，需关注；但默认 3 次重试不带退避，延迟增量有限。
    5. **重试异常范围**：当前只对 SSL/socket 三类异常重试，AWS SDK 抛出的 `SdkClientException`、`AwsServiceException`（5xx）等不在重试范围——这些由 SDK 自身重试机制处理。回迁后无需调整。
    6. **日志噪音**：重试时 `closeStream(true)` 会记 WARN 日志，大量瞬时故障时日志量可能上升，需告知运维。
