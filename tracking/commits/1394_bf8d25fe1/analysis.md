# 提交 1394：Core: Serialize `null` when there is no current snapshot (#11560)

## 提交信息

- **序号**：1394 / 4088
- **哈希**：bf8d25fe1578ef199d64fb609c0299728ec58910
- **短哈希**：bf8d25fe1
- **日期**：2024-11-18（Mon Nov 18 18:14:35 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Core: Serialize `null` when there is no current snapshot (#11560)
- **PR/Issue**：#11560

## 总体目的

`TableMetadataParser` 负责把 `TableMetadata` 序列化为 Iceberg 表元数据 JSON 文件（`metadata.json`）。当一张表尚未有任何提交（即没有当前快照）时，`metadata.currentSnapshot()` 返回 `null`。此前的实现用 `metadata.currentSnapshot() != null ? metadata.currentSnapshot().snapshotId() : -1`，在没有当前快照时把 `current-snapshot-id` 字段序列化为 `-1`。

Iceberg 表元数据规范（spec）实际上规定：`current-snapshot-id` 在没有当前快照时应为 `null`（或可省略），而不是 `-1`。`-1` 既不是合法的快照 ID，也容易让消费方误判为"存在一个 ID 为 -1 的快照"，从而触发解析错误或与 REST Catalog 等下游组件的协议不一致（REST 规范期望 JSON 中 `null`）。

本提交修正序列化逻辑：当没有当前快照时，写出一个 JSON `null` 字段，而不是 `-1`，使序列化产物符合规范并与其他实现（如 REST Catalog 服务端）保持一致。

## 如何达成设计目的

修改 `TableMetadataParser#toJson` 中对 `current-snapshot-id` 字段的写入逻辑：

- 旧逻辑：始终调用 `generator.writeNumberField(CURRENT_SNAPSHOT_ID, ...)`，无快照时传入 `-1`。
- 新逻辑：先用 `if (metadata.currentSnapshot() != null)` 判断，有快照时写数字字段 `writeNumberField`，无快照时改用 `generator.writeNullField(CURRENT_SNAPSHOT_ID)` 写出 JSON `null`。

`writeNullField` 是 Jackson `JsonGenerator` 的标准方法，会输出 `"current-snapshot-id": null`。同步更新对应单元测试 `TestLoadTableResponseParser` 中三处期望字符串，把 `"current-snapshot-id" : -1` 改为 `"current-snapshot-id" : null`，确保解析测试与新序列化行为一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java`

**修改目的**：修正无当前快照时 `current-snapshot-id` 字段的序列化值。

**工作逻辑**（位于第 214 行附近）：

修改前：

```java
generator.writeNumberField(
    CURRENT_SNAPSHOT_ID,
    metadata.currentSnapshot() != null ? metadata.currentSnapshot().snapshotId() : -1);
```

修改后：

```java
if (metadata.currentSnapshot() != null) {
  generator.writeNumberField(CURRENT_SNAPSHOT_ID, metadata.currentSnapshot().snapshotId());
} else {
  generator.writeNullField(CURRENT_SNAPSHOT_ID);
}
```

这样在 JSON 中：
- 有快照时：`"current-snapshot-id" : 123456789`
- 无快照时：`"current-snapshot-id" : null`

### `core/src/test/java/org/apache/iceberg/rest/responses/TestLoadTableResponseParser.java`

**修改目的**：更新三处测试期望 JSON 字符串，使其与新序列化行为一致。

**工作逻辑**：在三个测试用例的预期 JSON 中，把：

```json
"current-snapshot-id" : -1,
```

改为：

```json
"current-snapshot-id" : null,
```

这三处分别对应不同的 `LoadTableResponse` 序列化测试场景（基本加载、带统计信息等）。测试通过断言序列化结果与期望 JSON 字符串完全相等来验证行为，因此期望字符串必须同步更新。

## 小结

- **成效**：表元数据 JSON 在无当前快照时序列化为 `null` 而非 `-1`，符合 Iceberg 表元数据规范，并与 REST Catalog 协议保持一致，避免下游消费方误判。
- **影响范围**：2 个文件、8 处新增、6 处删除；核心改动仅 `TableMetadataParser` 一处 if/else，其余为测试期望字符串更新。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个符合规范的 bug 修复，建议回迁到 1.4.x，特别是当 1.4.x 与 REST Catalog 交互时，`-1` 可能导致 REST 服务端或客户端解析异常。
  - 回迁风险低：序列化端改为 `null`，反序列化端 `TableMetadataParser` 历来支持读取 `null`（因为本就是规范允许的值），所以旧客户端读取新格式无问题；新客户端读取旧格式（`-1`）也无问题，因为反序列化时 `-1` 会被当作一个不存在的快照 ID 处理。
  - 需注意：如果 1.4.x 有下游测试或集成依赖 `current-snapshot-id` 为 `-1` 的旧行为，回迁后这些测试需要同步更新期望值。
  - 该修复对与外部 REST Catalog 实现（如 Tabular、Nessie 等）的互操作性有正面影响，建议优先回迁。
