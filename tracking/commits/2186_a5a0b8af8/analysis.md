# 提交 2186：Spark: clean up obsoleted/unused writer classes (#13193)

## 提交信息

- **序号**：2186 / 4088
- **哈希**：a5a0b8af84362edd876ab88d377c6a608d634098
- **短哈希**：a5a0b8af8
- **日期**：2025-05-31 09:56:29 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Spark: clean up obsoleted/unused writer classes (#13193)
- **PR/Issue**：#13193

## 总体目的

此提交清理 Spark 模块中已过时和未使用的 Writer 类。`SparkPartitionedWriter` 和 `SparkPartitionedFanoutWriter` 这两个类曾经用于 Spark 写入数据到 Iceberg 表，但随着代码演进，这些类已经不再被使用（被其他更通用的 Writer 实现所取代）。保留这些未使用的类会增加代码维护负担，混淆代码结构，因此此提交将它们从 Spark v3.4、v3.5 和 v4.0 三个版本中全部删除。这是代码清理工作，有助于保持代码库的整洁和可维护性。

## 如何达成设计目的

- 删除 `SparkPartitionedWriter.java` 文件（Spark v3.4、v3.5、v4.0）
- 删除 `SparkPartitionedFanoutWriter.java` 文件（Spark v3.4、v3.5、v4.0）
- 共删除 6 个文件，每个文件 55 行代码

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPartitionedFanoutWriter.java` (删除, -55 lines)

**修改目的**：删除 Spark 3.4 中未使用的 Fanout Writer 类。

**工作逻辑**：该类继承 `PartitionedFanoutWriter<InternalRow>`，用于将 Spark InternalRow 写入分区文件。构造函数接收分区规范、文件格式、appender 工厂等参数，`partition` 方法将 InternalRow 转换为 PartitionKey。该类已不再被使用。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkPartitionedWriter.java` (删除, -55 lines)

**修改目的**：删除 Spark 3.4 中未使用的 Partitioned Writer 类。

**工作逻辑**：该类继承 `PartitionedWriter<InternalRow>`，功能与 FanoutWriter 类似但非 fanout 模式。同样已不再被使用。

### `spark/v3.5/` 下对应文件 (删除, -110 lines)

**修改目的**：删除 Spark 3.5 中相同的两个未使用 Writer 类。内容与 v3.4 完全相同。

### `spark/v4.0/` 下对应文件 (删除, -110 lines)

**修改目的**：删除 Spark 4.0 中相同的两个未使用 Writer 类。内容与 v3.4 完全相同。

## 总结

此提交是代码清理工作，从 Spark v3.4、v3.5 和 v4.0 三个版本中删除了已过时且不再使用的 `SparkPartitionedWriter` 和 `SparkPartitionedFanoutWriter` 类，共删除 6 个文件、330 行代码。这有助于减少代码维护负担，保持代码库整洁。
