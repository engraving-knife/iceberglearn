# 提交 2210：Docs: Document DataFrame API support for MERGE INTO in Spark 4.0 (#13231)

## 提交信息

- **序号**：2210 / 4088
- **哈希**：3e478d2feb603e8871016b805b1e715838cad516
- **短哈希**：3e478d2fe
- **日期**：2025-06-04 23:23:37 -0700
- **作者**：Wing Yew Poon
- **提交说明**：Docs: Document DataFrame API support for MERGE INTO in Spark 4.0 (#13231)
- **PR/Issue**：#13231

## 总体目的

这个提交是一个纯文档改动，目的是在 Iceberg 的 Spark 写入文档中记录 Spark 4.0 新增的 MERGE INTO DataFrame API 支持。Spark 4.0 通过 `DataFrameWriterV2` API 增加了使用 DataFrame 进行 MERGE INTO 操作的能力，但官方文档此前并未涵盖这一新功能。开发者在使用 Iceberg 与 Spark 集成时，文档是了解可用能力的重要入口，缺失这部分说明会导致用户难以发现和使用新特性。本次文档更新补充了 API 能力概览表中的条目，并新增了完整的使用示例代码。这有助于提升 Iceberg 与 Spark 4.0 集成场景的可发现性和易用性。

## 如何达成设计目的

- 在 `spark-writes.md` 文档的能力概览表格中新增 "DataFrame merge into" 一行，标注其需要 DSv2 API 且仅 Spark 4.0 及以后版本支持。
- 在文档的"写入数据"章节中新增"Merging data"小节，介绍 Spark 4.0 引入的 `DataFrameWriterV2` MERGE INTO 用法。
- 提供一个完整的 Scala 代码示例，演示 source DataFrame 通过 `mergeInto`、`whenMatched`、`whenNotMatched`、`whenNotMatchedBySource` 等链式调用完成 update、delete、insert、update by source 等操作。

## 修改详情

### `docs/docs/spark-writes.md` (修改, +21 lines)

**修改目的**：补充 Spark 4.0 DataFrame MERGE INTO API 的文档说明和示例。

**工作逻辑**：
- 在能力概览表格中新增一行：`| [DataFrame merge into](#merging-data) | ✔️ | ⚠ Requires DSv2 API (Spark 4.0 and later) |`，与已有的 append、overwrite、CTAS/RTAS 等条目格式保持一致。
- 在 "DataFrame CTAS and RTAS" 章节后新增 "Merging data" 小节：
  - 说明 Spark 4.0 通过 `DataFrameWriterV2` API 支持 MERGE INTO 查询。
  - 描述 MERGE INTO 的基本概念：使用 source（DataFrame）更新 target 表。
  - 提供完整 Scala 代码示例，展示 `mergeInto("target", condition)` 指定目标表和 ON 条件，随后通过 `whenMatched().updateAll()`、`whenMatched().delete()`、`whenNotMatched().insertAll()`、`whenNotMatchedBySource().update(Map(...))` 等操作链式构建 MERGE 语义，最后调用 `.merge()` 执行。

## 总结

该提交是文档类改动，为 Spark 4.0 新增的 DataFrame MERGE INTO API 提供了清晰的使用说明和示例代码，提升了 Iceberg Spark 集成功能的可发现性。改动简洁、聚焦，无代码逻辑变更。
