# 提交 2808：Core, Spark 4.0, 3.5, 3.4: Remove usage of deprecated avro/DataReader class (#14387)

## 提交信息

- **序号**：2808 / 4088
- **哈希**：8685985a039de97ff1ac834be2095e8c08be2482
- **短哈希**：8685985a0
- **日期**：2025-10-31 08:39:56 +0100
- **作者**：gaborkaszab
- **提交说明**：Core, Spark 4.0, 3.5, 3.4: Remove usage of deprecated avro/DataReader class (#14387)
- **PR/Issue**：#14387

## 总体目的

本提交移除 Spark 模块中对已废弃的 `DataReader` 类的使用，改用其替代类 `PlannedDataReader`，并将 `DataReader` 的计划移除版本从 2.0.0 提前到 1.12.0。

`DataReader` 是 Iceberg 的 Avro 数据读取器，之前已被标记为 `@Deprecated`，推荐使用 `PlannedDataReader` 替代。`PlannedDataReader` 相比 `DataReader` 支持更好的读取计划（planning）能力。Spark 的 `RewriteTablePathSparkAction`（表路径重写动作）在读取 Avro 格式的位置删除文件时仍使用 `DataReader`，本提交将其替换为 `PlannedDataReader`。

同时，`DataReader` 的废弃版本从"will be removed in 2.0.0"提前到"will be removed in 1.12.0"，表明项目决定更积极地移除该废弃类。此修改覆盖 Spark 3.4、3.5 和 4.0 三个版本。

## 如何达成设计目的

1. 在三个 Spark 版本模块的 `RewriteTablePathSparkAction` 中，将 `DataReader::create` 替换为 `PlannedDataReader.create(deleteSchema)` 的 lambda 表达式。
2. 修改 `DataReader` 的 Javadoc，将移除版本从 2.0.0 改为 1.12.0。
3. 扩展测试：将原来只测试 parquet 格式位置删除的测试拆分为 parquet、avro、orc 三种格式的独立测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/data/avro/DataReader.java` (+1/-1 lines)

**修改目的**：将废弃版本从 2.0.0 提前到 1.12.0。

**工作逻辑**：修改 Javadoc 中的 `@deprecated will be removed in 2.0.0` 为 `@deprecated will be removed in 1.12.0`，表明 `DataReader` 将在 1.12.0 版本中被移除。

### `spark/v4.0/spark/src/main/java/.../RewriteTablePathSparkAction.java` (+2/-2 lines)

**修改目的**：将 Avro 位置删除文件读取从 DataReader 替换为 PlannedDataReader。

**工作逻辑**：将 import 从 `DataReader` 改为 `PlannedDataReader`。在读取 Avro 格式位置删除文件的代码中，将 `.createReaderFunc(DataReader::create)` 改为 `.createReaderFunc(fileSchema -> PlannedDataReader.create(deleteSchema))`。注意 `PlannedDataReader.create` 接受 deleteSchema 参数（投影 schema），而非文件 schema。

### `spark/v3.5/spark/src/main/java/.../RewriteTablePathSparkAction.java` (+2/-2 lines)

**修改目的**：同上，Spark 3.5 版本的相同修改。

### `spark/v3.4/spark/src/main/java/.../RewriteTablePathSparkAction.java` (+2/-2 lines)

**修改目的**：同上，Spark 3.4 版本的相同修改。

### `spark/v4.0/spark/src/test/java/.../TestRewriteTablePathsAction.java` (+47/-16 lines)

**修改目的**：扩展位置删除测试覆盖多种文件格式。

**工作逻辑**：将原来的 `testPositionDeletes` 测试拆分为 `testPositionDeletesParquet`、`testPositionDeletesAvro`、`testPositionDeletesOrc` 三个测试，通过 `runPositionDeletesTest(String fileFormat)` 共享测试逻辑。每个测试创建使用指定格式的表（通过 `TableProperties.DELETE_DEFAULT_FILE_FORMAT` 设置），写入位置删除文件，执行路径重写，验证结果。这确保 `PlannedDataReader` 在 Avro 格式下被正确测试。

### `spark/v3.5/spark/src/test/java/.../TestRewriteTablePathsAction.java` (+47/-16 lines)

**修改目的**：同上，Spark 3.5 版本的相同测试修改。

### `spark/v3.4/spark/src/test/java/.../TestRewriteTablePathsAction.java` (+47/-16 lines)

**修改目的**：同上，Spark 3.4 版本的相同测试修改。

## 总结

本提交移除了 Spark 3.4/3.5/4.0 三个版本模块中对废弃 `DataReader` 类的使用，替换为 `PlannedDataReader`，并将 `DataReader` 的计划移除版本从 2.0.0 提前到 1.12.0。同时将位置删除测试扩展为覆盖 parquet、avro、orc 三种格式，确保 `PlannedDataReader` 在 Avro 格式下的正确性得到验证。
