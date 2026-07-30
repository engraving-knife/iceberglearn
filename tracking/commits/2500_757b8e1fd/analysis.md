# 提交 2500：Core: Use ResourcePaths instead of hard-coded resource paths in RESTCatalogAdapter #13814 (#13815)

## 提交信息

- **序号**：2500 / 4088
- **哈希**：757b8e1fd5513c6b4b5d6ba03145bd366e0910ef
- **短哈希**：757b8e1fd
- **日期**：2025-08-14 14:57:08 -0700
- **作者**：guixiaowen
- **提交说明**：Core: Use ResourcePaths instead of hard-coded resource paths in RESTCatalogAdapter #13814 (#13815)
- **PR/Issue**：#13815（关联 #13814）

## 总体目的

本提交将 `RESTCatalogAdapter` 测试类中两处硬编码的 REST 资源路径替换为 `ResourcePaths` 类提供的静态方法调用，实现路径定义的统一管理。

`RESTCatalogAdapter` 是 REST Catalog 的测试适配器，其中 `Route` 枚举定义了各 REST 端点的路由映射。原本大部分路由已使用 `ResourcePaths` 的方法（如 `ResourcePaths.V1_NAMESPACES`），但 TOKENS 和 CONFIG 两个路由仍使用硬编码字符串（`"v1/oauth/tokens"` 和 `"v1/config"`）。这造成了路径定义的不一致：如果将来路径格式发生变化，需要同时修改 `ResourcePaths` 和 `RESTCatalogAdapter` 两处。

`ResourcePaths` 类已经提供了 `tokens()` 和 `config()` 两个静态方法，分别返回 `"v1/oauth/tokens"` 和 `"v1/config"`，因此本提交将硬编码字符串替换为这些方法调用。

## 如何达成设计目的

将 `Route` 枚举中 TOKENS 和 CONFIG 的路径参数从字符串字面量改为 `ResourcePaths.tokens()` 和 `ResourcePaths.config()` 方法调用，与同一枚举中其他路由的写法保持一致。

## 修改详情

### `core/src/test/java/org/apache/iceberg/rest/RESTCatalogAdapter.java` (+2/-2 lines)

**修改目的**：统一路径定义来源。

**工作逻辑**：
- TOKENS 路由的路径由 `"v1/oauth/tokens"` 改为 `ResourcePaths.tokens()`。
- CONFIG 路由的路径由 `"v1/config"` 改为 `ResourcePaths.config()`。
- 其余路由（如 `SEPARATE_AUTH_TOKENS_URI` 仍使用硬编码 URL `https://auth-server.com/token`，因为它是测试专用的外部 URL，不属于标准 ResourcePaths 管理范围）保持不变。

## 总结

本提交是一个代码一致性改进，消除了 `RESTCatalogAdapter` 中路径定义的重复，使所有标准 REST 路径统一由 `ResourcePaths` 类管理。这符合 DRY 原则，便于未来路径变更时的维护。改动范围小且不影响运行时行为。
