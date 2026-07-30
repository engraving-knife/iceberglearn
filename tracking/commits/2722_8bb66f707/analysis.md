# 提交 2722：Core: Deprecate Namespace Joiner/Splitter and use separate methods

## 提交信息

- **序号**：2722 / 4088
- **哈希**：8bb66f7071cacfb69f37d72807836b5a841df0ea
- **短哈希**：8bb66f707
- **日期**：2025-10-08 08:20:35 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Deprecate Namespace Joiner/Splitter and use separate methods
- **PR/Issue**：#14274

## 总体目的

在 Iceberg REST Catalog 中，Namespace（命名空间）的多级名称在作为 HTTP 查询参数传输时，需要使用一个分隔符将各级名称连接成一个字符串。Iceberg 使用 Unicode 字符 `\u001f`（Unit Separator）作为分隔符。

此前，这个连接和拆分操作通过 `RESTUtil` 中公开的 `NAMESPACE_JOINER` 和 `NAMESPACE_SPLITTER` 常量直接使用。这种方式存在两个问题：一是调用方直接操作 Joiner/Splitter 对象，无法统一控制行为；二是社区未来计划让命名空间分隔符变为可配置的，如果调用方直接使用 Joiner/Splitter，就无法在中间插入配置逻辑。

此提交回顾了之前 PR #10858 的尝试（该 PR 被 #11574 回退），采用了新的方法：将 Joiner/Splitter 标记为废弃，并引入 `namespaceToQueryParam()` 和 `namespaceFromQueryParam()` 两个专门的方法来处理命名空间的连接和拆分。这样所有命名空间的序列化/反序列化都通过统一的方法进行，为将来使分隔符可配置奠定基础。

## 如何达成设计目的

主要设计思路：
1. 在 `RESTUtil` 中新增 `namespaceToQueryParam(Namespace)` 和 `namespaceFromQueryParam(String)` 方法，封装命名空间到查询参数字符串的转换逻辑
2. 将 `NAMESPACE_JOINER` 和 `NAMESPACE_SPLITTER` 标记为 `@Deprecated`（since 1.11.0，will be removed in 1.12.0）
3. 将调用方从直接使用 Joiner/Splitter 改为使用新方法
4. 添加测试覆盖新方法的各种边界情况

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+1/-1 lines)

**修改目的**：将命名空间查询参数的生成从直接使用 Joiner 改为调用新方法。

**工作逻辑**：将 `RESTUtil.NAMESPACE_JOINER.join(namespace.levels())` 替换为 `RESTUtil.namespaceToQueryParam(namespace)`，用于构造列出命名空间的 REST 请求中的 `parent` 查询参数。

### `core/src/main/java/org/apache/iceberg/rest/RESTUtil.java` (+49/-2 lines)

**修改目的**：添加废弃标记和新方法，封装命名空间的序列化逻辑。

**工作逻辑**：
1. **废弃标记**：为 `NAMESPACE_JOINER` 和 `NAMESPACE_SPLITTER` 添加 `@Deprecated` 注解，注明 since 1.11.0、removed in 1.12.0，并指向替代方法。
2. **`namespaceToQueryParam(Namespace)`**：将 Namespace 转换为查询参数字符串。接收 Namespace 对象，使用 `\u001f` 连接各级名称。注意此方法与 `encodeNamespace()` 不同，后者使用 UTF-8 编码后的 `%1F`。包含 null 检查。
3. **`namespaceFromQueryParam(String)`**：将查询参数字符串转换回 Namespace。使用 `\u001f` 拆分字符串并构建 Namespace 实例。包含 null 检查。文档中特别说明，如果传入的是 UTF-8 编码后的 `%1F`（而非原始 `\u001f`），会产生错误的 Namespace（因为 `%1F` 不会被识别为分隔符）。

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+2/-4 lines)

**修改目的**：将测试辅助类中的命名空间解析改为使用新方法。

**工作逻辑**：将原来通过 `RESTUtil.NAMESPACE_SPLITTER.splitToStream(...).toArray(String[]::new)` 构建 Namespace 的代码替换为 `RESTUtil.namespaceFromQueryParam(vars.get("parent"))`，简化了代码并使用了新的封装方法。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTUtil.java` (+46/-0 lines)

**修改目的**：为新方法添加全面的测试覆盖。

**工作逻辑**：
1. **`namespaceToQueryParam` 测试**：
   - null 输入抛出 IllegalArgumentException
   - 空命名空间返回空字符串
   - 单级命名空间返回名称本身
   - 多级命名空间使用 `\u001f` 连接，且与 `%1F` 编码版本不同
   - 包含点号的命名空间级别不会被错误分割
2. **`namespaceFromQueryParam` 测试**：
   - null 输入抛出 IllegalArgumentException
   - 空字符串返回 `Namespace.of("")`
   - 单级和多变命名空间正确解析
   - 验证传入 `%1F` 编码版本会产生错误的 Namespace（这是一个重要的一致性验证）

## 总结

此提交为命名空间分隔符的可配置化奠定了基础，通过引入专门的 `namespaceToQueryParam` 和 `namespaceFromQueryParam` 方法，废弃了直接暴露 Joiner/Splitter 的做法。这使得所有命名空间的序列化/反序列化都经过统一的方法，未来可以在此处插入可配置的分隔符逻辑。此前 PR #10858 曾尝试类似改动但被回退，此次提交采用了更稳妥的方式（保留废弃的常量而非直接删除），确保向后兼容性。
