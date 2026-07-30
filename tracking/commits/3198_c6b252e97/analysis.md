# 提交 3198：Core: Skip unnecessary metadata refresh when producing snapshot event after merge append (#14709)

## 提交信息

- **序号**：3198 / 4088
- **哈希**：c6b252e97eace78cf31df1d72ff91e7e641049da
- **短哈希**：c6b252e97
- **日期**：2026-02-02
- **作者**：gaborkaszab
- **提交说明**：Core: Skip unnecessary metadata refresh when producing snapshot event after merge append (#14709)
- **PR/Issue**：#14709

## 总体目的

该提交优化了 `MergingSnapshotProducer` 在提交后生成 snapshot 事件（`updateEvent()`）时的性能问题。原本在 merge append（如 `MergeOnRead`/`CopyOnWrite` 的 upsert、delete 等合并类操作）提交完成后，`updateEvent()` 会无条件调用 `ops().refresh()` 去重新拉取表元数据，再从中按 `snapshotId` 取出刚保存的 `Snapshot`，用于构造 `CommitReport`/metrics 事件。

问题是：提交刚完成时，`TableOperations` 持有的 `current()` 元数据已经包含了刚提交的快照（提交过程本身就会更新本地元数据）。此时再调用 `refresh()` 会触发一次额外的 `loadTable` REST 请求，对 REST Catalog 而言这是不必要的网络往返，在高频写入场景下造成显著开销与 catalog 服务端压力。

修复策略是"先查本地，缺失再刷新"：先用 `ops().current().snapshot(snapshotId)` 从已加载的元数据中查找快照；只有当本地元数据里找不到该快照时，才回退到 `ops().refresh()` 强制刷新。这样在绝大多数正常提交路径上避免了一次多余的元数据加载。

## 如何达成设计目的

在 `MergingSnapshotProducer.updateEvent()` 中将无条件 `refresh()` 改为"current 优先、refresh 兜底"的两段式查找。测试侧在 `RequestMatcher` 中新增一个支持 `Predicate<Object>` body 匹配的 `matches` 重载，用于精确校验 `ReportMetricsRequest` 中的 `CommitReport` 字段；并在 `TestRESTCatalog` 新增 `testNumLoadTableCallsForMergeAppend`，验证 merge append 后只发生一次 `loadTable`（GET table）调用，且 `CommitReport` 的 `snapshotId`/`sequenceNumber`/`operation`/`addedDataFiles` 与刚提交的快照一致。

## 修改详情

### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java` (+7/-1 lines)

**修改目的**：避免提交后生成事件时不必要的元数据刷新。

**工作逻辑**：
`updateEvent()` 用于在提交成功后产生供 metrics 上报的事件。原代码：

```java
Snapshot justSaved = ops().refresh().snapshot(snapshotId);
```

每次都强制刷新。新代码：

```java
Snapshot justSaved = ops().current().snapshot(snapshotId);
if (justSaved == null) {
  justSaved = ops().refresh().snapshot(snapshotId);
}
```

`ops().current()` 返回当前已持有的 `TableMetadata`，提交刚完成时它已包含新快照，因此通常能直接命中，省去一次 `loadTable`。只有当本地元数据因并发或其他原因未包含该 snapshotId 时，才回退到 `refresh()` 重新拉取，保证正确性兜底。后续对 `sequenceNumber`、`summary` 的处理逻辑不变（`justSaved == null` 时仍走原有降级路径）。

### `core/src/test/java/org/apache/iceberg/rest/RequestMatcher.java` (+16/-0 lines)

**修改目的**：为测试提供按 body 谓词匹配请求的能力。

**工作逻辑**：
新增重载 `matches(HTTPMethod method, String path, Map headers, Map parameters, Predicate<Object> pred)`，使用 `argThat` 组合方法、路径、headers、parameters 的相等性判断，并用 `pred.test(req.body())` 校验请求体。这比原来只能传固定 body 对象的版本更灵活，可在测试里对 `ReportMetricsRequest` 内部字段做断言。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+39/-3 lines)

**修改目的**：验证 merge append 后不再多余 loadTable，且 CommitReport 内容正确。

**工作逻辑**：
新增 `testNumLoadTableCallsForMergeAppend`：用 spy 包装 `RESTCatalogAdapter`，创建表后执行一次 `newAppend().appendFile(FILE_A).commit()`。然后用 `Mockito.verify(adapter).execute(matches(GET, table path), ...)` 断言 GET table（loadTable）只被调用一次，证明 `updateEvent()` 没有再触发额外的 loadTable。再用带 `Predicate` 的 `matches` 断言 POST metrics 请求中的 `ReportMetricsRequest.report()` 是 `CommitReport`，且其 `tableName`、`snapshotId`、`sequenceNumber`（与 `table.currentSnapshot()` 一致）、`operation`（`"append"`）、`addedDataFiles`（== 1）均正确反映刚提交的快照状态。

另外有一处对 `listNamespaces` mock 调用参数的微调：把原来的 `null` body 参数移除并调整括号位置（`), null)` → `))`），使匹配器签名与新写法一致，属配套小修。

## 总结

该优化消除了 merge append 提交后生成 metrics 事件时一次多余的 `loadTable` 调用，降低 REST Catalog 的网络开销与服务端压力，同时通过精确的 mock 验证确保 `CommitReport` 内容依然正确。改动小但对高频写入场景的吞吐与延迟有实际收益，且保留了"本地缺失则刷新"的兜底，兼顾了正确性。
