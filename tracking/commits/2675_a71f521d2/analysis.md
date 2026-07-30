# 提交 2675：Spark 4.0: Pass `format-version` when creating a snapshot in table migration actions (#14163)

## 提交信息

- **序号**：2675 / 4088
- **哈希**：a71f521d276f01b1b8a1e72fbf17068a149a31d7
- **短哈希**：a71f521d2
- **日期**：2025-09-23 09:15:49 -0600
- **作者**：Fokko Driesprong
- **提交说明**：Spark 4.0: Pass `format-version` when creating a snapshot in table migration actions (#14163)
- **PR/Issue**：#14163

## 总体目的

本提交修复了 Spark 表迁移操作（snapshot/add_files）中的一个缺陷：在将非 Iceberg 表（如 Parquet 表）通过 `system.snapshot` 存储过程迁移为 Iceberg 表时，生成的 manifest 文件没有正确传递目标表的 `format-version` 和 snapshot ID 信息。

具体问题在于 `SparkTableUtil.buildManifest()` 方法在创建 manifest 写入器时，使用的是 `ManifestFiles.write(spec, outputFile)`——这个重载不指定 format version，默认使用 v2 格式写入 manifest，且不传入 snapshot ID。这导致：
1. 如果用户指定了 `format-version=1` 创建 Iceberg 表，迁移生成的 manifest 仍然是 v2 格式，与表声明的格式不一致。
2. Manifest 条目中的 snapshot ID 未能正确设置——v1 表且未启用 snapshot ID 继承时，应显式设置为 `-1L`；其他情况应留空让后续 commit 时自动分配。

本提交通过将 format-version 和 snapshot ID 传递给 `buildManifest()`，并使用 `ManifestFiles.write(formatVersion, spec, outputFile, snapshotId)` 重载，确保迁移生成的 manifest 与目标表的格式版本一致，并正确处理 snapshot ID 继承行为。

## 如何达成设计目的

将 format-version 和 snapshot ID 的计算逻辑提前到 `mapPartitions` 调用之前（原来在后续 append 阶段才读取），使其能作为参数传入 `buildManifest()`。`buildManifest()` 内部改用支持 format version 和 snapshot ID 的 `ManifestFiles.write()` 重载。同时新增两个测试用例验证 v1 和 v2 格式下 manifest 的元数据和 snapshot ID 行为。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+36/-10 lines)

**修改目的**：让 manifest 生成正确传递 format-version 和 snapshot ID。

**工作逻辑**：
- `buildManifest()` 方法签名新增 `int formatVersion` 和 `Long snapshotId` 两个参数（置于参数列表最前）。内部将 `ManifestFiles.write(spec, outputFile)` 改为 `ManifestFiles.write(formatVersion, spec, outputFile, snapshotId)`，使写入的 manifest 文件携带正确的格式版本和 snapshot ID。
- 在调用 `buildManifest()` 的上游逻辑中（`importSparkTable` 相关方法），将原本位于 append 阶段的 `TableOperations ops`、`formatVersion` 和 `snapshotIdInheritanceEnabled` 计算提前到 `mapPartitions` 之前。新增 snapshot ID 决策逻辑：当 `formatVersion == 1` 且未启用 `SNAPSHOT_ID_INHERITANCE_ENABLED` 时，`snapshotId = -1L`（显式标记无继承）；否则 `snapshotId = null`（留待 commit 时自动分配）。然后将 `formatVersion` 和 `snapshotId` 传入 `buildManifest()` 调用。
- 移除了 append 阶段重复的 `formatVersion` 和 `snapshotIdInheritanceEnabled` 计算（已提前）。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSnapshotTableProcedure.java` (+95/-2 lines)

**修改目的**：验证迁移后 manifest 的 format-version 和 snapshot ID 正确性。

**工作逻辑**：
- 新增 `SNAPSHOT_ID_READ_SCHEMA` 常量，一个仅投影 `snapshot_id` 字段（field ID 1，LongType，optional）的 Schema，用于读取 manifest 条目中的 snapshot ID。
- `testSnapshotPartitioned()`：默认创建 v2 表，执行 snapshot 后遍历当前快照的所有 data manifest，用 Avro reader 读取并断言 manifest 元数据中 `format-version` 为 `"2"`，且每个条目的 `snapshot_id`（field 0）为 null（v2 表中 snapshot ID 在 commit 时自动继承，manifest 中留空）。
- `testSnapshotPartitionedV1()`：通过 `properties => map('format-version', '1')` 创建 v1 表，执行 snapshot 后断言 manifest 元数据中 `format-version` 为 `"1"`，且每个条目的 `snapshot_id` 不为 null（v1 表未启用继承时显式设为 -1L）。
- 新增必要的 import（`Avro`、`AvroIterable`、`GenericAvroReader`、`ManifestFile`、`Schema`、`Types`、`GenericData`、`Lists`）。

## 总结

本提交修复了 Spark 4.0 表迁移操作中 manifest 格式版本传递不正确的问题。通过将 format-version 和 snapshot ID 提前计算并传入 `buildManifest()`，确保迁移生成的 manifest 文件与目标表的格式版本一致，并正确处理 v1/v2 表的 snapshot ID 继承行为。这是 format-version 传递修复在 Spark 4.0 模块的实现，后续提交 2676（Spark 3.5）和 2677（Spark 3.4）将同一修复 backport 到其他 Spark 版本。
