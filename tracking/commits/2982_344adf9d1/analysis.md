# 提交 2982：Core: Make namespace separator configurable (#10877)

## 提交信息

- **序号**：2982 / 4088
- **哈希**：344adf9d123cfb45a14d8d0812c1d299753bee76
- **短哈希**：344adf9d1
- **日期**：2025-12-09
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Make namespace separator configurable (#10877)
- **PR/Issue**：#10877

## 总体目的

Iceberg REST Catalog 协议中，多级命名空间（multipart namespace）在 URL 路径或查询参数中以单段字符串表示，各层级之间用某个分隔符连接。长期以来 Core 模块的 `RESTUtil` 硬编码使用单元分隔符 `\u001f`（URL 编码 `%1F`）作为唯一分隔符。该字符不可打印、不直观，给客户端构造请求、调试、日志阅读带来不便，也限制了部署环境根据自身需要选择更友好分隔符（如 `.`）的能力。

本提交（PR #10877）是序号 2979（OpenAPI 规范侧改动）对应的 Core 实现部分，目的是把命名空间分隔符从硬编码 `\u001f`/`%1F` 改为可通过 catalog 属性 `namespace-separator` 配置，默认仍为 `%1F` 以保持向后兼容。这样服务端可以通告自定义分隔符，客户端（`RESTSessionCatalog`）在初始化时读取该配置，并在编码/解码命名空间（既包括路径变量 `encodeNamespace`/`decodeNamespace`，也包括查询参数 `namespaceToQueryParam`/`namespaceFromQueryParam`）时统一使用配置的分隔符。

实现的一个关键约束是向后兼容：旧的客户端可能仍用 `%1F`/`\u001f` 编码命名空间，新的服务端必须能识别；反之新客户端用自定义分隔符编码，旧服务端只认 `%1F`。因此解码方法在收到字符串时，会先检测其中是否包含旧分隔符，若包含则按旧分隔符拆分，否则按配置的新分隔符拆分，从而在两个方向上都兼容。这与序号 2979 中 OpenAPI 规范描述的"servers must use both the advertised separator and `0x1F` as valid separators when decoding namespaces"一致。

## 如何达成设计目的

整体思路是引入一个可配置的分隔符参数，贯穿 `RESTUtil` 的四个核心方法（`namespaceToQueryParam`/`namespaceFromQueryParam`/`encodeNamespace`/`decodeNamespace`），并在 `RESTSessionCatalog` 初始化时从 catalog 属性读取该配置传给使用方，在 `ResourcePaths` 构造时也接收该配置用于生成 URL 路径。旧的无参版本保留为 `@Deprecated`，内部调用新版本并使用默认 `\u001f`/`%1F`，保证对既有调用方与历史数据的兼容。解码逻辑内置"优先识别旧分隔符"的回退，保证新旧客户端/服务端互通。

涉及文件：`RESTCatalogProperties`（新增配置键）、`RESTSessionCatalog`（读取配置并在 `listNamespaces` 查询参数中使用）、`RESTUtil`（核心编码/解码方法重载与兼容逻辑）、`ResourcePaths`（URL 路径生成使用配置分隔符），以及测试侧的 `RESTCatalogAdapter`（测试桩用 `.`/`%2E` 作为自定义分隔符）、`TestRESTUtil`、`TestResourcePaths`、`TestRESTCatalog`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalogProperties.java` (+2/-0 lines)

**修改目的**：定义新的 catalog 配置键 `namespace-separator`。

**工作逻辑**：
新增 `public static final String NAMESPACE_SEPARATOR = "namespace-separator";`。该键与 `PAGE_SIZE` 等配置项并列，供 `RESTSessionCatalog` 与 `ResourcePaths` 通过 `PropertyUtil.propertyAsString(...)` 读取。默认值在 `RESTUtil.NAMESPACE_SEPARATOR_URLENCODED_UTF_8`（即 `%1F`）中定义。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+7/-1 lines)

**修改目的**：在 catalog 初始化时读取 `namespace-separator` 配置，并在 `listNamespaces` 的 `parent` 查询参数中使用。

**工作逻辑**：
新增字段 `private String namespaceSeparator = null;`。在 `initialize(...)` 中通过 `PropertyUtil.propertyAsString(mergedProps, RESTCatalogProperties.NAMESPACE_SEPARATOR, RESTUtil.NAMESPACE_SEPARATOR_URLENCODED_UTF_8)` 读取配置，缺省为 `%1F`。在 `listNamespaces` 中，把原先的 `queryParams.put("parent", RESTUtil.namespaceToQueryParam(namespace))` 改为 `RESTUtil.namespaceToQueryParam(namespace, namespaceSeparator)`，使查询参数按配置的分隔符编码。

### `core/src/main/java/org/apache/iceberg/rest/RESTUtil.java` (+111/-27 lines)

**修改目的**：核心改动——为四个命名空间编码/解码方法新增接受自定义分隔符的重载，并内置对旧分隔符的向后兼容。

**工作逻辑**：
- 常量重命名以提升可读性：`NAMESPACE_SEPARATOR` → `NAMESPACE_SEPARATOR_AS_UNICODE`（`\u001f`），新增 `NAMESPACE_SEPARATOR_URLENCODED_UTF_8 = "%1F"`（包级可见，供默认值使用）。`NAMESPACE_JOINER`/`NAMESPACE_SPLITTER` 仍保留但改为基于 `NAMESPACE_SEPARATOR_AS_UNICODE`，并标 `@Deprecated`。类上加 `@SuppressWarnings("UnicodeEscape")` 抑制 Unicode 转义告警。

- `namespaceToQueryParam(Namespace)`：保留为 deprecated，内部委托新重载 `namespaceToQueryParam(namespace, String.valueOf(NAMESPACE_SEPARATOR_AS_UNICODE))`（即默认用 unicode 字符 `\u001f`）。

- `namespaceToQueryParam(Namespace, String unicodeNamespaceSeparator)`：校验分隔符非空非 null；用 `URLDecoder.decode(unicodeNamespaceSeparator, UTF_8)` 解码（兼容传入 URL 编码形式），再用 `Joiner.on(separator).join(namespace.levels())` 拼接。注意此处分隔符是 unicode 字符层面（用于 query param），与 `encodeNamespace` 的 URL 编码层面不同。

- `namespaceFromQueryParam(String)`：保留为 deprecated，委托 `namespaceFromQueryParam(namespace, String.valueOf(NAMESPACE_SEPARATOR_AS_UNICODE))`。

- `namespaceFromQueryParam(String, String unicodeNamespaceSeparator)`：校验分隔符；解码分隔符；**关键兼容逻辑**——构造 `Splitter` 时先检测 `namespace` 是否包含旧 unicode 分隔符 `\u001f`，若包含则按 `\u001f` 拆分（兼容旧客户端用 unicode 字符发来的 query param），否则按配置分隔符拆分。这样新服务端既能读懂旧客户端的 `\u001f` query param，也能读懂新客户端的自定义分隔符 query param。

- `encodeNamespace(Namespace)`：标 `@Deprecated`，委托 `encodeNamespace(ns, NAMESPACE_SEPARATOR_URLENCODED_UTF_8)`。

- `encodeNamespace(Namespace, String separator)`：校验命名空间与分隔符非空；对每个 level 调用 `encodeString`（URL 编码），然后用 `Joiner.on(separator).join(...)` 拼接。注释明确 separator 按原样使用、不会再做 UTF-8 编码（即调用方应传入已编码形式如 `%2E`）。

- `decodeNamespace(String)`：标 `@Deprecated`，委托 `decodeNamespace(encodedNs, NAMESPACE_SEPARATOR_URLENCODED_UTF_8)`。

- `decodeNamespace(String, String separator)`：校验非空；**关键兼容逻辑**——构造 `Splitter` 时检测 `encodedNamespace` 是否包含旧分隔符 `%1F`，若包含则按 `%1F` 拆分（兼容旧客户端用 `%1F` 编码的路径变量），否则按配置 separator 拆分。然后对每个 level 调用 `decodeString` 解码。这样新服务端能同时解析旧客户端的 `%1F` 路径与新客户端的自定义分隔符路径。

整体设计确保了"旧客户端 + 新服务端"和"新客户端 + 旧服务端"两个方向都能工作，符合 OpenAPI 规范中"servers must use both the advertised separator and `0x1F`"的要求。

### `core/src/main/java/org/apache/iceberg/rest/ResourcePaths.java` (+33/-6 lines)

**修改目的**：让 URL 路径生成使用配置的命名空间分隔符。

**工作逻辑**：
- `forCatalogProperties(Map)`：除了取 `prefix`，还用 `PropertyUtil.propertyAsString(properties, RESTCatalogProperties.NAMESPACE_SEPARATOR, RESTUtil.NAMESPACE_SEPARATOR_URLENCODED_UTF_8)` 取分隔符，传入新私有构造器。
- 新增私有构造器 `ResourcePaths(String prefix, String namespaceSeparator)`，原公开构造器 `ResourcePaths(String prefix)` 标 `@Deprecated`（注释说 1.12.0 起改为 private），内部委托新构造器并使用默认 `%1F`。
- 新增私有方法 `pathEncode(Namespace ns)` 调用 `RESTUtil.encodeNamespace(ns, namespaceSeparator)`。把 `namespace`/`namespaceProperties`/`tables`/`table`/`register`/`metrics`/`views`/`view` 等所有原来直接调用 `RESTUtil.encodeNamespace(ns)` 的位置统一改为 `pathEncode(ns)`，使所有 URL 路径生成都使用配置的分隔符。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+11/-2 lines)

**修改目的**：让测试桩 `RESTCatalogAdapter` 使用自定义分隔符 `.`/`%2E`，以验证可配置分隔符的端到端工作。

**工作逻辑**：
新增常量 `NAMESPACE_SEPARATOR_UNICODE = "\u002e"`（即 `.`）与 `NAMESPACE_SEPARATOR_URLENCODED_UTF_8 = "%2E"`。在 `config()` 返回的配置中通过 `.withOverride(RESTCatalogProperties.NAMESPACE_SEPARATOR, NAMESPACE_SEPARATOR_URLENCODED_UTF_8)` 通告给客户端，使客户端用 `%2E` 编码路径。`LIST_NAMESPACES` 路径解析 `parent` 查询参数时改用 `RESTUtil.namespaceFromQueryParam(vars.get("parent"), NAMESPACE_SEPARATOR_UNICODE)`；`namespaceFromPathVars` 改用 `RESTUtil.decodeNamespace(pathVars.get("namespace"), NAMESPACE_SEPARATOR_URLENCODED_UTF_8)`。这样测试链路全程使用 `.` 作为分隔符，验证了非默认分隔符的端到端正确性。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+7/-5 lines)

**修改目的**：适配 `RESTCatalogAdapter` 改用 `%2E` 分隔符后的服务端路径校验。

**工作逻辑**：
`loadMetadataTableWithMetadataRefresh` 测试中，原 `TableIdentifier.of(TABLE.namespace().toString(), TABLE.name(), "partitions")` 改为 `TableIdentifier.of(NS.toString(), TABLE.name(), "partitions")`（避免依赖 TABLE 的 namespace 拼接）。在 verify 服务端被调用的路径时，构造一个使用 `%2E` 分隔符的 `ResourcePaths`：`ResourcePaths.forCatalogProperties(ImmutableMap.of(RESTCatalogProperties.NAMESPACE_SEPARATOR, "%2E"))`，然后用 `paths.table(metadataTableIdentifier)` 作为期望路径去校验 `adapterForRESTServer` 收到的请求，并加了注释说明 `RESTCatalogAdapter` 用 `%2E` 作为命名空间分隔符。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTUtil.java` (+66/-13 lines)

**修改目的**：覆盖自定义分隔符下的编码/解码、旧客户端与新服务端的互通、以及分隔符非法入参校验。

**工作逻辑**：
- `testRoundTripUrlEncodeDecodeNamespace` 改为参数化测试 `@ValueSource(strings = {"%1F", "%2D", "%2E", "#", "_"})`，对每个分隔符验证 `encodeNamespace(namespace, separator)` 与 `decodeNamespace(encodedNs, separator)` 的往返一致，并按 separator 动态构造期望编码串。
- 新增 `encodeAsOldClientAndDecodeAsNewServer`：模拟旧客户端用无参 `encodeNamespace`/`namespaceToQueryParam`（即默认 `%1F`/`\u001f`）编码，新服务端用自定义 separator（`%2E`）解码——验证 `decodeNamespace(encoded, "%2E")` 与 `namespaceFromQueryParam(unicode, "%2E")` 都能正确还原 namespace，证明兼容回退逻辑有效。
- 新增 `nullOrEmptyNamespaceSeparator`：对四个新方法的 `null` 与 `""` 入参都断言抛 `IllegalArgumentException("Invalid separator: null or empty")`。

### `core/src/test/java/org/apache/iceberg/rest/TestResourcePaths.java` (+46/-0 lines)

**修改目的**：覆盖 `ResourcePaths` 在自定义分隔符下生成 URL 路径的正确性。

**工作逻辑**：
新增两个参数化测试 `@ValueSource(strings = {"%1F", "%2D", "%2E"})`：
- `testNamespaceWithMultipartNamespace`：对两段命名空间 `Namespace.of("n", "s")`，验证 `forCatalogProperties` 带 `prefix` 与不带 `prefix` 两种情况下 `namespace(ns)` 生成的路径都按传入分隔符拼接（如 `v1/ws/catalog/namespaces/n%ss`）。
- `testNamespaceWithDot`：对含 `.` 的命名空间 `Namespace.of("n.s", "a.b")`，验证分隔符只用于分隔 levels，levels 内部的 `.` 被原样保留（如 `n.s%sa.b`），不会被误拆。

## 总结

该提交是 Iceberg Core 模块对"可配置命名空间分隔符"特性的实现，与序号 2979 的 OpenAPI 规范改动配套。核心价值在于让 REST Catalog 的命名空间分隔符从硬编码 `\u001f`/`%1F` 变为可通过 `namespace-separator` 属性配置（默认仍 `%1F`），提升可读性与部署灵活性。`RESTUtil` 的四个核心方法新增带分隔符参数的重载，解码逻辑内置"优先识别旧 `%1F`/`\u001f`"的回退，保证新旧客户端/服务端双向兼容；`RESTSessionCatalog` 与 `ResourcePaths` 在初始化时读取配置并贯穿到 URL 生成与查询参数编码。测试侧用 `.`/`%2E` 作为非默认分隔符做了端到端验证，并专门覆盖了旧客户端 + 新服务端的互通场景与非法入参校验。
