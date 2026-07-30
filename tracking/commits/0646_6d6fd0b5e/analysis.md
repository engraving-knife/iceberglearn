# 提交 0646：修复 s3a 文件上传被中断导致表元数据指向不存在文件的问题

## 提交信息

- **序号**：0646 / 4088
- **哈希**：6d6fd0b5e967327967142758c999483ae50f95c5
- **短哈希**：6d6fd0b5e
- **日期**：2024-03-29
- **作者**：Abid Mohammed <mohamabid+github@gmail.com>
- **提交说明**：[core] fix #9997 - Handle s3a file upload interrupt which results in table metadata pointing to files that doesn't exist (#9998)
- **PR/Issue**：PR #9998 / Issue #9997

## 总体目的

本提交修复 Issue #9997 报告的一个严重数据完整性问题：当使用 Hadoop S3A 文件系统向 Iceberg 表写入数据时，如果 `S3ABlockOutputStream` 的 `close()` 过程被线程中断（例如上层任务取消、超时打断等），S3A 的 `putObject()` 调用并不会抛出异常，导致 Iceberg 误以为上传成功并提交了表元数据，而实际上对象并未上传到 S3。这会造成表元数据指向并不存在的数据文件，后续读取时报错或数据丢失。

核心目标是：在 Iceberg 包装层（`HadoopStreams`）可靠地检测这种"静默失败"场景，把中断信号转换为 `IOException` 抛出，从而让 Iceberg 的事务提交流程感知到写入失败，避免提交脏的元数据。

## 如何达成设计目的

设计思路是利用 Java 线程的中断状态标志（interrupt flag）作为"上传可能被中断"的信号源：

1. S3A 的 `S3ABlockOutputStream#close()` 内部会调用 `putObject()` 完成最终的多部分上传提交。当线程在此时被中断时，`putObject()` 会吞掉 `InterruptedException` 并重新设置中断标志，但不会抛出异常。这是 Hadoop S3A 实现的既有行为。
2. Iceberg 的 `HadoopStreams.HadoopPositionOutputStream#close()` 原本只是简单调用 `stream.close()` 并标记 `closed = true`，不会感知中断状态。
3. 修复方案在 `stream.close()` 返回后，主动检查当前线程的中断标志（`Thread.interrupted()`），并判断底层流是否为 `S3ABlockOutputStream` 类型。如果两者都成立，则认为 S3A 上传可能因中断而失败，主动抛出 `IOException`。
4. 由于 `Thread.interrupted()` 会清除中断标志，这里使用它而不是 `isInterrupted()`，目的是在抛出异常前消费掉中断状态，避免后续逻辑误用。
5. 通过类名字符串比较（`getClass().getName()`）而非直接 `instanceof` 来判断 S3A 类型，是为了避免在 `core` 模块中引入对 Hadoop S3A 包的硬依赖（S3A 在 hadoop-aws 模块中，并非 core 的编译期依赖）。这种反射式判断保证了只在运行时才需要 S3A 类存在。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopStreams.java`

**修改目的**：在 `HadoopPositionOutputStream#close()` 中增加 S3A 上传中断的检测逻辑，将静默失败转换为显式异常。

**工作逻辑**：在原本的 `stream.close()` 与 `this.closed = true` 两行之间插入检测代码块：

```java
if (Thread.interrupted()
    && "org.apache.hadoop.fs.s3a.S3ABlockOutputStream"
        .equals(stream.getWrappedStream().getClass().getName())) {
  throw new IOException(
      "S3ABlockOutputStream failed to upload object after stream was closed");
}
```

- `stream.getWrappedStream()` 取出 `FSDataOutputStream` 包裹的底层流对象（即 S3A 的 `S3ABlockOutputStream`）。
- `Thread.interrupted()` 静态方法会检查并清除当前线程的中断标志。如果为 `true`，说明在 `stream.close()` 调用过程中线程被中断过。
- 仅当底层流是 S3A BlockOutputStream 时才触发，避免对其他文件系统（如 HDFS、本地文件系统）产生误判。
- 抛出的 `IOException` 会被上层 Iceberg 提交逻辑捕获，导致本次 commit 失败，从而阻止错误的元数据被提交。

### `core/src/test/java/org/apache/hadoop/fs/s3a/S3ABlockOutputStream.java`（新增）

**修改目的**：提供一个测试用的 S3ABlockOutputStream mock 类，模拟真实 S3A 流在 `close()` 时被中断的行为。

**工作逻辑**：模拟类位于 `org.apache.hadoop.fs.s3a` 包下（与真实类同名同包），便于通过类名检测逻辑。它内部使用单线程 `ExecutorService` 模拟 `close()` 时的上传动作：

- `close()` 提交一个 sleep 30 秒的任务并 `get()` 等待其完成，模拟一次长时间上传。
- 当外部调用 `interruptClose()` 时，会 `cancel(true)` 这个 future，触发 `InterruptedException`，被捕获后重新设置中断标志（`Thread.currentThread().interrupt()`），与真实 S3A 行为一致——不抛出异常，只保留中断状态。
- 这正是触发 Iceberg 检测逻辑的关键。

### `core/src/test/java/org/apache/iceberg/hadoop/TestHadoopStreams.java`（新增）

**修改目的**：编写单元测试验证 `HadoopStreams.wrap(...).close()` 在 S3A 流被中断时会抛出预期的 `IOException`。

**工作逻辑**：

- 创建 mock 的 `S3ABlockOutputStream`，用 `FSDataOutputStream` 包裹，再用 `HadoopStreams.wrap()` 包装成 Iceberg 的 `PositionOutputStream`。
- 启动一个独立线程，sleep 1 秒后调用 `s3ABlockOutputStream.interruptClose()` 触发中断。
- 主线程调用 `wrap.close()`，断言抛出 `IOException` 且消息为 `"S3ABlockOutputStream failed to upload object after stream was closed"`。
- 该测试覆盖了修复的核心场景：close 期间发生中断 -> 检测到中断 -> 抛出异常。

## 小结

- **成效**：本修复堵住了 S3A 写入路径上的一个静默失败漏洞，避免 Iceberg 表元数据提交后指向不存在的数据文件，对保障数据完整性具有重要意义。
- **影响范围**：仅影响使用 Hadoop S3A 文件系统（`s3a://` scheme）通过 `HadoopStreams` 写入的场景；对 HDFS、本地、阿里云 OSS 等其他文件系统无影响。
- **回迁到 1.4.x 的注意事项**：
  - 该修复是纯增量逻辑，不改变既有 API 行为，回迁风险低。
  - 回迁时需同时引入两个测试文件（mock 类与测试类），并确认 `core` 模块的测试 classpath 中允许放置 `org.apache.hadoop.fs.s3a` 包下的测试类（包名访问权限可能需要 `--add-opens` 或模块配置）。
  - 类名字符串硬编码 `"org.apache.hadoop.fs.s3a.S3ABlockOutputStream"` 在未来 Hadoop 版本若重命名该类时需要更新，但当前稳定。
  - 注意 `Thread.interrupted()` 会清除中断标志，若上层依赖中断状态传播需评估影响；但在此场景下抛出 `IOException` 已足够表达失败语义。
