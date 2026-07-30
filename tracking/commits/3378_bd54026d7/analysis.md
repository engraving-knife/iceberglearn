# 提交 3378：Spark: Delegate temp file deletion to JUnit in TestParquetVectorizedReads (#15557)

## 提交信息

- **序号**：3378 / 4088
- **哈希**：bd54026d732a1eac49d0a6852036d1cd113ea65d
- **短哈希**：bd54026d7
- **日期**：2026-03-13
- **作者**：Noritaka Sekiyama
- **提交说明**：Spark: Delegate temp file deletion to JUnit in TestParquetVectorizedReads (#15557)
- **PR/Issue**：#15557（原 #13506）

## 总体目的

本提交对 Spark 三个版本模块（v3.5、v4.0、v4.1）中的 `TestParquetVectorizedReads` 测试类进行清理，将手工的临时文件创建与删除模式替换为 JUnit `@TempDir` 自动管理的方式，提升测试代码的健壮性与可维护性。

原有代码在多个测试方法中使用了 `File.createTempFile("junit", null, temp.toFile())` 创建临时文件，紧接着调用 `testFile.delete()` 删除该空文件（以便 Iceberg 的 writer 能以全新路径创建文件），并附带了 `assertThat(testFile.delete()).as("Delete should succeed").isTrue()` 断言。这种"先创建再立即删除"的模式存在几个问题：一是代码冗余，每个测试方法都重复相同的创建-删除-断言三行；二是手工管理临时文件生命周期容易在测试失败时遗留文件；三是断言删除成功并非测试关注点，属于干扰性断言。改为 `temp.resolve("data.parquet").toFile()` 后，文件路径由 JUnit `@TempDir` 提供的 `temp` 目录管理，测试结束后自动清理，且无需先创建空文件再删除。

## 如何达成设计目的

整体思路是将 `File.createTempFile(...)` + `delete()` + 断言的三行模式，统一替换为 `temp.resolve("data.parquet").toFile()` 一行，让 JUnit 的 `@TempDir` 机制接管临时文件的全生命周期管理。改动机械地应用于 Spark v3.5、v4.0、v4.1 三个模块中 `TestParquetVectorizedReads` 的所有相关测试方法（每个文件 5 处），并将文件名从随机的 `junit*.tmp` 改为有意义的 `data.parquet`，便于调试时识别。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+5/-10 lines)

**修改目的**：用 JUnit `@TempDir` 替代手工临时文件管理。

**工作逻辑**：
该文件中有 5 处相同的修改模式，分布在 `writeRecords` 辅助方法及 `testReadingParquetRowGroups`、`testV2VectorizedReadsSupportedTypes`、`testUnsupportedReadsForParquetV2`、`testUUIDVectorizedReads` 等测试方法中。每处都将：
```java
File dataFile = File.createTempFile("junit", null, temp.toFile());
assertThat(dataFile.delete()).as("Delete should succeed").isTrue();
```
替换为：
```java
File dataFile = temp.resolve("data.parquet").toFile();
```
`temp` 是 JUnit `@TempDir` 注入的 `java.nio.file.Path`，`resolve("data.parquet")` 返回该目录下的子路径，`toFile()` 转为 `File` 对象。由于 Iceberg writer 会直接创建该文件，无需事先存在空文件，也无需手工删除——JUnit 在测试结束后自动清理整个 `@TempDir` 目录。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+5/-10 lines)

**修改目的**：同 v3.5，对 Spark 4.0 模块做相同清理。

**工作逻辑**：与 v3.5 完全一致的 5 处替换。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+5/-10 lines)

**修改目的**：同 v3.5，对 Spark 4.1 模块做相同清理。

**工作逻辑**：与 v3.5 完全一致的 5 处替换。

## 总结

本提交是一笔测试基础设施清理，将三个 Spark 版本模块中 `TestParquetVectorizedReads` 的手工临时文件管理统一委托给 JUnit `@TempDir`，消除了冗余的创建-删除-断言代码，降低测试维护成本并避免临时文件遗留，同时将文件名规范化为 `data.parquet` 以提升可读性。属于不影响功能逻辑的代码质量改进。
