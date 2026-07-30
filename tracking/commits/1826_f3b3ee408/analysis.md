# 提交 1826：Core: Write `null` for `current-snapshot-id` for V3+ (#12335)

## 提交信息

- **序号**：1826 / 4088
- **哈希**：f3b3ee40871d38083ed095215fffa91acb2c8a45
- **短哈希**：f3b3ee408
- **日期**：2025-03-06 08:20:34 +0100
- **作者**：Fokko Driesprong
- **提交说明**：Core: Write `null` for `current-snapshot-id` for V3+ (#12335)
- **PR/Issue**：#12335（Closes #12310）

## 总体目的

该提交修改了 Iceberg 表元数据 JSON 序列化时 `current-snapshot-id` 字段的写入行为：对于 V3+ 格式的表，当没有当前快照时写入 `null` 而非 `-1`。

此前，`TableMetadataParser` 在序列化表元数据时，如果没有当前快照（`metadata.currentSnapshot() == null`），会写入 `"current-snapshot-id": -1`。这个 `-1` 哨兵值是 Java 实现的约定，从未在 Iceberg 规范中正式定义。其他实现（如 Python、Rust）可能不认识 `-1` 的含义，将其当作一个有效的快照 ID 处理，导致行为不一致。

Iceberg 社区在邮件列表讨论后决定：对于 V3+ 格式的表，使用 JSON 的 `null` 表示"无当前快照"，与 JSON 语义一致；对于 V1/V2 表，保持 `-1` 以向后兼容。同时在 spec 中补充说明（见提交 1827）。

## 如何达成设计目的

在 `TableMetadataParser` 的序列化逻辑中，根据 `metadata.formatVersion()` 区分处理：
- 有当前快照时：正常写入快照 ID（不变）。
- 无当前快照时：
  - V3+：写入 `null`（`generator.writeNullField`）。
  - V1/V2：写入 `-1`（保持旧行为）。

新增常量 `MIN_NULL_CURRENT_SNAPSHOT_VERSION = 3` 控制行为切换点。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java` (修改, +13/-8 lines)

**修改目的**：区分 V3+ 和 V1/V2 的 `current-snapshot-id` 序列化行为。

**工作逻辑**：
```java
// 旧代码：无条件写 -1
generator.writeNumberField(CURRENT_SNAPSHOT_ID,
    metadata.currentSnapshot() != null ? metadata.currentSnapshot().snapshotId() : -1);

// 新代码：V3+ 写 null，V1/V2 写 -1
if (metadata.currentSnapshot() != null) {
    generator.writeNumberField(CURRENT_SNAPSHOT_ID, metadata.currentSnapshot().snapshotId());
} else {
    if (metadata.formatVersion() >= MIN_NULL_CURRENT_SNAPSHOT_VERSION) {
        generator.writeNullField(CURRENT_SNAPSHOT_ID);
    } else {
        generator.writeNumberField(CURRENT_SNAPSHOT_ID, -1L);
    }
}
```

### `core/src/test/java/org/apache/iceberg/rest/responses/TestLoadTableResponseParser.java` (修改, +92/-8 lines)

**修改目的**：覆盖 V1、V2、V3 三种格式版本的序列化行为。

**工作逻辑**：
- 原 `roundTripSerde` 拆分为 `roundTripSerdeV1` 和 `roundTripSerdeV2andHigher`（参数化测试，覆盖 V2 和 MAX_FORMAT_VERSION）。
- V1 测试验证 `current-snapshot-id` 不出现（V1 无快照时不写该字段）。
- V2+ 测试验证：V2 写 `-1`，V3 写 `null`。通过 `formatVersion >= 3 ? "null" : "-1"` 动态生成期望 JSON。

## 小结

- **成效**：V3+ 表在无当前快照时写入 `null` 而非 `-1`，消除了未规范化的哨兵值，使 JSON 语义更清晰，便于非 Java 实现正确处理。
- **影响范围**：仅 `TableMetadataParser.java` 的序列化逻辑，不影响反序列化（解析端已能处理 `-1`、`null`、缺失三种情况）。V1/V2 行为不变，向后兼容。
- **回迁到 1.4.x 的注意事项**：如果 1.4.x 支持 V3 格式，建议回迁此修复。如果 1.4.x 仅支持 V1/V2，则无需回迁（行为不变）。需注意 1.4.x 的 `TableMetadataParser` 反序列化是否已能正确处理 `null`（即 `jsonNull` 时返回 null 而非报错）。
