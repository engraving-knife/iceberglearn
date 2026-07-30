# 提交 2444：Flink: Expose cleanExpiredMetadata for snapshot expiration (#13569)

## 提交信息

- **序号**：2444 / 4088
- **哈希**：c23b341ea4398c2513fe2495a44fc6f23e4d72ef
- **短哈希**：c23b341e4
- **日期**：2025-08-04 13:44:40 +0200
- **作者**：gaborkaszab
- **提交说明**：Flink: Expose cleanExpiredMetadata for snapshot expiration (#13569)
- **PR/Issue**：#13569

## 总体目的

本提交为 Flink 维护 API 的 `ExpireSnapshots` 操作新增了 `cleanExpiredMetadata` 配置，使 Flink 的快照过期操作能够清理不再使用的表元数据（如旧的分区 spec 和 schema）。

Iceberg Core 的 `ExpireSnapshots` API 本身已提供 `cleanExpiredMetadata(boolean)` 方法，用于在过期快照时一并清理不再被任何快照引用的元数据（partition specs、schemas 等）。这些元数据会随着表的演进（如新增列、修改分区）不断累积，如果不清理会造成元数据膨胀。然而 Flink 维护 API 的 `ExpireSnapshots.Builder` 此前并未暴露该选项，用户无法通过 Flink 维护任务触发元数据清理。

本提交在 Flink 的 `ExpireSnapshots.Builder` 中新增 `cleanExpiredMetadata(boolean)` 方法，并将该配置通过 `ExpireSnapshotsProcessor` 传递到底层的 `ExpireSnapshots` 操作。配置为 `null`（未设置）时不调用该方法，保持原有行为（由 Core 默认值决定）。

## 如何达成设计目的

1. 在 `ExpireSnapshots.Builder` 中新增 `cleanExpiredMetadata` 字段（`Boolean` 类型，默认 null）和 builder 方法。
2. 创建 `ExpireSnapshotsProcessor` 算子时传入该配置。
3. 在 `ExpireSnapshotsProcessor` 中，当配置非 null 时调用 `expireSnapshots.cleanExpiredMetadata(...)`。
4. 在 `OperatorTestBase` 中新增辅助 insert 重载（支持 extra 列）。
5. 新增参数化测试 `testCleanExpiredMetadata`，分别验证开启和关闭元数据清理的效果。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ExpireSnapshots.java` (+16/-1 lines)

**修改目的**：在 Builder 中暴露 cleanExpiredMetadata 配置。

**工作逻辑**：新增 `private Boolean cleanExpiredMetadata = null` 字段。新增 `cleanExpiredMetadata(boolean newCleanExpiredMetadata)` builder 方法（链式返回 this）。在构建 `ExpireSnapshotsProcessor` 算子时，将该值作为最后一个构造参数传入。使用 `Boolean`（包装类型）而非 `boolean`，以便区分"未设置"（null，不调用 Core 方法）和"显式设置"两种情况。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ExpireSnapshotsProcessor.java` (+11/-2 lines)

**修改目的**：接收并应用 cleanExpiredMetadata 配置。

**工作逻辑**：
- 新增 `private final Boolean cleanExpiredMetadata` 字段，构造函数新增对应参数并赋值。
- 顺带修正了一处 typo：`"Table loader should no be null"` → `"Table loader should not be null"`。
- 在 `processElement` 中构建 `ExpireSnapshots` 操作后，当 `cleanExpiredMetadata != null` 时调用 `expireSnapshots.cleanExpiredMetadata(cleanExpiredMetadata)`，使 Core 的过期操作按用户配置清理或不清理元数据。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (+6/-0 lines)

**修改目的**：新增支持 extra 列的 insert 辅助方法。

**工作逻辑**：新增 `insert(Table, Integer id, String data, String extra)` 重载方法，使用 `GenericAppenderHelper` 追加含三字段的记录并刷新表。供测试在 schema 演进后插入带 extra 列的数据使用。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestExpireSnapshotsProcessor.java` (+44/-1 lines)

**修改目的**：验证 cleanExpiredMetadata 功能。

**工作逻辑**：
- 原有测试的 `ExpireSnapshotsProcessor` 构造调用补充第五个参数 `false`。
- 新增参数化测试 `testCleanExpiredMetadata(boolean cleanExpiredMetadata)`（参数化 true/false）：
  1. 创建表并插入一条记录（id=1），此时有 1 个 schema。
  2. 演进 schema 新增 `extra` 列（String），插入带 extra 的记录（id=2, extra="x"），此时有 2 个 schema。
  3. 创建 `ExpireSnapshotsProcessor`，`maxSnapshotAgeMs=0L`（立即过期）、`numSnapshots=1`（只保留 1 个快照）、`cleanExpiredMetadata` 由参数决定。
  4. 触发过期后，断言成功、只剩 1 个快照、有 1 条删除记录。
  5. 关键断言：若 `cleanExpiredMetadata=true`，则 `table.schemas()` 只剩当前 schema（旧的已清理）；若 `false`，则仍有 2 个 schema（旧 schema 保留）。新增 `Types` import。

## 总结

本提交为 Flink 的 `ExpireSnapshots` 维护操作补齐了 `cleanExpiredMetadata` 配置，使 Flink 用户能够控制快照过期时是否清理不再使用的表元数据（schema、partition spec）。实现采用 `Boolean` 包装类型以区分"未设置"和"显式设置"，并附带修复了一个 typo。参数化测试验证了开启和关闭两种情况下的元数据清理行为。
