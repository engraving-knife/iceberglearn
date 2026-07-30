# 提交 3481：API, Core: Add overwrite-aware table registration (#15525)

## 提交信息

- **序号**：3481 / 4088
- **哈希**：2abac79fcae94b5ad039bd09f7235be191b0761e
- **短哈希**：2abac79fca
- **日期**：2026-03-28 22:39:46 -0700
- **作者**：Rishi
- **提交说明**：API, Core: Add overwrite-aware table registration (#15525)
- **PR/Issue**：#15525

## 总体目的

为 Iceberg 的 Catalog API 添加"覆盖式表注册"（overwrite-aware table registration）能力。原有的 `registerTable` 方法仅支持"如果不存在则注册"的语义，若表已存在则抛出 `AlreadyExistsException`。新增功能允许调用方在注册表时指定 `overwrite=true`，覆盖已存在的表注册，这在元数据迁移、表位置修正等场景下非常有用。

## 如何达成设计目的

1. 在 `Catalog` 和 `SessionCatalog` 接口中新增带 `boolean overwrite` 参数的 `registerTable` 重载方法，使用 default 方法提供向后兼容（`overwrite=false` 时委托给原方法，`overwrite=true` 时抛出 `UnsupportedOperationException`）。
2. 在 REST 协议层面，`RegisterTableRequest` 已支持 `overwrite` 字段，`CatalogHandlers` 将该字段传递给 catalog。
3. 在 `RESTCatalog`、`RESTSessionCatalog`、`CachingCatalog`、`BaseSessionCatalog` 等实现中实现新方法，确保缓存正确失效。
4. 添加测试覆盖 overwrite=false、overwrite=true（catalog 不支持时）、overwrite=true（catalog 支持时）三种场景。

## 修改详情

### `api/src/main/java/org/apache/iceberg/catalog/Catalog.java` (+21 lines)

**修改目的**：在 Catalog 接口中新增 overwrite-aware 的 registerTable default 方法。

**工作逻辑**：
- 在原 `registerTable` 方法 javadoc 中添加指向新方法的链接。
- 新增 default 方法：
```java
default Table registerTable(
    TableIdentifier identifier, String metadataFileLocation, boolean overwrite) {
  if (!overwrite) {
    return registerTable(identifier, metadataFileLocation);
  }
  throw new UnsupportedOperationException("Registering tables with overwrite is not supported");
}
```
当 `overwrite=false` 时委托给原方法；`overwrite=true` 时默认抛出 `UnsupportedOperationException`，由具体实现决定是否支持。

### `api/src/main/java/org/apache/iceberg/catalog/SessionCatalog.java` (+26 lines)

**修改目的**：在 SessionCatalog 接口中新增对应的 overwrite-aware registerTable default 方法。

**工作逻辑**：与 Catalog 类似，新增带 `SessionContext` 和 `boolean overwrite` 参数的 default 方法，逻辑相同。

### `core/src/main/java/org/apache/iceberg/CachingCatalog.java` (+8 lines)

**修改目的**：在 CachingCatalog 中实现新方法，确保注册后缓存失效。

**工作逻辑**：
```java
@Override
public Table registerTable(
    TableIdentifier identifier, String metadataFileLocation, boolean overwrite) {
  Table table = catalog.registerTable(identifier, metadataFileLocation, overwrite);
  invalidateTable(identifier);
  return table;
}
```
委托给底层 catalog 后调用 `invalidateTable` 使缓存失效，保证后续 loadTable 获取最新数据。

### `core/src/main/java/org/apache/iceberg/catalog/BaseSessionCatalog.java` (+6 lines)

**修改目的**：在 BaseSessionCatalog 的内部 SessionCatalogWrapper 类中委托新方法到外部类。

**工作逻辑**：内部 wrapper 类的 `registerTable` 委托给 `BaseSessionCatalog.this.registerTable(context, ident, metadataFileLocation, overwrite)`。

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+3/-1 lines)

**修改目的**：在 REST 处理器中将请求的 `overwrite` 字段传递给 catalog。

**工作逻辑**：
```java
Table table =
    catalog.registerTable(identifier, request.metadataLocation(), request.overwrite());
```

### `core/src/main/java/org/apache/iceberg/rest/RESTCatalog.java` (+6 lines)

**修改目的**：在 RESTCatalog 中委托新方法到底层 delegate。

**工作逻辑**：`delegate.registerTable(ident, metadataFileLocation, overwrite)`。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+10 lines)

**修改目的**：在 RESTSessionCatalog 中实现新方法，将 overwrite 字段加入 REST 请求。

**工作逻辑**：
- 原 `registerTable(context, ident, metadataFileLocation)` 委托给新方法并传 `overwrite=false`。
- 新方法构建 `ImmutableRegisterTableRequest` 时加入 `.overwrite(overwrite)` 字段，通过 REST 协议发送到服务端。

### `core/src/test/java/org/apache/iceberg/hadoop/TestCachingCatalog.java` (+29 lines)

**修改目的**：测试 CachingCatalog 在 overwrite 注册时正确失效缓存。

**工作逻辑**：`testRegisterTableWithOverwriteInvalidatesCache` 测试注册表后加载到缓存，drop 后重新注册，验证缓存被正确失效。

### `core/src/test/java/org/apache/iceberg/rest/TestRESTCatalog.java` (+131 lines)

**修改目的**：测试 RESTCatalog 在三种 overwrite 场景下的行为。

**工作逻辑**：
- `testRegisterTableOverwriteFalse`：验证 overwrite=false 时请求正确发送且不覆盖。
- `testRegisterTableOverwriteTrue`：验证 catalog 不支持 overwrite 时抛出 RESTException。
- `testRegisterTableOverwriteTrueSupported`：使用自定义支持 overwrite 的 InMemoryCatalog，验证覆盖注册成功且 metadata 位置更新。使用 Mockito spy 验证 REST 请求中包含正确的 overwrite 字段。

## 总结

这是一个功能增强提交，为 Catalog API 添加了覆盖式表注册能力。设计上采用 default 方法保证向后兼容，由各 catalog 实现自行决定是否支持 overwrite。REST 协议层通过 `RegisterTableRequest.overwrite` 字段传递该选项。CachingCatalog 实现确保缓存正确失效。测试覆盖了不支持、支持、false 三种场景。
