# 提交 2678：Spark 3.4: Pass format-version when creating a snapshot (#14170)

## 提交信息

- **序号**：2678 / 4088
- **哈希**：d09815b0ecd4c0a80411df89908898be9fafb70b
- **短哈希**：d09815b0e
- **日期**：2025-09-23 21:55:12 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Spark 3.4: Pass format-version when creating a snapshot (#14170)
- **PR/Issue**：#14170（与 #14163 同一修复的 Spark 3.4 版本）

## 总体目的

本提交是提交 2674（PR #14163，Spark 4.0 format-version 传递修复）在 Spark 3.4 模块上的等价实现。修复的问题完全相同：Spark 表迁移操作（`system.snapshot` 存储过程）中 `SparkTableUtil.buildManifest()` 未传递目标表的 `format-version` 和 snapshot ID，导致迁移生成的 manifest 格式版本与表声明不一致，且 snapshot ID 继承行为不正确。

这是同一修复在三个 Spark 版本模块（3.4、3.5、4.0）中的第三个落地，覆盖最老的 Spark 3.4 模块。

## 如何达成设计目的

与提交 2674 和 2676 完全相同的设计：将 format-version 和 snapshot ID 的计算提前到 `mapPartitions` 之前，传入 `buildManifest()`，内部改用 `ManifestFiles.write(formatVersion, spec, outputFile, snapshotId)` 重载。新增两个测试用例验证 v1 和 v2 格式。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (+37/-10 lines)

**修改目的**：让 Spark 3.4 的 manifest 生成正确传递 format-version 和 snapshot ID。

**工作逻辑**：与提交 2674（Spark 4.0）和 2676（Spark 3.5）的对应文件完全一致。`buildManifest()` 方法签名新增 `int formatVersion` 和 `Long snapshotId` 参数，内部改用 `ManifestFiles.write(formatVersion, spec, outputFile, snapshotId)`。在上游调用处提前计算 `formatVersion` 和 `snapshotId`（v1 且未启用继承时为 `-1L`，否则为 `null`），并传入 `buildManifest()`。移除 append 阶段重复的计算。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSnapshotTableProcedure.java` (+94/-0 lines)

**修改目的**：验证 Spark 3.4 迁移后 manifest 的 format-version 和 snapshot ID 正确性。

**工作逻辑**：与提交 2674 和 2676 的测试完全一致。新增 `SNAPSHOT_ID_READ_SCHEMA` 常量、`testSnapshotPartitioned()`（v2 验证）和 `testSnapshotPartitionedV1()`（v1 验证）两个测试用例。

## 总结

本提交是 format-version 传递修复在 Spark 3.4 模块的实现，与提交 2674（Spark 4.0）和 2676（Spark 3.5）构成同一修复的三个 Spark 版本变体。三个提交由同一作者在同一时间提交（时间戳仅差 11 秒），代码和测试改动完全对应。修复确保表迁移操作生成的 manifest 文件携带正确的格式版本和 snapshot ID，保证 v1/v2 表的元数据一致性。
