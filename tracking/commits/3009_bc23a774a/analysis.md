# 提交 3009：Core: Adjust namespace separator in TestRESTCatalog (#14808)

## 提交信息

- **序号**：3009 / 4088
- **哈希**：bc23a774a3cb3b06a63289e445dbf91beb8b5254
- **短哈希**：bc23a774a
- **日期**：2025-12-13 09:09:08 +0100
- **作者**：gaborkaszab
- **提交说明**：Core: Adjust namespace separator in TestRESTCatalog (#14808)
- **PR/Issue**：#14808

## 总体目的

Iceberg REST Catalog 支持可配置的命名空间分隔符（配置键 `RESTCatalogProperties.NAMESPACE_SEPARATOR`）。默认的"legacy"分隔符是 `RESTUtil.NAMESPACE_SEPARATOR_URLENCODED_UTF_8`（即 `%1F`，转义后的单元分隔符），而服务端可通过在 `ConfigResponse` 的 overrides 中下发 `namespace-separator` 来让客户端改用别的分隔符。测试桩 `RESTCatalogAdapter` 就在 `config()` 中通过 `.withOverride(NAMESPACE_SEPARATOR, "%2E")` 通告客户端使用 `%2E`（URL 编码的点号 `.`）作为分隔符，从而在测试链路全程使用非默认分隔符来验证该特性。

问题在于 `TestRESTCatalog` 的测试基线与被测对象不一致：类级别的 `RESOURCE_PATHS` 是用 `ResourcePaths.forCatalogProperties(Maps.newHashMap())`（空属性 Map）构造的，这意味着使用的是默认 legacy 分隔符 `%1F`；而被测的 `RESTCatalog`（经由 adapter）实际被配置成了 `%2E`。对于不含嵌套命名空间的路径（如单层表 `TABLE`），两种分隔符生成的路径相同，所以既有断言碰巧能通过；但一旦涉及多层命名空间，用 legacy `%1F` 构造的预期路径就会与 catalog 实际发出的 `%2E` 路径不符，测试既不能真实覆盖可配置分隔符特性，也埋下了"用错误分隔符做断言"的隐患。此外，测试里还散落着多个局部 `ResourcePaths paths = forCatalogProperties(Maps.newHashMap())` 与一处显式用 `%2E` 的局部变量，口径不统一。

本提交修正这一不一致：把类级别 `RESOURCE_PATHS` 改为用 `%2E`（即 `RESTCatalogAdapter.NAMESPACE_SEPARATOR_URLENCODED_UTF_8`）构造，使其与 adapter 实际下发的 override 对齐；统一各处局部 `paths` 复用该常量；并新增针对嵌套命名空间在"legacy 分隔符（服务端不下发 override）"与"override 分隔符（服务端下发 `%2E`）"两种场景的端到端测试，以及 `TestResourcePaths` 中对两种分隔符编解码的单元测试，从而真正覆盖可配置分隔符特性。

## 如何达成设计目的

整体思路是"对齐基线 + 补覆盖"。先把 `RESTCatalogAdapter` 中原本 `private` 的常量 `NAMESPACE_SEPARATOR_URLENCODED_UTF_8` 用 `@VisibleForTesting` 暴露给测试引用，避免测试里硬编码 `%2E` 字面量。然后在 `TestRESTCatalog` 中把类级别 `RESOURCE_PATHS` 改为显式传入 `%2E` 构造，并把散落的局部 `paths` 替换为复用 `RESOURCE_PATHS`。最后新增两个集成测试分别模拟"服务端不下发分隔符 override"（catalog 回退 legacy `%1F`）和"服务端下发 `%2E` override"两种情形，用一个共享辅助方法 `runConfigurableNamespaceSeparatorTest` 对三层嵌套命名空间验证创建表与列出子命名空间时实际发出的 URL 路径与查询参数所用分隔符正确。配套在 `TestResourcePaths` 中新增两个单元测试，验证 `ResourcePaths` 与 `RESTUtil` 编解码在 legacy/新分隔符下的行为及向后兼容。

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+2/-1 lines)

**修改目的**：把命名空间分隔符常量暴露给测试引用。

**工作逻辑**：
新增 `import ...VisibleForTesting;`，并将 `private static final String NAMESPACE_SEPARATOR_URLENCODED_UTF_8 = "%2E";` 改为 `@VisibleForTesting static final String NAMESPACE_SEPARATOR_URLENCODED_UTF_8 = "%2E";`（去掉 `private` 提升为包级可见）。这样测试侧可直接引用 `RESTCatalogAdapter.NAMESPACE_SEPARATOR_URLENCODED_UTF_8` 而非重复硬编码 `%2E`，保证测试与 adapter 用的是同一个真实常量。该常量本身在 adapter 的 `config()` 中作为 override 下发给客户端。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+136/-16 lines)

**修改目的**：对齐测试基线分隔符并新增嵌套命名空间分隔符的端到端覆盖。

**工作逻辑**：
- **类级别 `RESOURCE_PATHS` 对齐**：由 `ResourcePaths.forCatalogProperties(Maps.newHashMap())`（legacy `%1F`）改为 `ResourcePaths.forCatalogProperties(ImmutableMap.of(RESTCatalogProperties.NAMESPACE_SEPARATOR, RESTCatalogAdapter.NAMESPACE_SEPARATOR_URLENCODED_UTF_8))`（`%2E`），与 adapter 下发的 override 一致。这是修正的核心：测试预期路径现在与被测 catalog 实际发出的路径使用同一分隔符。
- **统一局部 `paths`**：两处 `ResourcePaths paths = ResourcePaths.forCatalogProperties(Maps.newHashMap());`（在加载 refs/snapshots 的测试中）被删除，改为直接复用类级 `RESOURCE_PATHS.table(TABLE)`；另一处原本显式用 `ImmutableMap.of(NAMESPACE_SEPARATOR, "%2E")` 构造的局部 `paths` 也替换为 `RESOURCE_PATHS`。三处口径统一到 `%2E`。
- **新增 `nestedNamespaceWithLegacySeparator`**：用 Mockito spy 一个 `RESTCatalogAdapter`，在处理 `v1/config` 请求时调用真实逻辑拿到 `ConfigResponse`，再从其 overrides 中移除 `NAMESPACE_SEPARATOR`，模拟"服务端不下发分隔符 override"的服务端。随后构造 catalog，并以 `ResourcePaths.forCatalogProperties(ImmutableMap.of())`（legacy）与 `RESTUtil.NAMESPACE_SEPARATOR_URLENCODED_UTF_8`（legacy `%1F`）作为预期，调用共享辅助方法验证。
- **新增 `nestedNamespaceWithOverriddenSeparator`**：同样 spy adapter，但断言 `ConfigResponse.overrides()` 确实包含 `NAMESPACE_SEPARATOR → %2E`（验证 adapter 总是下发该 override），随后以 `RESOURCE_PATHS`（`%2E`）与 `RESTCatalogAdapter.NAMESPACE_SEPARATOR_URLENCODED_UTF_8` 作为预期调用辅助方法。
- **新增辅助方法 `runConfigurableNamespaceSeparatorTest`**：对三层命名空间 `Namespace.of("ns1","ns2","ns3")` 与父命名空间 `Namespace.of("ns1","ns2")`，先 `createNamespace` 再 `createTable`，然后用 `Mockito.verify(adapter).execute(...)` 校验：创建表时 POST 的路径 `expectedPaths.tables(nestedNamespace)` 使用了预期分隔符；列出子命名空间时 GET 的 `parent` 查询参数由 `RESTUtil.namespaceToQueryParam(parentNamespace, expectedSeparator)` 编码，也使用了预期分隔符。这样把"路径中的分隔符"与"查询参数中的分隔符"都覆盖到。

### `core/src/test/java/org/apache/iceberg/rest/TestResourcePaths.java` (+41/-0 lines)

**修改目的**：在 `ResourcePaths`/`RESTUtil` 层面补充两种分隔符下嵌套命名空间编解码的单元覆盖。

**工作逻辑**：
- **`nestedNamespaceWithLegacySeparator`**：对 `Namespace.of("first","second","third")`，用 legacy 分隔符（`RESTUtil.NAMESPACE_SEPARATOR_URLENCODED_UTF_8`，即 `%1F`）构造 `ResourcePaths.forCatalogProperties(ImmutableMap.of())`。验证 `pathsWithLegacySeparator.namespace(namespace)` 包含 `RESTUtil.encodeNamespace(namespace)`（不带分隔符参数，走 legacy 编码）且包含 legacy 分隔符；并验证 `RESTUtil.decodeNamespace(legacyEncodedNamespace)`（不传分隔符，走 legacy 解码）能还原原命名空间，且 `RESTUtil.decodeNamespace(legacyEncodedNamespace, newSeparator)`（传新分隔符 `%2E`）也能还原——这验证了"用新分隔符解码 legacy 编码的命名空间"的向后兼容（解码逻辑内置对旧 `%1F` 的回退识别）。
- **`nestedNamespaceWithNewSeparator`**：用新分隔符 `RESTCatalogAdapter.NAMESPACE_SEPARATOR_URLENCODED_UTF_8`（`%2E`）构造 `ResourcePaths`，验证 `namespace(namespace)` 包含 `RESTUtil.encodeNamespace(namespace, newSeparator)`（带分隔符参数编码）且包含新分隔符；并验证 `RESTUtil.decodeNamespace(newEncodedSeparator, newSeparator)` 能还原。这覆盖了显式使用新分隔符的编解码路径。

## 总结

该提交是 REST Catalog 测试侧的修正与加固：把 `TestRESTCatalog` 中与被测 catalog 实际配置不符的 legacy 分隔符基线对齐为 adapter 下发的 `%2E`，统一散落的局部 `ResourcePaths` 构造，并新增嵌套命名空间在 legacy（无 override 回退）与 override（`%2E`）两种场景下的端到端与单元测试，配套把 adapter 的分隔符常量用 `@VisibleForTesting` 暴露。核心价值在于消除"用错误分隔符做断言"的隐患，使可配置命名空间分隔符特性得到真实、完整的覆盖，保证多层命名空间在路径与查询参数中的编解码行为被正确验证。
