# 提交 3148：Flink: Backport: Test Parquet writer default handling via core's DataTestBase (#15123) (#15125)

## 提交信息

- **序号**：3148 / 4088
- **哈希**：96a59408b271881a596f74697c05adb2dbc44094
- **短哈希**：96a59408b
- **日期**：2026-01-23 09:27:03 -0800
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Test Parquet writer default handling via core's DataTestBase (#15123) (#15125)
- **PR/Issue**：#15125（回移自主分支 #15123）

## 总体目的

本提交是序号 3146（PR #15123）的回移（backport）。3146 只在 `flink/v2.1` 模块中让 `TestFlinkParquetWriter` 接入了核心 `DataTestBase` 的默认值测试体系，而 Iceberg 同时维护多个 Flink 版本分支模块（`flink/v1.20`、`flink/v2.0`、`flink/v2.1`）。为了让默认值测试覆盖在所有受支持的 Flink 版本上保持一致，避免某个 Flink 版本的 Parquet 写入器在默认值场景下出现未被发现的回归，本提交把完全相同的测试改动应用到 `flink/v1.20` 与 `flink/v2.0` 两个模块的 `TestFlinkParquetWriter.java`。

回移的原因在于：Flink 1.20、2.0、2.1 三个版本的 `TestFlinkParquetWriter` 在改动前内容完全相同（diff 中两个文件的 `index` 均为 `d181d3351`，改动后 `index` 均为 `1eaf539df`），它们共享同一份测试实现但物理上是各自模块下的独立文件，因此必须逐模块同步修改，无法通过继承共享。由于这些 Flink 模块对应的维护分支通常不直接接收 main 分支的功能提交，需要通过专门的 backport PR（#15125）将改动回移到这些分支。

## 如何达成设计目的

将 3146 中对 `flink/v2.1/.../TestFlinkParquetWriter.java` 的修改原样应用到 `flink/v1.20/.../TestFlinkParquetWriter.java` 与 `flink/v2.0/.../TestFlinkParquetWriter.java` 两个文件。两个文件的改动内容与 3146 完全一致，涉及启用 `supportsDefaultValues()`、重构写校验方法以分离 writeSchema/expectedSchema、统一三个入口、清理导入等。

## 修改详情

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetWriter.java` (+33/-31 lines)

**修改目的**：让 Flink 1.20 模块的 Parquet 写入器测试接入核心默认值测试框架。

**工作逻辑**：与 3146 完全相同的改动——新增 `supportsDefaultValues()` 返回 `true`；将私有 `writeAndValidate(Iterable<RowData>, Schema)` 重构为 `writeAndValidate(Schema writeSchema, Schema expectedSchema, List<Record> data)`，写入用 `writeSchema`、读取与校验用 `expectedSchema`；`writeAndValidate(Schema, List<Record>)` 简化为委托 `writeAndValidate(schema, schema, data)`，移除 `RowDataSerializer.toBinaryRow` 转换 `BinaryRowData` 的逻辑；新增 `writeAndValidate(Schema writeSchema, Schema expectedSchema)` 入口；清理 `BinaryRowData`/`RowDataSerializer` 导入并新增 `RowType`。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetWriter.java` (+33/-31 lines)

**修改目的**：让 Flink 2.0 模块的 Parquet 写入器测试接入核心默认值测试框架。

**工作逻辑**：与上述 Flink 1.20 文件完全一致的改动，逐字相同。两个文件改动前后的 blob 哈希一致，证明它们本就是同一份实现的副本。

## 总结

本提交作为 3146（#15123）的 backport，将 Flink Parquet 写入器默认值测试的改动同步到 `flink/v1.20` 与 `flink/v2.0` 两个模块，使所有受支持的 Flink 版本在字段默认值场景下拥有一致的测试覆盖，补齐了之前仅 Flink 2.1 模块受益的测试盲区。
