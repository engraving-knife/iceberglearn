# 提交 2174：Docs: Fix Flink upsert doc on equality fields requirement (#13127)

## 提交信息

- **序号**：2174 / 4088
- **哈希**：6be7cd76e368330e50fe1f57d986c4c2cb0087a0
- **短哈希**：6be7cd76e
- **日期**：2025-05-28 22:03:54 +0800
- **作者**：Manu Zhang
- **提交说明**：Docs: Fix Flink upsert doc on equality fields requirement (#13127)
- **PR/Issue**：#13127

## 总体目的

此提交修复 Flink UPSERT 文档中关于 equality fields（等值字段）要求的说明，同时改进了错误提示信息。原来的文档和错误信息表述不够准确，容易让用户误解为需要将"分区字段"本身加入 equality fields，但实际上需要加入的是分区字段对应的"源列"（source column）。例如，如果分区字段是 `days(ts)`，需要加入 equality fields 的是 `ts` 而不是 `days(ts)`。此提交同时更新了文档说明和代码中的错误提示信息，使表述更加清晰准确，并增加了具体示例帮助用户理解。

## 如何达成设计目的

- 更新文档 `flink-writes.md` 中的说明，添加具体示例（如 `days(ts)` 对应 `ts`）
- 修改 FlinkSink 和 IcebergSink 中的错误提示信息，明确指出需要包含的是"源列"而非"分区字段"本身
- 在错误信息中增加源列名称的输出，帮助用户快速定位问题
- 同步更新测试中的断言信息以匹配新的错误提示
- 修改覆盖了 Flink v1.19、v1.20、v2.0 三个版本

## 修改详情

### `docs/docs/flink-writes.md` (修改, +3/-5 lines)

**修改目的**：改进 UPSERT 模式下 equality fields 要求的文档说明。

**工作逻辑**：将原来的简单说明改为更详细的描述，明确指出"分区字段对应的源列"必须包含在 equality fields 中，并添加了 `days(ts)` -> `ts` 的具体示例。同时清理了多余的空行。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java` (修改, +4/-2 lines)

**修改目的**：改进 UPSERT 模式和 hash 分布模式下的错误提示信息。

**工作逻辑**：
- 在 UPSERT 模式的校验中，错误信息从 "partition field '%s' should be included" 改为 "source column '%s' of partition field '%s', should be included"，并额外输出源列名称（通过 `table.schema().findColumnName(partitionField.sourceId())`）
- 在 hash 分布模式的校验中做同样的改进

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (修改, +5/-2 lines)

**修改目的**：改进 IcebergSink 中的错误提示信息。

**工作逻辑**：与 FlinkSink 类似的修改，在 UPSERT 和 hash 分布模式的校验中，错误信息增加源列名称的输出。注意这里 UPSERT 模式的错误信息文本也被统一为 "In 'hash' distribution mode with equality fields set" 前缀。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2Base.java` (修改, +1/-1 lines)

**修改目的**：更新测试断言以匹配新的错误信息。

**工作逻辑**：将 `hasMessageStartingWith` 的预期从 "In 'hash' distribution mode with equality fields set, partition field" 改为 "In 'hash' distribution mode with equality fields set, source column"。

### `flink/v1.20/` 下对应文件 (修改)

**修改目的**：对 Flink 1.20 版本应用相同的修改。包括 FlinkSink.java、IcebergSink.java、TestFlinkIcebergSinkV2Base.java，修改逻辑与 v1.19 完全一致。

### `flink/v2.0/` 下对应文件 (修改)

**修改目的**：对 Flink 2.0 版本应用相同的修改。包括 FlinkSink.java、IcebergSink.java、TestFlinkIcebergSinkV2Base.java，修改逻辑与 v1.19 完全一致。

## 总结

此提交修复了 Flink UPSERT 文档和错误提示中关于 equality fields 要求的不准确表述，明确指出需要包含的是分区字段对应的"源列"而非分区字段本身，并在错误信息中增加源列名称输出。修改覆盖了文档和三个 Flink 版本（v1.19/v1.20/v2.0）的代码及测试，提升了用户体验和问题排查效率。
