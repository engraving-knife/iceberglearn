# 提交 1827：Spec: Add implementation note on `current-snapshot-id` (#12334)

## 提交信息

- **序号**：1827 / 4088
- **哈希**：41ed4500725ed85a04fff6c698eec9e9824098e3
- **短哈希**：41ed45007
- **日期**：2025-03-06 08:42:32 +0100
- **作者**：Fokko Driesprong
- **提交说明**：Spec: Add implementation note on `current-snapshot-id` (#12334)
- **PR/Issue**：#12334

## 总体目的

该提交在 Iceberg 格式规范 `format/spec.md` 中新增了关于快照 ID 分配和 `current-snapshot-id` 字段的实现说明，正式规范化了此前只在 Java 实现中约定但未写入规范的行为。

此前 spec 中没有说明：
1. 快照 ID 应如何生成（正值、低碰撞概率）；
2. `current-snapshot-id` 为 `-1` 表示"无当前快照"的哨兵值约定；
3. V3+ 表应使用 `null` 而非 `-1`。

这些行为在 Java 实现中早已存在（提交 1826 实现了 V3+ 写 `null`），但缺少规范层面的说明，导致其他实现无法参考。本提交补全了这些实现说明。

## 如何达成设计目的

在 `format/spec.md` 的"Snapshot summary"小节之后新增 "Assignment of Snapshot IDs and `current-snapshot-id`" 小节，包含三段说明：

1. **快照 ID 生成建议**：写入者应产生正值、低碰撞概率的快照 ID，不建议仅基于时间戳（碰撞概率高）。
2. **Java 参考实现方法**：使用 UUID v4，取高 4 字节与低 4 字节 XOR，再 AND `Long.MAX_VALUE`，得到伪随机快照 ID。
3. **`current-snapshot-id` 的 `-1` 与 `null`**：Java 在 V1/V2 表写 `-1` 表示"无当前快照"，其他实现可接受 `-1` 等价于 `null`；V3+ 表 Java 将写 `null`。

## 修改详情

### `format/spec.md` (修改, +8 lines)

在 Snapshot summary 表格之后新增：

```markdown
### Assignment of Snapshot IDs and `current-snapshot-id`

Writers should produce positive values for snapshot ids in a manner that minimizes the
probability of id collisions and should verify the id does not conflict with existing
snapshots. Producing snapshot ids based on timestamps alone is not recommended as it
increases the potential for collisions.

The reference Java implementation uses a type 4 uuid and XORs the 4 most significant bytes
with the 4 least significant bytes then ANDs with the maximum long value to arrive at a
pseudo-random snapshot id with a low probability of collision.

Java writes `-1` for "no current snapshot" with V1 and V2 tables and considers this
equivalent to omitted or `null`. This has never been formalized in the spec, but for
compatibility, other implementations can accept `-1` as `null`. Java will no longer write
`-1` and will use `null` for "no current snapshot" for all tables with a version greater
than or equal to V3.
```

## 小结

- **成效**：正式在 spec 中记录了快照 ID 生成建议和 `current-snapshot-id` 的 `-1`/`null` 约定，使规范与 Java 实现对齐，为其他语言实现提供参考。
- **影响范围**：仅 `format/spec.md` 文档，不影响运行时代码。纯文档改动。
- **回迁到 1.4.x 的注意事项**：纯 spec 文档修复，可安全回迁。如果 1.4.x 的 spec 已有相关内容则合并，否则直接 cherry-pick。需与 1826（代码实现）配合理解。
