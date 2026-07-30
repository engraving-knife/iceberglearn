# 提交 2350：Spark 3.4, 3.5: Expose cleanExpiredMetadata in expire_snapshots Spark procedure (#13553)

## 提交信息

- **序号**：2350 / 4088
- **哈希**：4920a0ca28f7bfa9113b9c4abcb54c402504e7f3
- **短哈希**：4920a0ca2
- **日期**：2025-07-14 19:31:50 +0200
- **作者**：gaborkaszab
- **提交说明**：Spark 3.4, 3.5: Expose cleanExpiredMetadata in expire_snapshots Spark procedure (#13553)
- **PR/Issue**：#13553

## 总体目的

这个提交在 Spark 3.4 和 3.5 的 `expire_snapshots` 过程中实际暴露了 `clean_expired_metadata` 参数，使用户能通过 Spark SQL 过程调用来控制是否清理不再被快照引用的元数据（如分区规范和 schemas）。

背景：Iceberg Core API 的 `ExpireSnapshots` action 已支持 `cleanExpiredMetadata` 方法，允许在过期快照时一并清理陈旧的元数据。但 Spark 层的 `ExpireSnapshotsProcedure`（面向用户的 SQL 过程入口）和 `ExpireSnapshotsSparkAction`（Spark 实现）此前没有将这个选项透传给用户。这意味着即使用户希望清理过期元数据，也无法通过 Spark SQL 过程来做到。

本提交将此能力从 Core 层透传到 Spark 3.4 和 3.5 的 procedure 接口，补齐了功能链路。与前一个提交 2349（文档）配套，共同完成了该参数的端到端支持。

## 如何达成设计目的

设计思路分为三步：

1. **Procedure 参数声明**：在 `ExpireSnapshotsProcedure` 的参数数组中新增 `clean_expired_metadata`（可选 boolean 类型）。
2. **参数解析与透传**：在 procedure 执行逻辑中从参数位置读取该值，并通过 `action.cleanExpiredMetadata(cleanExpiredMetadata)` 传递给 Spark action。
3. **Spark Action 实现**：在 `ExpireSnapshotsSparkAction` 中新增 `cleanExpiredMetadata` 字段和对应的 setter 方法，在执行过期时将其传递给底层 `ExpireSnapshots` action，并在描述信息中记录该选项。

同时为两个 Spark 版本（3.4 和 3.5）分别添加了对应的单元测试，验证默认不清理元数据以及启用后正确清理 schemas 的行为。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/ExpireSnapshotsProcedure.java` (+8/-1 lines)

**修改目的**：在 Spark 3.4 的 expire_snapshots 过程中新增 `clean_expired_metadata` 参数。

**工作逻辑**：
- 在 `ProcedureParameter` 数组末尾新增 `ProcedureParameter.optional("clean_expired_metadata", DataTypes.BooleanType)`。
- 在执行方法中从 `args` 索引 6 处读取该布尔值（`args.getBoolean(6)`），处理 null 情况。
- 当值非 null 时，调用 `action.cleanExpiredMetadata(cleanExpiredMetadata)` 将其透传给 Spark action。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/ExpireSnapshotsSparkAction.java` (+11/-1 lines)

**修改目的**：在 Spark 3.4 的 `ExpireSnapshotsSparkAction` 中实现 `cleanExpiredMetadata` 支持。

**工作逻辑**：
- 新增私有字段 `private boolean cleanExpiredMetadata = false;`（默认 false，保持向后兼容）。
- 新增 `cleanExpiredMetadata(boolean clean)` setter 方法，设置字段并返回 `this` 以支持链式调用。
- 在执行过期逻辑时，将原 `expireSnapshots.cleanExpiredFiles(false).commit()` 改为 `expireSnapshots.cleanExpiredMetadata(cleanExpiredMetadata).cleanExpiredFiles(false).commit()`，把清理元数据的选项传给底层 action。
- 在 `toString()` 描述方法中追加 `clean_expired_metadata=...` 选项，便于日志中追踪。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestExpireSnapshotsProcedure.java` (+60/-0 lines)

**修改目的**：为 Spark 3.4 的 procedure 接口添加测试。

**工作逻辑**：新增两个测试：
- `testNoExpiredMetadataCleanupByDefault()`：创建表并插入数据后 ALTER ADD COLUMN，过期旧快照后验证 schemas 数量不变（默认不清理）。
- `testCleanExpiredMetadata()`：同样场景但传入 `clean_expired_metadata => true`，过期后验证只剩最新 schema。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestExpireSnapshotsAction.java` (+63/-0 lines)

**修改目的**：为 Spark 3.4 的 action 接口添加测试。

**工作逻辑**：新增 `testNoExpiredMetadataCleanupByDefault()` 和 `testCleanExpiredMetadata()` 两个测试，通过 Java API 直接调用 action 验证默认行为和启用清理后的行为，后者还验证了分区规范的清理。

### Spark 3.5 对应文件（+76/-2 lines）

**修改目的**：在 Spark 3.5 中做完全相同的改动。

**工作逻辑**：`spark/v3.5/` 下的 `ExpireSnapshotsProcedure.java`、`ExpireSnapshotsSparkAction.java`、`TestExpireSnapshotsProcedure.java`、`TestExpireSnapshotsAction.java` 做了与 3.4 完全一致的修改和测试。

## 总结

该提交在 Spark 3.4 和 3.5 的 `expire_snapshots` 过程中完整暴露了 `clean_expired_metadata` 参数，打通了从 Core API 到 Spark SQL 过程的端到端链路，并配备了覆盖默认行为和启用行为的单元测试。使用户能通过 SQL 过程调用控制是否在过期快照时清理陈旧的 schemas 和分区规范，配合提交 2349 的文档，完成了该特性的完整交付。
