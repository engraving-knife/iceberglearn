# 提交 2477：Core: Deprecate unused methods in OAuth2Util (#13767)

## 提交信息

- **序号**：2477 / 4088
- **哈希**：de93196ab5eb0ddb9d00189fb8f10072927350a3
- **短哈希**：de93196ab
- **日期**：2025-08-09 08:33:37 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Deprecate unused methods in OAuth2Util (#13767)
- **PR/Issue**：#13767

## 总体目的

该提交将 `OAuth2Util` 中 4 个不再使用的重载方法标记为 `@Deprecated`，并添加 Javadoc 说明替代方法，为后续版本移除这些方法做准备。

`OAuth2Util` 类中存在多个 `exchangeToken` 和 `fetchToken` 方法的重载版本。随着功能演进，新增了带有更多参数（如 `clientId`、`clientSecret`、`tokenEndpoint`、`headers` map 等）的更完整版本，而旧的重载版本已不再被内部或外部使用。保留这些未使用的方法会增加 API 表面积和维护成本。按照 Iceberg 的版本管理策略，先在 1.10.0 版本标记为 deprecated，计划在 1.11.0 版本移除，给使用者一个迁移过渡期。

## 如何达成设计目的

对 4 个方法添加 `@Deprecated` 注解和 Javadoc：

1. 两个 `exchangeToken` 重载方法：标注 deprecated，指向带有完整参数列表的 `exchangeToken(RESTClient, Map, String, String, String, String, String, String, Map)` 版本。

2. 两个 `fetchToken` 重载方法：标注 deprecated，指向带有完整参数列表的 `fetchToken(RESTClient, Map, String, String, String, Map)` 版本。

Javadoc 中明确标注 "since 1.10.0, will be removed in 1.11.0" 以及推荐的替代方法链接。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/auth/OAuth2Util.java` (+22/-0 lines)

**修改目的**：为 4 个未使用的重载方法添加 @Deprecated 注解和说明。

**工作逻辑**：

在以下 4 个方法前添加 `@Deprecated` 注解和 Javadoc：

1. `exchangeToken(RESTClient, Map, String, String, String, String, String)` - 7 参数版本，指向 9 参数版本（含额外 `clientId`、`clientSecret`、`Map` headers 参数）。

2. `exchangeToken(RESTClient, Map, String, String, String)` - 5 参数版本，同样指向 9 参数版本。

3. `fetchToken(RESTClient, Map, String, String)` - 4 参数版本，指向 6 参数版本（含 `tokenEndpoint` 和 `Map` headers 参数）。

4. `fetchToken(RESTClient, Map, String, String, String)` - 5 参数版本，指向 6 参数版本。

每个 Javadoc 均包含 `@deprecated since 1.10.0, will be removed in 1.11.0` 及 `use {@link ...} instead` 链接，方法实现本身未做任何修改。

## 总结

该提交将 `OAuth2Util` 中 4 个不再使用的 `exchangeToken` 和 `fetchToken` 重载方法标记为 `@Deprecated`，并提供了清晰的迁移指引。这是 API 清理的标准做法，先标记废弃再在后续版本移除，既减少 API 表面积又给使用者迁移过渡期。该提交不改变任何运行时行为，仅添加注解和文档。
