# 提交 3231：Core: Include query params into ETag calculation in reference IRC (#15057)

## 提交信息

- **序号**：3231 / 4088
- **哈希**：d23b0d39bfd0a1f914aaa6d24d65906fc99200a3
- **短哈希**：d23b0d39b
- **日期**：2026-02-10
- **作者**：gaborkaszab
- **提交说明**：Core: Include query params into ETag calculation in reference IRC (#15057)
- **PR/Issue**：#15057

## 总体目的

本提交修复了 REST Catalog 中 ETag 计算未包含查询参数（query parameters）的问题。在 Iceberg 的 REST Catalog 实现中，ETag 用于 HTTP 缓存验证——客户端通过 `If-None-Match` 头携带上次获取的 ETag，服务器比较 ETag 后决定返回完整响应还是 304 Not Modified（表示内容未变）。此前 ETag 仅基于表的 metadata location 计算，不考虑请求时的查询参数。

然而，加载表时可以通过 `snapshots` 查询参数指定 snapshot loading mode（`ALL` 加载全部快照，`REFS` 仅加载快照引用）。不同 loading mode 返回的表元数据内容不同：`ALL` 模式返回完整的快照列表，`REFS` 模式仅返回引用。如果两个客户端使用不同的 loading mode 但 metadata location 相同，它们会获得相同的 ETag，导致 HTTP 缓存语义被破坏——一个使用 `ALL` 模式的客户端可能因为 ETag 匹配而收到 304 Not Modified，但实际缓存的是 `REFS` 模式的部分数据，反之亦然。这会导致表数据不完整或缓存不一致的问题。

本提交将查询参数纳入 ETag 计算，确保不同查询参数产生不同的 ETag，从而正确区分不同 loading mode 下的表加载结果。同时引入 `SNAPSHOTS_QUERY_PARAMETER` 常量替代硬编码字符串 `"snapshots"`，提升代码可维护性。

## 如何达成设计目的

修改 `ETagProvider.of()` 方法签名，新增 `Map<String, String> params` 参数。当 params 非空时，使用 `TreeMap` 对参数排序（确保参数顺序不影响 ETag），将 metadata location 和排序后的参数拼接后进行 Murmur3 哈希。在 `RESTCatalogAdapter`（测试用 HTTP 适配器）中，ETag 生成调用更新为传入查询参数或默认查询参数。新增 `SNAPSHOTS_QUERY_PARAMETER` 常量统一管理查询参数名。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ETagProvider.java` (+17/-4 lines)

**修改目的**：将查询参数纳入 ETag 哈希计算。

**工作逻辑**：
`of()` 方法签名从 `of(String metadataLocation)` 改为 `of(String metadataLocation, Map<String, String> params)`。当 params 非 null 且非空时，使用 `TreeMap` 对参数按 key 排序（保证不同参数顺序产生相同 ETag），然后用 `Joiner.on(",").withKeyValueSeparator("=")` 将参数拼接为 `key1=value1,key2=value2` 格式，再与 metadata location 用逗号拼接后进行 Murmur3 哈希。当 params 为 null 或空时，行为与之前一致（仅哈希 metadata location），保证向后兼容。

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogProperties.java` (+1/-0 lines)

**修改目的**：新增 snapshots 查询参数名常量。

**工作逻辑**：
新增 `SNAPSHOTS_QUERY_PARAMETER = "snapshots"` 常量，替代此前在 `RESTSessionCatalog` 和 `RESTCatalogAdapter` 中硬编码的字符串 `"snapshots"`。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+3/-1 lines)

**修改目的**：使用新常量替代硬编码字符串。

**工作逻辑**：
`snapshotModeToParam` 方法中 `"snapshots"` 替换为 `RESTCatalogProperties.SNAPSHOTS_QUERY_PARAMETER`。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+21/-6 lines)

**修改目的**：在测试适配器中更新 ETag 生成以包含查询参数。

**工作逻辑**：
新增 `defaultQueryParams()` 私有静态方法返回默认查询参数 `Map.of(SNAPSHOTS_QUERY_PARAMETER, "all")`。在创建表（createTable）、更新表（updateTable/commitTable）等不携带查询参数的场景中，ETag 使用 `defaultQueryParams()`。在加载表（loadTable）场景中，ETag 使用 `httpRequest.queryParameters()`——即客户端实际发送的查询参数。`snapshotModeFromQueryParams` 方法也使用新常量。

### `core/src/test/java/org/apache/iceberg/rest/TestETagProvider.java` (+29/-8 lines)

**修改目的**：验证包含查询参数的 ETag 计算正确性。

**工作逻辑**：
所有测试方法调用更新为传入 params 参数。`testETagContent` 验证不同参数值产生不同 ETag，以及 null/空 params 仅基于路径计算 ETag。新增 `testDifferentParameterOrderGiveSameETag` 验证参数顺序不影响 ETag（`{param1:value1, param2:value2}` 与 `{param2:value2, param1:value1}` 产生相同 ETag）。

### `core/src/test/java/org/apache/iceberg/rest/TestFreshnessAwareLoading.java` (+77/-12 lines)

**修改目的**：验证不同 snapshot loading mode 产生不同 ETag。

**工作逻辑**：
新增 `differentETagForDifferentSnapshotMode` 测试：使用 REFS 模式创建表和加载表，验证两次操作的 ETag 不同（因为 createTable 使用默认 ALL 参数，loadTable 使用 REFS 参数），并验证 loadTable 确实发送了 `snapshots=refs` 查询参数。`notModifiedResponse` 测试重构为从 createTable 响应中捕获 ETag（而非从 loadTable 后的表操作中获取），确保 ETag 来源正确。`adapterCapturingResponseHeaders` 方法重构为同时将响应头传递给原始 consumer 和测试用 map。

## 总结

本提交通过将查询参数纳入 ETag 计算，修复了不同 snapshot loading mode 下 ETag 相同导致的缓存不一致问题。使用 `TreeMap` 排序确保参数顺序不影响 ETag，同时引入常量提升代码质量。这一修复对于 REST Catalog 的正确缓存验证至关重要，确保客户端不会因 ETag 误匹配而获取到不完整的表元数据。
