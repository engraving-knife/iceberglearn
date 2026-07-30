# 提交 0738：Hive: Remove deprecated `setSchema(TableMetadata, Map<String, String>)`

## 提交信息
- **序号**：0738 / 4088
- **哈希**：aeb26820a82859323c46f1879bd96f932a3cfca5
- **短哈希**：aeb26820a
- **日期**：2024-05-01 17:11:29 +0200
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Hive: Remove deprecated `setSchema(TableMetadata, Map<String, String>)` (#10257)
- **PR/Issue**：#10257

## 总体目的

本提交移除 Hive Metastore 集成模块中已废弃的 `setSchema(TableMetadata, Map<String, String>)` 方法重载，这是 API 清理工作的一部分。

### 移除原因

被移除的方法签名为：
```java
@Deprecated
default void setSchema(TableMetadata metadata, Map<String, String> parameters) {
  setSchema(metadata.schema(), parameters);
}
```

该方法在 1.6.0 版本被标记为 `@Deprecated`，并注明"将在 1.7.0 中移除"。其废弃与移除的原因如下：

1. **冗余的便利方法**：该方法仅仅是从 `TableMetadata` 中取出 `schema()`，然后委托给 `setSchema(Schema, Map<String, String>)`。这种"拆包再传递"的便利方法增加了 API 表面积，但并未提供实质性的额外能力——调用方完全可以自行调用 `metadata.schema()` 再传入。

2. **API 简化与职责单一**：保留两个重载（一个接收 `TableMetadata`，一个接收 `Schema`）会让调用方困惑应使用哪个。移除后，`HiveOperationsBase` 接口只保留 `setSchema(Schema, Map<String, String>)`，职责更清晰——该方法负责将 schema 信息写入 Hive 表的 properties 参数 map。

3. **版本演进计划**：Iceberg 对废弃 API 遵循"标记废弃一个版本、下一个版本移除"的策略。该方法在 1.6.0 标记废弃，按计划在 1.7.0 移除。本提交执行了这一计划。

## 如何达成设计目的

移除过程分两步：

1. **删除接口中的废弃方法**：从 `HiveOperationsBase` 接口中删除 `setSchema(TableMetadata, Map<String, String>)` 的 `@Deprecated` default 实现。由于它是 default 方法，删除不会破坏编译（default 方法提供默认实现），但任何直接调用该重载的代码需要改为调用 `setSchema(Schema, Map)`。

2. **更新测试中的调用点**：`TestHiveCatalog` 中有一处调用 `ops.setSchema(metadata, parameters)`，需改为 `ops.setSchema(metadata.schema(), parameters)`，即先从 `TableMetadata` 提取 `Schema` 再传入。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveOperationsBase.java`
**修改目的**：移除已废弃的 `setSchema(TableMetadata, Map<String, String>)` 方法。

**具体改动**：删除以下 8 行代码（含 Javadoc 注释、`@Deprecated` 注解和 default 方法实现）：
```java
/**
 * @deprecated since 1.6.0, will be removed in 1.7.0; Use {@link #setSchema(Schema, Map)} instead
 */
@Deprecated
default void setSchema(TableMetadata metadata, Map<String, String> parameters) {
  setSchema(metadata.schema(), parameters);
}
```

移除后，接口中仅保留 `setSchema(Schema schema, Map<String, String> parameters)` 方法。该方法的作用是：从 parameters 中移除 `CURRENT_SCHEMA` 属性，并在 `exposeInHmsProperties()` 为 true 且 schema 非空时，将 schema 的相关信息写入 Hive 表属性。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveCatalog.java`
**修改目的**：将测试中对已移除重载的调用改为使用保留的方法签名。

**具体改动**：
- 修改前：`ops.setSchema(metadata, parameters);`
- 修改后：`ops.setSchema(metadata.schema(), parameters);`

该测试验证 `setSchema` 方法在 schema 为 null 时正确清理 `CURRENT_SCHEMA` 属性。调用点上下文为：先构造一个 `TableMetadata`（其 schema 为 null），调用 `setSchema` 后断言 `parameters` 不含 `CURRENT_SCHEMA` 键。

## 小结

- **成效**：成功移除废弃 API，简化了 `HiveOperationsBase` 接口。接口现在只有一个 `setSchema` 方法，消除了重载歧义。
- **影响范围**：仅影响 `hive-metastore` 模块。对内部代码而言，唯一调用点（测试）已同步更新。对外部自定义实现 `HiveOperationsBase` 的用户，若其代码调用了被移除的 `setSchema(TableMetadata, Map)` 重载，需改为 `setSchema(metadata.schema(), parameters)`。这是一个**源码不兼容**的变更（但非二进制不兼容，因为被移除的是 default 方法，编译时已有默认实现）。
- **回迁到 1.4.x 的注意事项**：
  - 需确认 1.4.x 分支的 `HiveOperationsBase` 是否存在该废弃方法。由于该方法在 1.6.0 才标记废弃，1.4.x 分支可能尚未引入该废弃方法（即 1.4.x 可能仍使用旧的 API 结构），或者 1.4.x 可能完全没有这个废弃重载。
  - 若 1.4.x 中不存在该废弃方法，则本提交无需回迁（无东西可移除）。
  - 若 1.4.x 中存在，则需评估移除是否会对 1.4.x 的外部用户造成影响。由于 1.4.x 是较旧版本，移除一个标记为"1.7.0 移除"的方法在 1.4.x 上可能过早，建议谨慎评估回迁必要性。
  - 如果仅为保持与 main 分支一致而回迁，需同时回迁测试调用点的修改。
