# 提交 2701：Core: Implement refs snapshot mode in reference IRC (#14060)

## 提交信息

- **序号**：2701 / 4088
- **哈希**：6ee8252be487fd0e5242ce50cd443e47f347135c
- **短哈希**：6ee8252be
- **日期**：2025-09-29 08:55:34 -0700
- **作者**：gaborkaszab
- **提交说明**：Core: Implement refs snapshot mode in reference IRC (#14060)
- **PR/Issue**：#14060

## 总体目的

本提交增强 Iceberg 内置的参考 REST Catalog 实现（reference IRC，即 `CatalogHandlers` + `RESTCatalogAdapter`，用于测试与作为 REST 服务端参考实现），使其支持 REST 规范中 `loadTable` 端点的 `snapshots` 查询参数的 `refs` 取值，从而能够返回部分快照列表（仅包含 refs 引用的快照），而非全部历史快照。

REST Catalog 规范定义 `loadTable` 端点支持 `snapshots` 参数，取值 `all`（加载全部快照）或 `refs`（仅加载被 refs 引用的快照，即当前快照与各 branch/tag 引用的快照）。`refs` 模式对于拥有大量历史快照的大表很有价值：客户端首次加载表时只需获取被引用的快照，后续按需加载其它快照，减少首屏负载。

此前参考 IRC 实现忽略了 `snapshots` 参数，始终返回全部快照（等价于 `all`）。这导致：
1. 参考实现与规范行为不完全一致；
2. 以参考 IRC 为后端的测试无法真正验证客户端的 `refs` 加载流程——测试不得不通过 Mockito mock `adapter.execute` 来人为构造 `refs` 响应（在响应中手动调用 `TableMetadata.suppressHistoricalSnapshots()`），而非让服务端真正处理该参数。

本提交让 `CatalogHandlers.loadTable` 接受 `SnapshotMode` 参数并在 `REFS` 模式下调用 `TableMetadata.buildFrom(...).suppressHistoricalSnapshots()` 抑制历史快照，同时让 `RESTCatalogAdapter` 在 `LOAD_TABLE` 路由时从查询参数解析 `snapshots` 并传入。测试因此可以移除繁琐的 mock，直接验证端到端的 `refs` 加载。

## 如何达成设计目的

1. **`CatalogHandlers.loadTable` 新增重载**：保留原 `loadTable(Catalog, TableIdentifier)` 并标记 `@Deprecated`（自 1.11.0，1.12.0 移除），内部委托给新方法 `loadTable(Catalog, TableIdentifier, SnapshotMode)` 并默认 `ALL`。新方法根据 `mode`：
   - `ALL`：直接使用 `BaseTable` 当前 `TableMetadata`；
   - `REFS`：用 `TableMetadata.buildFrom(loadedMetadata).withMetadataLocation(...).suppressHistoricalSnapshots().build()` 构造一份抑制了历史快照的元数据副本；
   - 其它：抛 `IllegalArgumentException`。

2. **`RESTCatalogAdapter` 解析查询参数**：在 `LOAD_TABLE` 路由处，调用新增的 `snapshotModeFromQueryParams(httpRequest.queryParameters())` 从 `snapshots` 参数解析 `SnapshotMode`（缺省取 `SNAPSHOT_LOADING_MODE_DEFAULT` 即 `ALL`，值转大写后 `SnapshotMode.valueOf`），传入 `CatalogHandlers.loadTable`。

3. **测试简化**：`TestRESTCatalog` 中三处原本用 Mockito `doAnswer` mock `adapter.execute` 来人为构造 `refs` 响应的代码块被移除，改为直接 `catalog.loadTable(TABLE)` 然后断言 `((BaseTable) refsTable).operations().current()` 的 `snapshots` 字段大小、`currentSnapshot()`、`snapshots()`、`history()` 等。因为现在服务端真正处理了 `refs` 参数，无需客户端侧 mock。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+33/-2 lines)

**修改目的**：让 `loadTable` 支持 `SnapshotMode`，在 `REFS` 模式下抑制历史快照。

**工作逻辑**：
- 原 `loadTable(Catalog, TableIdentifier)` 标记 `@Deprecated`（注释说明自 1.11.0 起，1.12.0 移除，改用带 `SnapshotMode` 的重载），内部调用 `loadTable(catalog, ident, SnapshotMode.ALL)`。
- 新增 `loadTable(Catalog, TableIdentifier, SnapshotMode mode)`：加载 `BaseTable` 后取 `loadedMetadata`；按 `mode` 分支——`ALL` 直接用 `loadedMetadata`；`REFS` 用 `TableMetadata.buildFrom(loadedMetadata).withMetadataLocation(loadedMetadata.metadataFileLocation()).suppressHistoricalSnapshots().build()` 生成抑制历史快照的副本（`withMetadataLocation` 保留原元数据文件位置，`suppressHistoricalSnapshots` 仅保留被 refs 引用的快照）；`default` 抛 `IllegalArgumentException`。最终用 `LoadTableResponse.builder().withTableMetadata(metadata).build()` 返回。`BaseMetadataTable` 分支行为不变（抛 `NoSuchTableException`）。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+12/-1 lines)

**修改目的**：在 `LOAD_TABLE` 路由解析 `snapshots` 查询参数并传入 `CatalogHandlers.loadTable`。

**工作逻辑**：
- 新增 `import java.util.Locale` 与 `import org.apache.iceberg.rest.RESTCatalogProperties.SnapshotMode`。
- `LOAD_TABLE` 分支原 `CatalogHandlers.loadTable(catalog, tableIdentFromPathVars(vars))` 改为 `CatalogHandlers.loadTable(catalog, tableIdentFromPathVars(vars), snapshotModeFromQueryParams(httpRequest.queryParameters()))`。
- 新增私有静态方法 `snapshotModeFromQueryParams(Map<String,String> queryParams)`：从 `snapshots` 键取值，缺省 `RESTCatalogProperties.SNAPSHOT_LOADING_MODE_DEFAULT`，转大写（`Locale.US`）后 `SnapshotMode.valueOf`。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+18/-101 lines)

**修改目的**：移除手动 mock `refs` 响应的代码，改为直接验证服务端真实 `refs` 行为。

**工作逻辑**：三处测试方法（覆盖单快照、branch+main 双快照、多快照场景）中，删除 `refsAnswer` 的 `Answer<?>` 定义、`Mockito.doAnswer(...).when(adapter).execute(...)` mock 设置块；改为直接 `Table refsTable = catalog.loadTable(TABLE);`，然后断言 `((BaseTable) refsTable).operations().current()` 的 `snapshots` 字段大小符合预期（1 或 2）、`refsTable.currentSnapshot()` 与原表一致、`refsTable.snapshots()`/`history()` 大小正确。`verify(adapter, times(1)).execute(...)` 的请求匹配器中仍带 `Map.of("snapshots", "refs")`，验证客户端确实发出了 `refs` 请求。同时移除不再使用的 `import org.apache.iceberg.TableMetadata`。

## 总结

本提交让 Iceberg 参考 REST Catalog 实现真正支持 `loadTable` 的 `snapshots=refs` 模式：`CatalogHandlers.loadTable` 新增 `SnapshotMode` 重载并在 `REFS` 模式下通过 `suppressHistoricalSnapshots` 抑制历史快照，`RESTCatalogAdapter` 从查询参数解析模式并传入。这使参考实现与 REST 规范对齐，并简化了 `TestRESTCatalog` 中原本需要 Mockito mock 才能验证 `refs` 流程的测试代码，改为端到端验证。原 `loadTable` 重载标记 `@Deprecated` 以引导调用方迁移。
