# 提交 3304：Core: Add test for freshness-aware table loading with lazy snapshot loading (#15274)

## 提交信息

- **序号**：3304 / 4088
- **哈希**：f79932d112b77dc4e49c21c3501c7b722d129462
- **短哈希**：f79932d11
- **日期**：2026-02-23
- **作者**：gaborkaszab
- **提交说明**：Core: Add test for freshness-aware table loading with lazy snapshot loading (#15274)
- **PR/Issue**：#15274

## 总体目的

该提交为 Iceberg REST Catalog 的两个关键特性——新鲜度感知表加载（freshness-aware table loading）和惰性快照加载（lazy snapshot loading）——的交互行为新增了端到端测试。这两个特性分别解决不同的问题，但它们的交互方式此前缺乏测试覆盖。

**新鲜度感知表加载**：REST Catalog 在加载表时使用 ETag 机制实现条件请求。首次加载表时，服务端返回 ETag；后续加载时客户端携带 `If-None-Match` 请求头，若服务端判断表未变化则返回 304 Not Modified，客户端直接使用缓存的表元数据，避免重复传输完整的表元数据。这与 HTTP 缓存语义一致，能显著降低 REST Catalog 的网络开销和加载延迟。

**惰性快照加载**：当 `SNAPSHOT_LOADING_MODE` 设为 `REFS` 时，REST Catalog 在加载表时仅请求当前快照引用（`snapshots=refs`），而非全部快照详情。表的完整快照列表在首次访问 `table.snapshots()` 时才通过 `snapshots=all` 请求惰性加载。这对历史快照众多的大表尤其重要，可避免加载大量不需要的历史快照数据。

本提交要验证的核心问题是：当惰性快照加载触发完整快照列表加载时，缓存的表条目行为是否正确——具体而言，惰性加载是否更新了缓存中表对象的底层 `TableMetadata`、缓存条目对象引用是否保持不变、缓存命中计数是否正确、以及后续的 `loadTable` 是否能从缓存返回已加载完整快照的表。这些交互细节如果存在 bug，可能导致用户在 refs 模式下看到不完整的快照列表，或缓存失效逻辑出错。

## 如何达成设计目的

通过在 `TestFreshnessAwareLoading` 测试类中新增 `tableCacheWithLazySnapshotLoading` 测试方法，使用一个能捕获响应头的 spy adapter 构造 REST Catalog，配置为 refs 模式，创建表并提交两个快照，然后分阶段验证加载行为和缓存状态，并使用 Mockito 验证底层 HTTP 请求的参数和次数。

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/TestFreshnessAwareLoading.java` (+98/-0 lines)

**修改目的**：验证 refs 模式下新鲜度感知缓存与惰性快照加载的交互。

**工作逻辑**：
新增 `tableCacheWithLazySnapshotLoading` 测试方法，分阶段验证：

1. **初始化**：用 `adapterCapturingResponseHeaders` 创建捕获响应头的 spy adapter，构造 `RESTCatalog` 并初始化为 `SnapshotMode.REFS` 模式。创建表后提交两次 append（FILE_A、FILE_B），生成两个快照。

2. **首次加载（refs 模式）**：`loadTable` 触发首次请求，验证响应中 ETag 非空。直接访问 `BaseTable` 底层 `TableMetadata` 的 `snapshots` 字段（绕过惰性加载触发），断言仅含 1 个快照（当前快照），证明 refs 模式确实只加载了当前快照。

3. **缓存状态验证**：从 `RESTTableCache` 获取底层 Caffeine 缓存，断言缓存大小为 1 且对应 key 的 `TableWithETag` 条目存在。

4. **触发惰性加载**：调用 `refsTable.snapshots()`，断言返回 2 个快照，证明 `snapshots=all` 请求被触发并加载了全部快照。关键验证：缓存大小仍为 1、缓存条目对象引用不变（`isSameAs`）、缓存命中计数为 0（惰性加载不经过缓存）。这说明惰性加载是原地刷新 `TableMetadata`，而非创建新的缓存条目。

5. **缓存命中验证**：再次 `loadTable`，断言缓存命中计数变为 1，且返回的表已包含全部 2 个快照。这证明惰性加载刷新后的元数据对后续缓存命中可见。

6. **幂等性验证**：再次访问 `snapshots()` 仍为 2，不触发额外加载。

7. **请求验证（Mockito verify）**：通过 `verify(adapter, times(1))` 精确断言三类请求各发生一次：
   - 首次加载：GET 请求，无 `If-None-Match` 头，查询参数 `snapshots=refs`；
   - 第二次加载（缓存命中）：GET 请求，携带 `If-None-Match: <eTag>` 头，查询参数 `snapshots=refs`；
   - 惰性快照加载：GET 请求，无 `If-None-Match` 头，查询参数 `snapshots=all`。

此外新增了 `FILE_B`、`Snapshot`、`InstanceOfAssertFactories` 的导入以支持测试逻辑。`InstanceOfAssertFactories.list(Snapshot.class)` 用于将 `snapshots` 字段作为 `List<Snapshot>` 进行断言，这是 AssertJ 的类型安全断言工具。

## 总结

本次提交新增了 refs 模式下新鲜度感知缓存与惰性快照加载交互的端到端测试，精确验证了惰性加载原地刷新元数据、不替换缓存条目、不影响缓存命中计数等关键行为，并通过 Mockito 严格校验了三类 HTTP 请求的参数与次数。该测试填补了两个重要特性交互场景的测试空白，对保障 REST Catalog 在大表多快照场景下的正确性和性能优化可靠性具有重要价值。
