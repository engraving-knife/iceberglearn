# 提交 1860：Flink 1.18, 1.19: Implement timestamp(9), unknown, and defaults (#12532)

## 提交信息

- **序号**：1860 / 4088
- **哈希**：a706c348a1f6983690fd0f261f78b360ac558c13
- **短哈希**：a706c348a
- **日期**：2025-03-15 13:24:30 -0700
- **作者**：Ryan Blue
- **提交说明**：Flink 1.18, 1.19: Implement timestamp(9), unknown, and defaults (#12532)
- **PR/Issue**：#12532

## 总体目的

这是提交 1858（#12470，Flink 1.20 支持 v3 spec 的 `TimestampNanoType`、`UnknownType` 与列默认值）的姊妹提交，把完全相同的功能补齐到 Flink 1.18 与 Flink 1.19 两个模块。

Iceberg 同时维护 flink 1.18 / 1.19 / 1.20 三个版本的集成模块（因 Flink 各版本的 API 有细微差异需分别维护）。1858 只改了 1.20，1.18 和 1.19 仍不支持 v3 类型，导致：

1. **跨版本行为不一致**：同一张含 `timestamp(9)` 或列默认值的 v3 Iceberg 表，在 Flink 1.20 能正确读写，但在 1.18/1.19 上会抛 `UnsupportedOperationException` 或类型映射错误。
2. **用户升级受阻**：仍在用 Flink 1.18/1.19 的用户无法使用 Iceberg v3 表的新特性。

本提交把 1858 的全部修改复制到 flink 1.18 与 1.19 两个模块（每个模块 17 个文件），让三个 Flink 版本的 v3 支持完全对齐。注意 `api/src/test/java/org/apache/iceberg/util/RandomUtil.java` 的改动已在 1858 完成（共享 api 模块），本提交不再重复。

## 如何达成设计目的

与 1858 完全相同的修改思路，逐文件复制到 `flink/v1.18/flink/...` 与 `flink/v1.19/flink/...` 两个目录树下。修改覆盖：类型映射、行数据访问、Avro 读写、Parquet 读写、常量转换、测试工具、测试用例。每个 Flink 版本的修改内容与 1858 在 1.20 上的修改逐行一致（仅包路径前缀 `flink/v1.18` / `flink/v1.19` 不同）。详见 1858 的分析，此处不重复展开每个文件的逻辑。

## 修改详情

### flink/v1.18 与 flink/v1.19 各 17 个文件（每版本与 1858 在 1.20 上的修改一致）

**主代码（每版本 10 个文件）**：
- `flink/FlinkRowData.java`（+5）：`NullType` 字段访问返回 null。
- `flink/TypeToFlinkType.java`（+12）：`UNKNOWN` → `NullType`，`TIMESTAMP_NANO` → `TimestampType(9)`/`LocalZonedTimestampType(9)`。
- `flink/data/FlinkAvroWriter.java`（+3）：新增 `timestamp-nanos` case。
- `flink/data/FlinkParquetReaders.java`（+14/-54）：新增 `NanosToTimestampReader`，删除 Tz 专用 reader，微秒负数改 `floorDiv/floorMod`。
- `flink/data/FlinkParquetWriters.java`（+159/-30）：逻辑类型分发重构为 `LogicalTypeWriterBuilder` visitor，新增 `timestampNanos`。
- `flink/data/FlinkPlannedAvroReader.java`（+4/-2）：`buildReadPlan` 传 `RowDataUtil::convertConstant`，新增 `timestamp-nanos` case。
- `flink/data/FlinkValueReaders.java`（+18/-6）：新增 `TimestampNanosReader`，微秒改 `floorDiv/floorMod`。
- `flink/data/FlinkValueWriters.java`（+15）：新增 `TimestampNanosWriter`。
- `flink/data/ParquetWithFlinkSchemaVisitor.java`（+11/-6）：`visitFields` 跳过 `LogicalTypeRoot.NULL` 字段。
- `flink/data/RowDataUtil.java`（+4）：`convertConstant` 新增 `case UUID`。

**测试代码（每版本 7 个文件）**：
- `flink/RowDataConverter.java`（+14/-6）：新增 `TIMESTAMP_NANO` case 与 `convertTimestamp` 方法。
- `flink/TestHelpers.java`（+44/-13）：默认值字段断言 + `TIMESTAMP_NANO` 断言。
- `flink/data/AbstractTestFlinkAvroReaderWriter.java`（删除 182 行）。
- `flink/data/TestFlinkAvroPlannedReaderWriter.java`（删除 34 行）。
- `flink/data/TestFlinkAvroReaderWriter.java`（新增 136 行）：合并替代上述两个旧测试。
- `flink/data/TestFlinkParquetReader.java`（+10）。
- `flink/data/TestFlinkParquetWriter.java`（+21/-2）。

## 小结

- **成效**：Flink 1.18 与 1.19 模块获得与 1.20 完全相同的 v3 spec 支持（`TimestampNanoType`、`UnknownType`、列默认值），三个 Flink 版本行为对齐。
- **影响范围**：flink 1.18 与 1.19 各 17 个文件，合计 34 个文件、+910/-696 行。属于功能增强，影响所有使用 v3 类型的 Flink 1.18/1.19 作业。
- **回迁到 1.4.x 的注意事项**：与 1858 一致，依赖 v3 spec 在 1.4.x 已支持。若 1.4.x 仍维护 flink 1.18/1.19 模块且需要 v3 支持，建议与 1858 一并回迁。两个版本修改内容完全相同，回迁时可机械复制。注意 `api/src/test/java/org/apache/iceberg/util/RandomUtil.java` 的 `TIMESTAMP_NANO` case 已在 1858 改动（共享 api 模块），本提交不重复。
