# 提交 2417：Core: Prevent empty Puffin file creation in DV writer (#13666)

## 提交信息

- **序号**：2417 / 4088
- **哈希**：e3763f1288a371667fffe356bd782863c502289f
- **短哈希**：e3763f128
- **日期**：2025-07-25 13:08:08 -0600
- **作者**：Drew Gallardo
- **提交说明**：Core: Prevent empty Puffin file creation in DV writer (#13666)
- **PR/Issue**：#13666

## 总体目的

本提交修复了 `BaseDVFileWriter` 在没有删除数据（deletes）时仍会创建空 Puffin 文件的问题。

DV（Deletion Vector，删除向量）文件用于存储 Iceberg 表的删除信息，以 Puffin 文件格式持久化。`BaseDVFileWriter` 负责将删除数据写入 DV 文件。当调用 `close()` 方法时，此前的代码总是会创建一个 `PuffinWriter`，即使没有任何删除数据需要写入。这会导致生成空的 Puffin 文件，造成不必要的文件创建和存储浪费。

本提交在 `close()` 方法中增加了一个早期返回检查：如果 `deletesByPath` 为空（即没有删除数据），则直接构造一个空的 `DeleteWriteResult` 并返回，跳过 PuffinWriter 的创建和文件写入。

## 如何达成设计目的

在 `BaseDVFileWriter.close()` 方法中，在创建 `PuffinWriter` 之前，检查 `deletesByPath` 是否为空。如果为空，直接构造一个包含空 DV 列表、空引用数据文件集合和空重写删除文件列表的 `DeleteWriteResult`，然后提前返回，避免创建不必要的 PuffinWriter 和空 Puffin 文件。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/BaseDVFileWriter.java` (+6/-0 lines)

**修改目的**：在无删除数据时跳过 Puffin 文件创建。

**工作逻辑**：在 `close()` 方法中，在原有的 `PuffinWriter writer = newWriter()` 调用之前，新增了一个空检查。如果 `deletesByPath.isEmpty()`，则直接设置 `this.result = new DeleteWriteResult(dvs, referencedDataFiles, rewrittenDeleteFiles)`（此时 `dvs` 为空列表，`referencedDataFiles` 为空集合，`rewrittenDeleteFiles` 为空列表），然后 `return` 跳过后续的 PuffinWriter 创建和写入逻辑。

### `data/src/test/java/org/apache/iceberg/io/TestDVWriters.java` (+23/-0 lines)

**修改目的**：新增测试验证无删除数据时不创建 Puffin 文件。

**工作逻辑**：新增 `testNoPuffinFileCreatedWhenNoDeletesWritten` 测试方法：
- 创建 `BaseDVFileWriter` 实例
- 不写入任何删除数据，直接调用 `close()`
- 验证 `result` 的 `deleteFiles()` 为空
- 验证 `result` 的 `referencedDataFiles()` 为空
- 验证 `result` 的 `referencesDataFiles()` 为 false
- 验证 `result` 的 `rewrittenDeleteFiles()` 为空
- 验证表的 `data` 目录不存在，证明没有创建任何 Puffin 文件

## 总结

本提交修复了 DV writer 在无删除数据时仍创建空 Puffin 文件的问题，通过简单的早期返回检查避免了不必要的文件创建。这减少了存储系统中的垃圾文件，特别是在有大量空写入操作的场景下。
