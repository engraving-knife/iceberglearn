# 提交 2677：Spark 3.5: Pass format-version when creating a snapshot in table migration actions (#14169)

## 提交信息

- **序号**：2677 / 4088
- **哈希**：76f7c812a183bff02ad76c1411683432e3f86221
- **短哈希**：76f7c812a
- **日期**：2025-09-23 21:55:01 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Spark 3.5: Pass format-version when creating a snapshot in table migration actions (#14169)
- **PR/Issue**：#14169（与 #14163 同一修复的 Spark 3.5 版本）

## 总体目的

本提交是提交 2674（PR #14163，Spark 4.0 format-version 传递修复）在 Spark 3.5 模块上的等价实现。两者修复的问题完全相同：在 Spark 表迁移操作（`system.snapshot` 存储过程）中，`SparkTableUtil.buildManifest()` 创建 manifest 写入器时未传递目标表的 `format-version` 和 snapshot ID，导致迁移生成的 manifest 格式版本与表声明不一致，且 snapshot ID 继承行为不正确。

由于 Iceberg 为 Spark 3.4、3.5、4.0 分别维护独立的模块（`spark/v3.4`、`spark/v3.5`、`spark/v4.0`），同一修复需要在每个模块中分别落地。本提交覆盖 Spark 3.5 模块。

## 如何达成设计目的

与提交 2674 完全相同的设计：将 format-version 和 snapshot ID 的计算从 append 阶段提前到 `mapPartitions` 之前，作为参数传入 `buildManifest()`，内部改用 `ManifestFiles.write(formatVersion, spec, outputFile, snapshotId)` 重载。新增两个测试用例验证 v1 和 v2 格式下 manifest 的元数据和 snapshot ID 行为。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+37/-10 lines)

**修改目的**：让 Spark 3.5 的 manifest 生成正确传递 format-version 和 snapshot ID。

**工作逻辑**：与提交 2674 的 `spark/v4.0` 版本完全一致。`buildManifest()` 方法签名新增 `int formatVersion` 和 `Long snapshotId` 参数，内部改用 `ManifestFiles.write(formatVersion, spec, outputFile, snapshotId)`。在上游调用处提前计算 `formatVersion`（从目标表元数据读取）和 `snapshotId`（v1 且未启用继承时为 `-1L`，否则为 `null`），并传入 `buildManifest()`。移除 append 阶段重复的计算。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSnapshotTableProcedure.java` (+94/-0 lines)

**修改目的**：验证 Spark 3.5 迁移后 manifest 的 format-version 和 snapshot ID 正确性。

**工作逻辑**：与提交 2674 的测试完全一致。新增 `SNAPSHOT_ID_READ_SCHEMA` 常量、`testSnapshotPartitioned()`（v2，manifest 元数据 format-version 为 "2"，条目 snapshot_id 为 null）和 `testSnapshotPartitionedV1()`（v1，manifest 元数据 format-version 为 "1"，条目 snapshot_id 不为 null）两个测试用例。

## 总结

本提交是 format-version 传递修复在 Spark 3.5 模块的实现，与提交 2674（Spark 4.0）和 2677（Spark 3.4）构成同一修复的三个 Spark 版本变体。修复确保表迁移操作生成的 manifest 文件携带正确的格式版本和 snapshot ID，与目标表的配置一致。三个 Spark 版本模块的代码和测试改动完全对应，体现了 Iceberg 多版本 Spark 适配中同步维护的模式。
