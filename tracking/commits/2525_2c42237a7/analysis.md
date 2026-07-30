# 提交 2525：feat: make RESTCatalogServer catalog name configurable (#13750)

## 提交信息

- **序号**：2525 / 4088
- **哈希**：2c42237a732d195b21226f9988c91e9f1c171d7b
- **短哈希**：2c42237a7
- **日期**：2025-08-18 22:42:23 -0700
- **作者**：Itamar Weiss
- **提交说明**：feat: make RESTCatalogServer catalog name configurable (#13750)
- **PR/Issue**：#13750
- **共同作者**：Kevin Liu

## 总体目的

此提交为 `RESTCatalogServer` 添加了可配置的 catalog 名称支持，允许用户通过配置属性 `catalog.name` 自定义 REST Catalog 服务器的目录名称，而非使用硬编码的 `"rest_backend"`。

`RESTCatalogServer` 是 Iceberg Open API 模块中的测试夹具（test fixture），用于在测试和集成场景中启动一个 REST Catalog 服务器实例。此服务器内部通过 `CatalogUtil.buildIcebergCatalog()` 构建底层的 Catalog 实例。

此前，catalog 名称被硬编码为 `"rest_backend"`，这意味着：
1. 如果用户或测试需要使用不同的 catalog 名称（例如匹配其环境中的命名约定），无法通过配置实现
2. 在日志和调试信息中，所有 REST Catalog 服务器实例都显示相同的名称，难以区分
3. 某些测试场景需要特定的 catalog 名称来验证名称相关的行为

## 如何达成设计目的

设计方案在 `RESTCatalogServer` 中添加 `CATALOG_NAME` 配置键和默认值，在构建 Catalog 时从配置属性中读取名称。

具体设计要点：
1. 定义 `CATALOG_NAME = "catalog.name"` 配置键常量
2. 定义 `CATALOG_NAME_DEFAULT = "rest_backend"` 默认值常量，保持向后兼容
3. 在 `buildCatalog()` 方法中，使用 `PropertyUtil.propertyAsString()` 从配置属性中读取 catalog 名称，默认回退到 `rest_backend`
4. 将读取的 catalog 名称传递给 `CatalogUtil.buildIcebergCatalog()` 替代硬编码字符串
5. 更新日志信息以显示实际的 catalog 名称

## 修改详情

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTCatalogServer.java` (+8/-2 lines)

**修改目的**：添加可配置的 catalog 名称支持。

**工作逻辑**：
1. 添加两个常量：
   - `CATALOG_NAME = "catalog.name"`：公开的配置键，允许外部设置
   - `CATALOG_NAME_DEFAULT = "rest_backend"`：默认值，保持向后兼容
2. 在 `buildCatalog()` 方法中：
   - 使用 `PropertyUtil.propertyAsString(catalogProperties, CATALOG_NAME, CATALOG_NAME_DEFAULT)` 从配置中读取 catalog 名称
   - 将 `CatalogUtil.buildIcebergCatalog("rest_backend", ...)` 改为 `CatalogUtil.buildIcebergCatalog(catalogName, ...)`
   - 更新日志：从 `"Creating catalog with properties: {}"` 改为 `"Creating {} catalog with properties: {}"`，在日志中显示实际的 catalog 名称

## 总结

此提交是一个功能增强，使 REST Catalog 服务器的 catalog 名称可通过 `catalog.name` 配置属性自定义。修改保持向后兼容（默认值仍为 `rest_backend`），同时提供了灵活性以支持不同的测试和部署场景。这是一个小而精的改进，代码变更量小但实用价值明显。
