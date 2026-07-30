# 提交 2342：API, Spark: Expose cleanExpiredMetadata in expire_snapshots Spark procedure (#13509)

## 提交信息

- **序号**：2342 / 4088
- **哈希**：5bda66bbb0b1c425e58e60cac8efb29fd1ff04de
- **短哈希**：5bda66bbb
- **日期**：2025-07-11 21:32:37 +0200
- **作者**：gaborkaszab
- **提交说明**：API, Spark: Expose cleanExpiredMetadata in expire_snapshots Spark procedure (#13509)
- **PR/Issue**：#13509

## 总体目的

本提交在 `expire_snapshots` Spark 存储过程中暴露 `cleanExpiredMetadata` 选项，使用户可以在通过 Spark SQL 过期快照时同时清理不再被引用的表元数据（如分区规格和 schema）。

Iceberg 的表元数据会随时间增长：每次 schema 变更会产生新的 schema，每次分区规格变更会产生新的 spec。旧的 schema 和 spec 即使不再被任何快照引用，也会保留在元数据中。`ExpireSnapshots` API 在 Core 模块中已有 `cleanExpiredMetadata(boolean)` 方法来清理这些过期元数据，但此前的 Spark `expire_snapshots` 存储过程没有暴露这个选项，用户只能通过编程 API 使用。

本提交在 API 层的 `ExpireSnapshots` action 接口中添加 `cleanExpiredMetadata` 默认方法，在 Spark 4.0 的 `ExpireSnapshotsSparkAction` 中实现该方法，并在 `ExpireSnapshotsProcedure` 存储过程中暴露为 `clean_expired_metadata` 参数。默认不清理（保持向后兼容），用户可通过 `clean_expired_metadata => true` 启用。

## 如何达成设计目的

设计思路是沿着 API -> Action -> Procedure 三层逐级暴露已有的 Core 功能。

关键设计点：
1. **API 层**：在 `ExpireSnapshots` action 接口中新增 `cleanExpiredMetadata(boolean)` 默认方法，默认抛出 `UnsupportedOperationException`（与 Core 层 `ExpireSnapshots.cleanExpiredMetadata` 对应）。
2. **Action 层**：在 `ExpireSnapshotsSparkAction` 中实现该方法，存储布尔标志，在执行过期时传递给 Core 的 `expireSnapshots.cleanExpiredMetadata()` 调用。
3. **Procedure 层**：在 `ExpireSnapshotsProcedure` 中新增 `clean_expired_metadata` 可选布尔参数，传递给 action。
4. **默认行为**：默认为 `false`（不清理），保持与既有行为的向后兼容。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/ExpireSnapshots.java` (+15/-0 lines)

**修改目的**：在 Action API 接口中新增 `cleanExpiredMetadata` 方法。

**工作逻辑**：新增默认方法 `cleanExpiredMetadata(boolean clean)`，默认抛出 `UnsupportedOperationException`。Javadoc 说明该方法用于过期不再被快照引用的分区规格和 schema 等元数据，与 `org.apache.iceberg.ExpireSnapshots.cleanExpiredMetadata(boolean)` 功能一致。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/ExpireSnapshotsSparkAction.java` (+11/-1 lines)

**修改目的**：实现 `cleanExpiredMetadata` 方法。

**工作逻辑**：
1. 新增 `cleanExpiredMetadata` 布尔字段，默认 `false`。
2. 实现 `cleanExpiredMetadata(boolean)` 方法，设置字段并返回 `this`。
3. 在 `doExecute` 中，将 `expireSnapshots.cleanExpiredFiles(false).commit()` 改为 `expireSnapshots.cleanExpiredMetadata(cleanExpiredMetadata).cleanExpiredFiles(false).commit()`，将标志传递给 Core 的过期操作。
4. 在 `options` 描述中添加 `clean_expired_metadata=` 标志，便于日志追踪。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/ExpireSnapshotsProcedure.java` (+8/-1 lines)

**修改目的**：在存储过程中暴露 `clean_expired_metadata` 参数。

**工作逻辑**：
1. 在 `PARAMETERS` 数组中新增 `optionalInParameter("clean_expired_metadata", DataTypes.BooleanType)`。
2. 在 `call` 方法中解析第 7 个参数（索引 6）为 `Boolean cleanExpiredMetadata`。
3. 当参数非 null 时，调用 `action.cleanExpiredMetadata(cleanExpiredMetadata)`。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestExpireSnapshotsProcedure.java` (+60/-0 lines)

**修改目的**：测试存储过程的元数据清理行为。

**工作逻辑**：新增两个测试：
1. `testNoExpiredMetadataCleanupByDefault`：验证默认不清理元数据——创建表、添加列使产生 2 个 schema，过期旧快照后验证仍有 2 个 schema。
2. `testCleanExpiredMetadata`：验证启用清理——设置 `clean_expired_metadata => true`，过期后验证仅剩最新 schema。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestExpireSnapshotsAction.java` (+63/-0 lines)

**修改目的**：测试 Action 层的元数据清理行为。

**工作逻辑**：新增两个测试：
1. `testNoExpiredMetadataCleanupByDefault`：通过 Action API 验证默认不清理 schema。
2. `testCleanExpiredMetadata`：通过 Action API 验证 `.cleanExpiredMetadata(true)` 后，过期操作清理旧 schema 和旧 spec，仅保留最新的。

## 总结

本提交将 Iceberg Core 已有的 `cleanExpiredMetadata` 功能通过 API 接口、Spark Action 和 Spark 存储过程三层逐级暴露给用户。用户现在可以通过 `CALL system.expire_snapshots(..., clean_expired_metadata => true)` 在过期快照时同时清理不再引用的 schema 和分区规格，减少元数据膨胀。默认行为保持不变（不清理）。
