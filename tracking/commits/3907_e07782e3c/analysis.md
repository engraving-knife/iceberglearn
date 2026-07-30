# 提交 3907：Core: Parquet per column dictionary encoding (#16713)

## 提交信息

- **序号**：3907 / 4088
- **哈希**：e07782e3cc0781a2d32b7572e9c2bab922e67b9e
- **短哈希**：e07782e3c
- **日期**：2026-06-19 08:59:32 +0200
- **作者**：Alex Sorokoumov
- **提交说明**：Core: Parquet per column dictionary encoding (#16713)
- **PR/Issue**：#16713, Closes #16599

## 总体目的

为 Iceberg 的 Parquet writer 添加按列控制字典编码（dictionary encoding）的能力。此前，Iceberg 只暴露从 parquet-mr 继承的全局字典编码开关（`parquet.enable.dictionary`），无法为单个列单独启用或禁用字典编码。

字典编码对低基数列（如类别、状态码）非常有效，可以大幅压缩存储；但对高基数列（如 UUID、时间戳）反而会增加开销且降低读取性能。无法按列控制意味着用户只能全量启用或禁用，无法针对不同列特征进行优化。parquet-java 已通过 `ParquetProperties.Builder.withDictionaryEncoding(String columnPath, boolean)` 支持按列控制，本提交将该能力透传到 Iceberg。

## 如何达成设计目的

通过三步实现：
1. 新增表属性前缀 `write.parquet.dict-encoding-enabled.column.<col>`，与现有的 `write.parquet.bloom-filter-enabled.column.*` 和 `write.parquet.stats-enabled.column.*` 约定一致
2. 在 `Context.dataContext` 中解析该前缀为 `columnDictionaryEncodingEnabled` 映射，通过 getter 暴露
3. 在 `Parquet.WriteBuilder` 中添加 `withDictionaryEncoding(String, boolean)` 编程式 API

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+3/-0 lines)

**修改目的**：定义新的表属性前缀常量。

```java
public static final String PARQUET_DICT_ENCODING_ENABLED_COLUMN_PREFIX =
    "write.parquet.dict-encoding-enabled.column.";
```

### `docs/docs/configuration.md` (+1/-0 lines)

**修改目的**：文档中新增配置项说明。

```markdown
| write.parquet.dict-encoding-enabled.column.col1     | (not set)                   | Controls whether to use parquet dictionary encoding for column 'col1'                                                                                                                                                                              |
```

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (+48/-0 lines)

**修改目的**：实现按列字典编码的配置解析和应用。

**工作逻辑**：

1. **WriteBuilder 新增编程式 API**：
```java
public WriteBuilder withDictionaryEncoding(String columnName, boolean enabled) {
  config.put(PARQUET_DICT_ENCODING_ENABLED_COLUMN_PREFIX + columnName, String.valueOf(enabled));
  return this;
}
```
columnName 是 Iceberg 字段名，嵌套字段用点分隔（如 `tags.element.id`）。

2. **Context 解析配置**：
```java
// dataContext 中解析
Map<String, String> columnDictionaryEncodingEnabled =
    PropertyUtil.propertiesWithPrefix(config, PARQUET_DICT_ENCODING_ENABLED_COLUMN_PREFIX);

// deleteContext 保持空映射（删除文件不支持按列字典编码）
ImmutableMap.of(),
```

3. **setDictionaryEncodingConfig 方法**：将 Iceberg 列名映射到 Parquet 列路径后应用：
```java
private void setDictionaryEncodingConfig(
    Context context,
    Map<String, String> colNameToParquetPathMap,
    BiConsumer<String, Boolean> withDictionaryEncoding) {
  context.columnDictionaryEncodingEnabled()
      .forEach((colPath, isEnabled) -> {
        String parquetColumnPath = colNameToParquetPathMap.get(colPath);
        if (parquetColumnPath == null) {
          LOG.warn("Skipping dictionary encoding config for missing field: {}", colPath);
          return;
        }
        withDictionaryEncoding.accept(parquetColumnPath, Boolean.valueOf(isEnabled));
      });
}
```
在 `build()` 和 `buildForPartitioningWriter()` 中调用，将配置应用到 `ParquetProperties.Builder`。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java` (+169/-0 lines)

**修改目的**：测试按列字典编码功能。

**工作逻辑**：
1. `testPerColumnDictionaryEncoding`：验证禁用 category 列的字典编码后该列不使用字典，而 region 列仍使用全局默认
2. `testPerColumnDictionaryEncodingNestedField`：验证嵌套字段（`tags.element`）的字典编码控制，测试 Iceberg 列名到 Parquet 3-level 路径（`tags.list.element`）的映射
3. `testPerColumnDictionaryEncodingOverridesGlobal`：验证按列设置覆盖全局设置
4. `testPerColumnDictionaryEncodingWithProgrammaticApi`：验证编程式 API 的效果

## 总结

为 Iceberg Parquet writer 添加了按列控制字典编码的能力，通过表属性前缀和编程式 API 两种方式配置。该功能与现有的按列 bloom filter 和 stats 配置约定一致，使用户能针对不同列特征优化压缩策略。特别值得注意的是 Iceberg 列名到 Parquet 3-level 路径的映射处理，确保嵌套字段也能正确配置。
