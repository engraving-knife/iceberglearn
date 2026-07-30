# 提交 1430：Docs: Use DataFrameWriterV2 in example (#11647)

## 提交信息

- **序号**：1430 / 4088
- **哈希**：cdd944ebbd42cd94103f15c8baad8daa82995846
- **短哈希**：cdd944ebb
- **日期**：2024-11-25（Mon Nov 25 20:45:27 2024 +0800）
- **作者**：Cheng Pan <chengpan@apache.org>
- **提交说明**：Docs: Use DataFrameWriterV2 in example (#11647)
- **PR/Issue**：#11647

## 总体目的

Iceberg 的 Spark 配置文档 `docs/docs/spark-configuration.md` 中"Write options"章节原本用 `DataFrameWriter`（V1）的 API 作为示例：

```scala
df.write
    .option("write-format", "avro")
    .option("snapshot-property.key", "value")
    .insertInto("catalog.db.table")
```

但 Iceberg 推荐使用 `DataFrameWriterV2`（即 `df.writeTo(...)`）作为更现代的写入 API，因为 V2 API 是 Iceberg 表catalog 集成的主路径，能更好地与 Iceberg 的分支、分区、overwrite 语义配合。文档示例却仍用 V1 API，会误导用户。

本提交把示例改为 `DataFrameWriterV2` 风格：

```scala
df.writeTo("catalog.db.table")
    .option("write-format", "avro")
    .option("snapshot-property.key", "value")
    .append()
```

同时把章节说明文字中的 `DataFrameWriter` 改为 `DataFrameWriterV2`。

## 如何达成设计目的

直接修改 `docs/docs/spark-configuration.md` 中"Write options"章节的说明文字和代码示例，把 `df.write` + `.insertInto(...)` 替换为 `df.writeTo(...)` + `.append()`。无代码逻辑改动。

## 修改详情

### `docs/docs/spark-configuration.md`

**修改目的**：把 Write options 示例从 DataFrameWriter V1 改为 V2。

**工作逻辑**：
- 说明文字：`Spark write options are passed when configuring the DataFrameWriter, like this:` → `... DataFrameWriterV2, like this:`
- 代码示例：
  ```scala
  // write with Avro instead of Parquet
  df.writeTo("catalog.db.table")
      .option("write-format", "avro")
      .option("snapshot-property.key", "value")
      .append()
  ```
  原 `df.write` + `.insertInto("catalog.db.table")` 被替换为 `df.writeTo("catalog.db.table")` + `.append()`。`writeTo` 直接接收完整表名，`.append()` 对应追加写入语义。

## 小结

- **成效**：文档示例与 Iceberg 推荐的 DataFrameWriterV2 API 保持一致，避免误导用户使用 V1 API。
- **影响范围**：仅 `docs/docs/spark-configuration.md` 一处文字 + 代码示例，3 行变更，无源码或测试改动。
- **回迁到 1.4.x 的注意事项**：纯文档改动，无运行时影响。如果 1.4.x 的 `spark-configuration.md` 同样存在 V1 示例，可无风险回迁以保持文档一致性。但 1.4.x 作为维护分支，文档通常以 main 为准并由站点统一发布，**回迁优先级低**，可视情况回迁或不回迁。
