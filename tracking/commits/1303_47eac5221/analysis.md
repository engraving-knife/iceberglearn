# 提交 1303：Spark: Flaky test due temp directory (#10811)

## 提交信息

- **序号**：1303 / 4088
- **哈希**：47eac5221bd59ce0228f8e01825b35f8be7bf0c7
- **短哈希**：47eac5221
- **日期**：2024-10-28 20:57:39 +0100
- **作者**：Manu Zhang
- **提交说明**：Spark: Flaky test due temp directory (#10811)
- **PR/Issue**：#10811

## 总体目的

`TestDataFrameWrites` 是 Spark 模块下针对 DataFrame 写入的参数化测试。其中一个测试方法在失败写入场景下会创建临时目录（`location`），用于验证失败写入不会改变表的快照和结果。测试结束后该临时目录理应被清理，但在某些情况下（例如测试失败、并发执行、或目录下文件被异步进程占用）目录可能残留，导致后续测试运行因目录已存在而失败，表现为不稳定的 flaky test。

本提交的目的是在测试方法末尾主动、循环地删除该临时目录，并容忍删除过程中出现的 `NoSuchFileException`（文件已被并发删除的情况），从而消除因目录残留导致的测试不稳定。

## 如何达成设计目的

在测试方法最后的断言之后，追加一个 `while (location.exists())` 循环，反复尝试 `FileUtils.deleteDirectory(location)`，捕获并忽略 `NoSuchFileException`。循环设计是为了应对目录下文件可能被并发删除/重建的场景——只要目录还存在就持续尝试，直到删除成功。`NoSuchFileException` 被忽略是因为在循环过程中部分文件可能已被其他线程/进程删除，这是预期内的正常情况。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWrites.java` (+10/-0 lines)

**修改目的**：在测试方法末尾清理临时目录，消除 flaky test。

**工作逻辑**：
新增 import：
```java
import java.nio.file.NoSuchFileException;
import org.apache.commons.io.FileUtils;
```
在测试方法最后追加清理逻辑：
```java
while (location.exists()) {
  try {
    FileUtils.deleteDirectory(location);
  } catch (NoSuchFileException e) {
    // ignore NoSuchFileException when a file is already deleted
  }
}
```
`FileUtils.deleteDirectory` 来自 commons-io，会递归删除目录及其内容。`while` 循环保证即使一次删除未完全成功（例如并发场景下目录被重建）也会重试。捕获 `NoSuchFileException` 是为了在循环过程中遇到已被删除的文件时不中断清理流程。

## 总结

这是一次针对 Spark 测试 flaky 问题的修复提交。通过在测试末尾主动循环删除临时目录并容忍并发删除产生的 `NoSuchFileException`，避免了临时目录残留导致的后续测试失败。改动简单、聚焦，只影响测试代码，不影响产品逻辑，属于提升测试稳定性的合理改动。
