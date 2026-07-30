# 提交 0739：Flink: Backport #10200 to v1.19 and v1.17

## 提交信息
- **序号**：0739 / 4088
- **哈希**：032330856631ebeb22718acbc64fb19011f2f064
- **短哈希**：032330856
- **日期**：2024-05-01 20:46:38 +0200
- **作者**：pvary <peter.vary.apache@gmail.com>
- **提交说明**：Flink: Backport #10200 to v1.19 and v1.17 (#10259)
- **PR/Issue**：#10259（回迁 #10200）

## 总体目的

本提交将 #10200（即提交 0737 `Flink: Apply DeleteGranularity for writes`）的 Flink 部分改动回迁（backport）到 `flink/v1.17` 和 `flink/v1.19` 两个 Flink 版本目录。

### 背景与动机

提交 0737 将 `DeleteGranularity` 能力引入了 core 模块的 `BaseTaskWriter` 以及 `flink/v1.18` 目录的 `BaseDeltaTaskWriter`。但 Iceberg 的 Flink 集成同时维护多个 Flink 版本目录（v1.17、v1.18、v1.19 等），每个目录有独立的 `BaseDeltaTaskWriter` 和测试代码副本。0737 只改了 v1.18，v1.17 和 v1.19 的代码未同步，导致不同 Flink 版本的行为不一致。

本提交补齐了 v1.17 和 v1.19 的改动，使所有受支持的 Flink 版本都使用 `DeleteGranularity.FILE` 粒度写入位置删除，并具备相同的删除统计信息测试覆盖。

### 与提交 0737 的关系

本提交是 0737 的补充，仅包含 Flink 层改动（`BaseDeltaTaskWriter` + `TestFlinkIcebergSinkV2`），**不包含** core 模块的 `BaseTaskWriter`/`SortingPositionOnlyDeleteWriter` 改动——因为这些 core 改动在 main 分支已由 0737 完成，对所有 Flink 版本目录共享生效。本提交只需让各 Flink 版本目录的 `RowDataDeltaWriter` 调用新的带 `DeleteGranularity` 参数的父类构造函数。

## 如何达成设计目的

对 v1.17 和 v1.19 两个目录分别应用与 0737 中 v1.18 完全相同的改动：

1. **`BaseDeltaTaskWriter`**：在 `RowDataDeltaWriter` 构造函数中，将 `super(partition, schema, deleteSchema)` 改为 `super(partition, schema, deleteSchema, DeleteGranularity.FILE)`，并引入 `DeleteGranularity` 的 import。这使得该 Flink 版本的 sink 也使用文件粒度组织位置删除文件。

2. **`TestFlinkIcebergSinkV2`**：新增 `testDeleteStats()` 测试和 `globalTimeout` 规则，与 v1.18 的测试完全一致，验证 FILE 粒度下删除文件的下界统计正确关联到数据文件路径。

之所以 v1.17、v1.18、v1.19 三个目录的改动完全相同，是因为这三个 Flink 版本目录中的 `BaseDeltaTaskWriter` 和 `TestFlinkIcebergSinkV2` 代码在此处结构一致（Iceberg 对多 Flink 版本采用代码复制策略，而非条件编译）。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/BaseDeltaTaskWriter.java`
**修改目的**：让 Flink 1.17 sink 使用 FILE 粒度写入位置删除。

**具体改动**：
- 引入 `import org.apache.iceberg.deletes.DeleteGranularity;`。
- `RowDataDeltaWriter` 构造函数调用改为 `super(partition, schema, deleteSchema, DeleteGranularity.FILE);`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2.java`
**修改目的**：为 Flink 1.17 新增删除统计信息测试。

**具体改动**：
- 新增 import：`assumeThat`、`DataFile`、`DeleteFile`、`MetadataColumns`、`Timeout`。
- 新增 `@Rule public final Timeout globalTimeout = Timeout.seconds(60);`。
- 新增 `testDeleteStats()` 测试：写入 `+I(1,aaa)` / `-D(1,aaa)` / `+I(1,aaa)` 序列，验证删除文件的 `lowerBounds` 中 `DELETE_FILE_PATH` 等于数据文件路径（跳过 Avro 格式）。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/BaseDeltaTaskWriter.java`
**修改目的**：让 Flink 1.19 sink 使用 FILE 粒度写入位置删除。

**具体改动**：与 v1.17 完全相同（引入 `DeleteGranularity` import + 构造函数传参 `DeleteGranularity.FILE`）。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkV2.java`
**修改目的**：为 Flink 1.19 新增删除统计信息测试。

**具体改动**：与 v1.17 完全相同（新增 import、globalTimeout 规则、`testDeleteStats()` 测试）。

## 小结

- **成效**：成功将 `DeleteGranularity.FILE` 粒度配置回迁到 Flink 1.17 和 1.19 版本目录。结合 0737 对 v1.18 的改动，现在所有三个受支持的 Flink 版本（1.17/1.18/1.19）行为一致，均使用文件粒度组织位置删除文件。
- **影响范围**：仅影响 `flink/v1.17` 和 `flink/v1.19` 两个模块目录。行为变更与 0737 对 v1.18 的影响相同：Flink sink 的删除文件从分区级组织变为文件级组织，删除文件数量可能增多但读取效率提升。不涉及 core 模块改动。
- **回迁到 1.4.x 的注意事项**：
  - 本提交依赖 0737 的 core 模块改动（`BaseTaskWriter` 新增带 `DeleteGranularity` 的构造函数）。回迁时必须先回迁 0737 的 core 部分，否则 `super(partition, schema, deleteSchema, DeleteGranularity.FILE)` 调用会因父类无对应构造函数而编译失败。
  - 需确认 1.4.x 支持的 Flink 版本目录。1.4.x 可能支持不同的 Flink 版本（如 v1.17/v1.18/v1.19/v1.20），需对每个存在的目录分别应用 `BaseDeltaTaskWriter` 改动。
  - 各 Flink 版本目录的 `BaseDeltaTaskWriter` 和 `TestFlinkIcebergSinkV2` 代码结构应与 main 分支一致，若 1.4.x 有差异需相应调整。
  - 与 0737 一样，FILE 粒度会增加删除文件数量，需关注 1.4.x 是否有配套的删除文件压缩机制。
