# 提交 3669：OpenAPI, Core: Disambiguate the intent of REFS snapshot mode (#16252)

## 提交信息

- **序号**：3669 / 4088
- **哈希**：299f7be3987c87c8812234f85131d594b5553db6
- **短哈希**：299f7be39
- **日期**：2026-05-08 10:29:29 -0700
- **作者**：gaborkaszab
- **提交说明**：OpenAPI, Core: Disambiguate the intent of REFS snapshot mode (#16252)
- **PR/Issue**：#16252

## 总体目的

这个提交澄清了 REST Catalog OpenAPI 规范中 `snapshots=refs` 参数的含义，明确它只影响 `LoadTableResponse` 中的 `snapshots` 字段，而不影响 `snapshot-log` 字段。

REST Catalog 的 `GET /v1/{prefix}/namespaces/{namespace}/tables/{table}` 端点支持 `snapshots` 查询参数，取值为 `all`（返回所有有效快照）或 `refs`（仅返回被分支或标签引用的快照）。此前的规范描述较为模糊，一些 REST 实现错误地认为 `refs` 模式也会过滤 `snapshot-log`（快照日志），导致返回不完整的 snapshot-log。

本提交在规范描述中明确指出 `refs` 模式作用于 `snapshots` 字段（"via the `snapshots` field"），并在测试中验证无论是否使用 `refs` 模式，`snapshot-log` 都应完整返回。

## 如何达成设计目的

1. 在 `open-api/rest-catalog-open-api.yaml` 中更新 `snapshots` 参数的描述，明确其作用于 `snapshots` 字段。
2. 在 `TestRESTCatalog` 的两个已有测试中新增断言，验证使用 `refs` 模式时 `snapshot-log` 仍完整返回。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+3/-3 lines)

**修改目的**：澄清 `snapshots` 参数作用于 `snapshots` 字段。

**工作逻辑**：
```yaml
# 旧
description:
  The snapshots to return in the body of the metadata. Setting the value to `all` would
  return the full set of snapshots currently valid for the table. Setting the value to
  `refs` would load all snapshots referenced by branches or tags.
# 新
description:
  The snapshots to return in the body of the metadata via the `snapshots` field. Setting
  the value to `all` would return the full set of snapshots currently valid for the table.
  Setting the value to `refs` would load all snapshots referenced by branches or tags.
```
关键变化：新增 "via the `snapshots` field"，明确不影响 `snapshot-log`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+25 lines)

**修改目的**：验证 REFS 模式下 snapshot-log 完整返回。

**工作逻辑**：在两个使用 `refs` 模式加载表的测试中新增断言：
```java
// snapshot log is complete regardless REFS mode
assertThat(((BaseTable) refsTable).operations().current())
    .extracting("snapshotLog")
    .asInstanceOf(InstanceOfAssertFactories.list(HistoryEntry.class))
    .hasSize(2)  // 或 1，取决于测试场景
    .containsExactlyInAnyOrderElementsOf(
        ((BaseTable) table).operations().current().snapshotLog());
```
验证 refs 模式下表的 snapshot-log 与全量加载的 snapshot-log 完全一致。

## 总结

这个提交澄清了 REST Catalog 规范中 `snapshots=refs` 参数的语义，明确其只过滤 `snapshots` 字段而不影响 `snapshot-log`。通过更新 OpenAPI 描述和新增测试断言，防止实现者误解该参数的作用范围。这是一个规范明确性改进，解决了部分 REST 实现中 snapshot-log 被错误过滤的问题。
