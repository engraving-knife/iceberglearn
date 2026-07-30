# 提交 1755：Revert "Core: Serialize `null` when there is no current snapshot (#11560)" (#12312)

## 提交信息

- **序号**：1755 / 4088
- **哈希**：3a23f857841e1d2e991efa00f331f093fca5e9c4
- **短哈希**：3a23f8578
- **日期**：2025-02-19 10:19:08 +0100
- **作者**：Fokko Driesprong
- **提交说明**：Revert "Core: Serialize `null` when there is no current snapshot (#11560)" (#12312)
- **PR/Issue**：#12312

## 总体目的

本提交回退了之前提交 #11560（commit `bf8d25fe`）所引入的变更。原提交 #11560 将 `TableMetadataParser` 在没有当前快照（current snapshot）时序列化的行为从写入 `-1` 改为写入 `null`。本回退将其恢复为原来的行为——当没有当前快照时，`current-snapshot-id` 字段写入 `-1` 而非 `null`。

回退的原因是：将 `current-snapshot-id` 序列化为 `null` 会导致向后兼容性问题。Iceberg 表的元数据格式规范中，`current-snapshot-id` 应为整数类型，使用 `-1` 表示无当前快照是既有的约定。改为 `null` 后，旧版本的读取器或其他兼容客户端可能无法正确解析该字段（例如在期望整数的地方遇到 null，可能导致解析错误或异常），因此需要回退以恢复兼容性。

## 如何达成设计目的

提交通过 `git revert` 操作回退原提交的变更：
1. 修改 `TableMetadataParser.java` 中序列化 `current-snapshot-id` 的逻辑，从原来的条件判断写 `null` 恢复为使用三元表达式写 `-1`。
2. 修改对应的测试文件，将期望值从 `null` 改回 `-1`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java`（修改, +3/-5 lines）

**修改目的**：恢复无当前快照时写入 `-1` 而非 `null` 的序列化行为。

**工作逻辑**：原 #11560 引入的代码为：
```java
if (metadata.currentSnapshot() != null) {
    generator.writeNumberField(CURRENT_SNAPSHOT_ID, metadata.currentSnapshot().snapshotId());
} else {
    generator.writeNullField(CURRENT_SNAPSHOT_ID);
}
```

回退后的代码为：
```java
generator.writeNumberField(
    CURRENT_SNAPSHOT_ID,
    metadata.currentSnapshot() != null ? metadata.currentSnapshot().snapshotId() : -1);
```

当有当前快照时写入快照 ID，没有时写入 `-1`（整数），而非 `null`。这恢复了 Iceberg 元数据格式规范中的传统行为。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestLoadTableResponseParser.java`（修改, +3/-3 lines）

**修改目的**：将测试期望值从 `null` 改回 `-1`，与回退后的序列化行为一致。

**工作逻辑**：三处测试用例中，将 `"current-snapshot-id" : null` 改回 `"current-snapshot-id" : -1`。这三处分别对应不同的表加载响应解析测试场景，确保测试期望与实际序列化输出一致。

## 小结

- **成效**：回退了 #11560 引入的 `null` 序列化行为，恢复为使用 `-1` 表示无当前快照，确保了向后兼容性和元数据格式规范一致性。
- **影响范围**：修改 Core 模块的 `TableMetadataParser`，影响所有表元数据的序列化/反序列化场景。此回退影响面较广，因为 `current-snapshot-id` 是表元数据的核心字段之一。
- **回迁到 1.4.x 的注意事项**：如果 1.4.x 分支没有引入 #11560 的变更（即一直使用 `-1`），则不需要回迁此回退提交。如果 1.4.x 分支已回迁了 #11560，则应一并回迁此回退。建议确认 1.4.x 分支中 `TableMetadataParser` 的当前行为后再决定。
