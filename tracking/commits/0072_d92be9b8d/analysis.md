# 提交 0072：AWS: Glue catalog strip trailing slash on DB URI (#8870)

## 提交信息

- **序号**：0072 / 4088
- **哈希**：d92be9b8d539fed4d25c06ba486e1d5019932821
- **短哈希**：d92be9b8d5
- **日期**：2023-10-19
- **作者**：Amogh Jahagirdar
- **提交说明**：AWS: Glue catalog strip trailing slash on DB URI (#8870)
- **PR/Issue**：#8870

## 总体目的

此提交修复了 AWS Glue Catalog 在处理数据库（Database）`locationUri` 时，由于尾斜杠（trailing slash）未被规范化而引发的路径拼接异常问题。

具体场景是：当用户在 Glue 中创建数据库时，数据库的 `locationUri` 可能被配置为类似 `s3://bucket2/db/` 这样的带尾斜杠形式（这是合法且常见的，用户通过 AWS 控制台、CloudFormation、Terraform 或其他工具建库时经常会带上尾斜杠）。然而 `GlueCatalog` 在两处直接使用了这个 URI：

1. `defaultWarehouseLocation` 中通过 `String.format("%s/%s", dbLocationUri, tableIdentifier.name())` 拼接表默认路径，会得到 `s3://bucket2/db//table`，出现双斜杠。
2. `loadNamespaceMetadata` 中直接把 `database.locationUri()` 写入返回的 namespace 属性 map（键为 `GLUE_DB_LOCATION_KEY`），把带尾斜杠的原始 URI 透传给上层调用方。

双斜杠路径会带来多个隐患：路径在字符串比较时不相等（`s3://bucket2/db/table` ≠ `s3://bucket2/db//table`），导致缓存失效或重复创建；不同引擎对 `//` 的处理不一致，可能在 S3 上产生意外的"空目录"前缀；元数据中的 location 不规范会影响后续基于路径的定位与权限校验。提交通过复用 core 模块已有的 `LocationUtil.stripTrailingSlash` 工具方法，在这两处出口对 URI 做归一化，保证从 GlueCatalog 流出的数据库位置永远不含尾斜杠，从而避免下游拼接出畸形路径。

## 如何达成设计目的

整体设计思路是"在出口处归一化"：不修改 Glue 返回的原始响应，而是在 GlueCatalog 读取 `locationUri` 并对外暴露的两个关键点统一调用 `LocationUtil.stripTrailingSlash`。这样既不侵入 AWS SDK 的数据模型，也覆盖了所有从 GlueCatalog 暴露 DB location 的路径。同时新增了针对带尾斜杠场景的单元测试，并改造了 `testLoadNamespaceMetadata` 使其在 mock 数据中带入带尾斜杠的 `locationUri`，以验证 `loadNamespaceMetadata` 的归一化行为。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/glue/GlueCatalog.java`

**修改目的**：在 `defaultWarehouseLocation` 与 `loadNamespaceMetadata` 两处对 Glue 返回的数据库 `locationUri` 进行尾斜杠归一化。

**工作逻辑**：

第一处改动在 `defaultWarehouseLocation` 方法（[GlueCatalog.java:354-356](../../../../../aws/src/main/java/org/apache/iceberg/aws/glue/GlueCatalog.java) 附近）。原逻辑为：

```java
String dbLocationUri = response.database().locationUri();
if (dbLocationUri != null) {
  return String.format("%s/%s", dbLocationUri, tableIdentifier.name());
}
```

修改后在 null 检查通过后、拼表名之前先剥离尾斜杠：

```java
String dbLocationUri = response.database().locationUri();
if (dbLocationUri != null) {
  dbLocationUri = LocationUtil.stripTrailingSlash(dbLocationUri);
  return String.format("%s/%s", dbLocationUri, tableIdentifier.name());
}
```

这样无论数据库 location 是 `s3://bucket2/db/` 还是 `s3://bucket2/db///`，最终拼接出的表默认路径都是 `s3://bucket2/db/table`，杜绝了双斜杠。

第二处改动在 `loadNamespaceMetadata` 方法（[GlueCatalog.java:633](../../../../../aws/src/main/java/org/apache/iceberg/aws/glue/GlueCatalog.java) 附近），把 Glue 数据库 `locationUri` 写入返回 properties 时同样做归一化：

```java
if (database.locationUri() != null) {
  result.put(
      IcebergToGlueConverter.GLUE_DB_LOCATION_KEY,
      LocationUtil.stripTrailingSlash(database.locationUri()));
}
```

这一处保证了通过 `loadNamespaceMetadata` 暴露给上层（例如引擎查询 namespace 属性、`createNamespace` 后回读、`setProperties` 等）的 location 字段同样不含尾斜杠，避免不一致的 location 在跨 catalog、跨引擎场景下引发问题。

`LocationUtil.stripTrailingSlash`（[LocationUtil.java](../../../../../core/src/main/java/org/apache/iceberg/util/LocationUtil.java)）通过循环去除路径末尾所有连续斜杠，并使用 relocated guava `Preconditions` 校验入参非空非空串，是 Iceberg core 中已有的路径规范化工具，本提交只是复用而非新引入。

### `aws/src/test/java/org/apache/iceberg/aws/glue/TestGlueCatalog.java`

**修改目的**：补充对带尾斜杠 DB URI 场景的覆盖，验证归一化行为生效。

**工作逻辑**：

新增测试 `testDefaultWarehouseLocationDbUriTrailingSlash`：mock `getDatabase` 返回 `locationUri="s3://bucket2/db/"`，断言 `glueCatalog.defaultWarehouseLocation(TableIdentifier.of("db", "table"))` 等于 `"s3://bucket2/db/table"`（而不是 `s3://bucket2/db//table`），直接验证第一处改动的修复效果。

改造现有测试 `testLoadNamespaceMetadata`：在原 mock 的 `Database` 上补充 `locationUri("s3://bucket2/db/")`，并在 `parameters` 中显式加入 `GLUE_DB_LOCATION_KEY -> "s3://bucket2/db"`。这样既验证了 `loadNamespaceMetadata` 在写入 `GLUE_DB_LOCATION_KEY` 时会调用 `stripTrailingSlash`（输出 `s3://bucket2/db` 而非 `s3://bucket2/db/`），也验证了当 parameters 中已存在同名键、且 database 同时带 `locationUri` 时，被归一化后的 location 会覆盖原 parameters 中的值。

## 小结

通过在 GlueCatalog 的 `defaultWarehouseLocation` 与 `loadNamespaceMetadata` 两处出口对数据库 `locationUri` 调用 `LocationUtil.stripTrailingSlash`，修复了 Glue 数据库带尾斜杠 URI 导致表默认路径出现双斜杠及 namespace 属性不一致的缺陷，提升了 Glue Catalog 在异构建库场景下的路径一致性。
