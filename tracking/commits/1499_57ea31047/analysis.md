# 提交 1499：Parquet: Implement defaults for generic data (#11785)

## 提交信息

- **序号**：1499 / 4088
- **哈希**：57ea310475e3336e7ec5b581ec40ca956972822a
- **短哈希**：57ea31047
- **日期**：2024-12-16（Mon Dec 16 12:46:05 2024 -0800）
- **作者**：Ryan Blue <blue@apache.org>
- **提交说明**：Parquet: Implement defaults for generic data (#11785)
- **PR/Issue**：#11785

## 总体目的

Iceberg 表 schema 支持为字段声明"初始默认值"（initial default）和"写入默认值"（write default），用于 schema 演进场景：当老数据文件缺少新增字段时，读取时用 `initial default` 填充；写入时若未提供值则用 `write default`。此前 Iceberg 的 Parquet generic data 读取器（`BaseParquetReaders`）在字段缺失时一律返回 null：

- 对可选（optional）字段返回 null 是合理的；
- 但对必填（required）字段返回 null 是错误的——必填字段不应有 null 值；
- 而且忽略了字段声明的 `initial default`，导致默认值语义在 Parquet generic 路径上未实现。

本提交为 `BaseParquetReaders` 实现"按字段默认值填充缺失列"的逻辑：当文件中没有某字段的 reader 时，按优先级——常量 > `field.initialDefault()` > null（仅 optional） > 抛错（required 且无默认）——填充值。同时扩展 `TestGenericData`（Parquet）覆盖默认值场景，并改进 `DataTestHelpers.assertEquals` 使其能按字段名（而非位置）对比、对缺失字段用 `initialDefault` 校验。

## 如何达成设计目的

1. 重写 `BaseParquetReaders` 中 struct reader 的字段重排逻辑：把"取 reader 或返回 null"扩展为"取常量 / 取 reader / 取 `initialDefault` / null / 抛错"的优先级判断；并对 `initialDefault` 走 `ParquetValueReaders.constant(value, maxDefinitionLevel)` 以正确处理 Parquet 的定义层级。
2. 重构 `TestGenericData.writeAndValidate` 为支持"写入 schema"与"期望读取 schema"分离的版本，使测试能模拟"老文件没有新字段、读取时按默认值填充"的场景。
3. 新增多个测试：缺失必填无默认（应抛错）、默认值、null 默认值、嵌套 struct 默认值、map 值默认值、list 元素默认值。
4. 改进 `DataTestHelpers.assertEquals`：按字段 ID 在期望 struct 中找对应字段，若期望中没有该字段则断言实际值等于 `field.initialDefault()`。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetReaders.java`

**修改目的**：在 struct reader 构建阶段，按字段默认值语义填充缺失列。

**工作逻辑**：原逻辑（简化）：

```java
for (Types.NestedField field : expectedFields) {
  int id = field.fieldId();
  if (idToConstant.containsKey(id)) { /* 用常量 */ }
  else if (id == MetadataColumns.IS_DELETED.fieldId()) { /* false */ }
  else {
    ParquetValueReader<?> reader = readersById.get(id);
    if (reader != null) { reorderedFields.add(reader); ... }
    else { reorderedFields.add(ParquetValueReaders.nulls()); types.add(null); }
  }
}
```

新逻辑把 `reader` 提到外层提前取出，并在 `reader == null` 时细分：

```java
ParquetValueReader<?> reader = readersById.get(id);
if (idToConstant.containsKey(id)) { /* 用常量，处理 null 常量 */ }
else if (id == MetadataColumns.IS_DELETED.fieldId()) { /* false */ }
else if (reader != null) { reorderedFields.add(reader); types.add(typesById.get(id)); }
else if (field.initialDefault() != null) {
  // 用初始默认值作为常量，传入该字段的 maxDefinitionLevel（或默认）
  reorderedFields.add(
      ParquetValueReaders.constant(
          field.initialDefault(),
          maxDefinitionLevelsById.getOrDefault(id, defaultMaxDefinitionLevel)));
  types.add(typesById.get(id));
}
else if (field.isOptional()) {
  reorderedFields.add(ParquetValueReaders.nulls());
  types.add(null);
}
else {
  throw new IllegalArgumentException("Missing required field: " + field.name());
}
```

关键点：

- `initialDefault != null` 才走默认值常量路径；若默认值本身是 null（即未声明默认值），则进入 optional 走 nulls、required 抛错。
- `ParquetValueReaders.constant(value, maxDefinitionLevel)` 接收 max definition level，确保嵌套 struct 中常量列的定义层级正确（影响 Parquet 重复/定义层级语义）。
- 必填字段缺失且无默认时显式抛 `IllegalArgumentException`，避免静默返回 null 导致下游 NPE。

### `data/src/test/java/org/apache/iceberg/data/DataTestHelpers.java`

**修改目的**：让断言能按字段名而非位置匹配，并对期望中不存在的字段用 `initialDefault` 校验。

**工作逻辑**：原实现按 struct 字段位置遍历、`expected.get(i)` vs `actual.get(i)`。新实现：

```java
Types.StructType expectedType = expected.struct();
for (Types.NestedField field : struct.fields()) {
  Types.NestedField expectedField = expectedType.field(field.fieldId());
  if (expectedField != null) {
    assertEquals(field.type(),
        expected.getField(expectedField.name()),
        actual.getField(field.name()));
  } else {
    assertThat(actual.getField(field.name())).isEqualTo(field.initialDefault());
  }
}
```

这样当期望记录（写入时生成的）不含某字段、而实际记录（读取时按默认值填充）含该字段时，能正确断言实际值等于 `field.initialDefault()`。

### `data/src/test/java/org/apache/iceberg/data/parquet/TestGenericData.java`

**修改目的**：覆盖默认值读取场景。

**工作逻辑**：

- 把 `writeAndValidate(Schema)` 重构为 `writeAndValidate(writeSchema, expectedSchema)`：用 `writeSchema` 生成数据并写文件，用 `expectedSchema` 投影读取并断言；固定随机种子 `12228L` 替代 `0L` 保证可复现。
- 复用 containers 路径用 for-each 重写，更简洁。
- 新增 `testMissingRequiredWithoutDefault`：写入只有 `id`，期望多一个 required `missing_str`（无默认），断言读取抛 `IllegalArgumentException("Missing required field: missing_str")`。
- 新增 `testDefaultValues`：写入 `(id, data)`，期望多出 `missing_str`（默认 "orange"）、`missing_int`（默认 34），验证读取时填充默认值。
- 新增 `testNullDefaultValue`：期望多一个 optional `missing_date`（无默认，即 null 默认），验证 optional 字段缺失时返回 null。
- 新增 `testNestedDefaultValue`：嵌套 struct 内新增 `missing_inner_float`（默认 -0.0F），验证嵌套字段默认值。
- 新增 `testMapNestedDefaultValue`：map 值 struct 内新增 `value_int`（默认 34），验证 map 值嵌套字段默认值。
- 新增 `testListNestedDefaultValue`：list 元素 struct 内新增 `element_int`（默认 34），验证 list 元素嵌套字段默认值。

这些测试通过 `NestedField.required(...).withId(...).ofType(...).withInitialDefault(...).build()` 流式 API 构造带默认值的字段，覆盖了 struct/map/list 三种嵌套场景下的默认值传播。

## 小结

- **成效**：Parquet generic data 读取器现正确实现字段默认值语义——缺失列按 `initial default` 填充；必填字段缺失且无默认时显式抛错而非返回 null；常量列带 max definition level 保证嵌套语义正确。测试覆盖了顶层与 struct/map/list 嵌套场景，以及必填无默认、optional 无默认等边界。
- **影响范围**：`parquet` 模块 1 个主代码文件（`BaseParquetReaders`）、`data` 模块 2 个测试文件（`DataTestHelpers`、`TestGenericData`），共 260 行新增/36 行删除。仅影响 generic data 的 Parquet 读取路径，不影响 Spark/ORC/Flink 等其它读取器。
- **回迁到 1.4.x 的注意事项**：
  - 本提交依赖 `Types.NestedField.initialDefault()` API 与 `NestedField.withInitialDefault(...)` builder，这些是 schema 默认值特性的基础，必须确认 1.4.x 已具备（默认值特性是 v3/schema 演进的一部分，1.4.x 时期可能已部分支持）。
  - 依赖 `ParquetValueReaders.constant(value, maxDefinitionLevel)` 重载，需确认 1.4.x 的 `ParquetValueReaders` 已有该方法；若仅有单参 `constant(value)` 版本，需一并回迁该重载，否则嵌套场景定义层级不正确。
  - 回迁后应一并运行 `data` 模块的 Parquet 测试与 `DataTest` 套件，确认默认值语义在 generic data 路径上正确生效。
  - 若 1.4.x 的 `BaseParquetReaders` 与 main 已分叉（如已修复其它 bug），需手动合并，保留双方改动。
  - 与下一个提交（1500 Avro defaults）是同一特性的 Avro 对应版本，两者通常一起回迁以保证默认值语义在所有格式上一致。
