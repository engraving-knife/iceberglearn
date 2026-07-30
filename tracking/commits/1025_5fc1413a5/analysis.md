# 提交 1025：Core: Use encoding/decoding methods for namespaces and deprecate Splitter/Joiner (#10858)

## 提交信息

- **序号**：1025 / 4088
- **哈希**：5fc1413a5efc4419ccc081f3031325f107ccddab
- **短哈希**：5fc1413a5
- **日期**：2024-08-05 14:35:07 +0200
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Core: Use encoding/decoding methods for namespaces and deprecate Splitter/Joiner (#10858)
- **PR/Issue**：#10858

## 总体目的

Iceberg 的 REST 目录协议在 URL 查询参数中编码命名空间（Namespace）时，使用 `%1F`（即 ASCII Unit Separator 字符 `\u001f` 的百分号编码形式）作为层级分隔符。原本 `RESTUtil` 类同时暴露了两套工具：基于原始字符 `\u001f` 的 `NAMESPACE_JOINER`/`NAMESPACE_SPLITTER`（未做百分号编码），以及基于 `%1F` 的 `NAMESPACE_ESCAPED_JOINER`/`NAMESPACE_ESCAPED_SPLITTER`（已做百分号编码，用于实际传输）。

问题在于：调用方在构造 REST 请求 URL 时，如果直接使用未转义的 `NAMESPACE_JOINER`（基于 `\u001f`）来拼接命名空间层级，会产生包含不可见控制字符的查询参数，这在 URL 传输、日志记录、服务端解析时都存在隐患。正确做法是统一使用已经过百分号编码的转义分隔符。

本提交的目标是：(1) 在 `RESTSessionCatalog` 中将命名空间编码从直接使用 `NAMESPACE_JOINER` 改为调用正确的 `RESTUtil.encodeNamespace` 方法；(2) 将原来基于原始字符 `\u001f` 的 `NAMESPACE_JOINER`/`NAMESPACE_SPLITTER` 废弃（标记 `@Deprecated`），使其指向正确的转义版本，引导调用方迁移到 `encodeNamespace`/`decodeNamespace` 方法，并计划在 1.8.0 将其改为 private。

## 如何达成设计目的

1. 在 `RESTUtil` 中将 `NAMESPACE_JOINER` 和 `NAMESPACE_SPLITTER` 的分隔符从 `\u001f` 改为 `%1F`（与已有的 `NAMESPACE_ESCAPED_*` 一致），并加上 `@Deprecated` 注解和 Javadoc，指向 `encodeNamespace`/`decodeNamespace` 作为替代。
2. 在 `RESTSessionCatalog.listNamespaces` 中，把 `RESTUtil.NAMESPACE_JOINER.join(namespace.levels())` 替换为 `RESTUtil.encodeNamespace(namespace)`，确保查询参数 `parent` 使用正确转义的命名空间。
3. 在测试辅助类 `RESTCatalogAdapter` 中，把手工使用 `NAMESPACE_SPLITTER.splitToStream` 解析命名空间的逻辑替换为 `RESTUtil.decodeNamespace`，保持编解码对称。

由于废弃常量的分隔符被改为 `%1F`，与转义版本一致，因此即使有外部代码仍使用废弃常量，行为也与正确编码一致，只是会收到废弃警告，降低了迁移风险。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java`

**修改目的**：在列出命名空间时使用正确的命名空间编码方法，避免产生未转义的控制字符。

**工作逻辑**：`listNamespaces` 方法在 `namespace` 非空时，需要将命名空间作为 `parent` 查询参数传递给 REST 接口。原本使用 `RESTUtil.NAMESPACE_JOINER.join(namespace.levels())`（基于 `\u001f`），改为 `RESTUtil.encodeNamespace(namespace)`（基于 `%1F` 百分号编码）。这确保查询参数中只包含可安全传输的 ASCII 字符。

### `core/src/main/java/org/apache/iceberg/rest/RESTUtil.java`

**修改目的**：废弃基于原始字符的 `NAMESPACE_JOINER`/`NAMESPACE_SPLITTER`，使其指向转义版本，引导调用方使用 `encodeNamespace`/`decodeNamespace`。

**工作逻辑**：

1. 删除 `private static final char NAMESPACE_SEPARATOR = '\u001f';` 常量。
2. 将 `NAMESPACE_JOINER` 和 `NAMESPACE_SPLITTER` 的分隔符从 `NAMESPACE_SEPARATOR`（`\u001f`）改为 `NAMESPACE_ESCAPED_SEPARATOR`（`%1F`），使废弃常量的行为与转义版本一致。
3. 为 `NAMESPACE_JOINER` 和 `NAMESPACE_SPLITTER` 添加 `@Deprecated` 注解和 Javadoc，说明"since 1.7.0, will be made private in 1.8.0; use `encodeNamespace(Namespace)` / `decodeNamespace(String)` instead"。

这样即使外部调用方仍在使用废弃常量，由于分隔符已统一为 `%1F`，编码结果是正确的，只是会收到编译废弃警告。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java`

**修改目的**：测试辅助类中使用正确的解码方法替代手工 Splitter 解析。

**工作逻辑**：在 `RESTCatalogAdapter` 解析 URL 变量中的 `parent` 参数时，原本使用手工 `NAMESPACE_SPLITTER.splitToStream` 解析为字符串数组再构造 `Namespace`，改为直接调用 `RESTUtil.decodeNamespace(vars.get("parent"))`，使用统一的 `decodeNamespace` 方法，确保编解码逻辑对称、可维护。

## 小结

- **成效**：修复了 `RESTSessionCatalog` 中命名空间编码使用未转义控制字符的隐患，统一了 REST 协议中命名空间的编解码路径，并通过废弃注解引导外部调用方迁移到 `encodeNamespace`/`decodeNamespace` 方法，为后续将这些常量设为 private 做准备。
- **影响范围**：涉及 `core` 模块的 `RESTSessionCatalog`、`RESTUtil` 和测试辅助类 `RESTCatalogAdapter`，共 3 个文件。属于 REST Catalog 相关的编解码行为修正。
- **回迁到 1.4.x 的注意事项**：这是一个 bug 修复（未转义控制字符可能导致 REST 客户端与服务端交互问题），适合回迁。回迁时需注意：(1) 确认 1.4.x 分支的 `RESTUtil` 已有 `encodeNamespace`/`decodeNamespace` 方法（若无则需一并回迁引入这些方法的提交）；(2) 废弃注解的版本号（1.7.0/1.8.0）在 1.4.x 中可保留或调整为适合 1.4.x 的版本；(3) 行为变化：废弃常量分隔符从 `\u001f` 变为 `%1F`，若有外部代码依赖原分隔符字符会受影响，但这是正确行为，应予回迁。
