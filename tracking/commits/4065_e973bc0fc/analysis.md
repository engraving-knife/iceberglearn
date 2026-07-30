# 提交 4065：API, Core: Use fixed transform result types when source type is unknown (#17262)

## 提交信息

- **序号**：4065 / 4088
- **哈希**：e973bc0fcd05514466fd0e201c34ece2b396c4fd
- **短哈希**：e973bc0fc
- **日期**：2026-07-17 15:08:09 -0700
- **作者**：Anoop Johnson
- **提交说明**：API, Core: Use fixed transform result types when source type is unknown (#17262)
- **PR/Issue**：#17262

## 总体目的

这个提交修复了 `PartitionSpec.partitionType()` 在分区字段源列被删除（dropped）后，结果类型降级不正确的问题。

Iceberg 的分区规范（PartitionSpec）中，每个分区字段（PartitionField）有一个 transform（如 identity、bucket、truncate、year、month 等）和一个源列 ID。`partitionType()` 方法计算分区结构类型时，需要为每个分区字段确定结果类型。

此前的实现中，当源列已从 schema 中删除（`schema.findType(field.sourceId())` 返回 null）时，代码会直接将结果类型设为 `UnknownType`。这对 identity 和 truncate 这类结果类型依赖于源类型的 transform 是正确的，但对 bucket、year、month、day、hour 等结果类型固定（不依赖源类型）的 transform 来说是错误的——例如 `bucket` 总是返回 `IntegerType`，`year` 总是返回 `IntegerType`，即使源列被删除，这些 transform 的结果类型仍然是确定的。

本提交将「源类型为 null 时直接返回 UnknownType」改为「将 UnknownType 作为源类型传给 transform 的 `getResultType()`」，让 transform 自行决定结果类型。对于结果类型固定的 transform（bucket、date/time transforms），它们会返回固定的结果类型；对于结果类型等于源类型的 transform（identity、truncate），会返回 UnknownType。这与 `javaClasses()` 方法中已有的行为保持一致。

## 如何达成设计目的

提取一个私有方法 `resultType(PartitionField field)`，统一处理源类型查找和结果类型推导：
- 查找源类型，若为 null 则替换为 `UnknownType.get()`。
- 调用 `field.transform().getResultType(sourceType)` 让 transform 自行推导结果类型。

将 `partitionType()` 和 `javaClasses()` 中原先各自内联的逻辑都改为调用这个统一方法，消除重复并保证两者行为一致。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java` (+32/-17 lines, 净 +15)

**修改目的**：统一分区字段结果类型推导逻辑，让固定结果类型的 transform 在源列删除后仍返回正确类型。

**工作逻辑**：

新增私有方法：
```java
private Type resultType(PartitionField field) {
  Type sourceType = schema.findType(field.sourceId());
  if (sourceType == null) {
    // when the source field has been dropped, substitute unknown and let the transform derive
    // its result type; transforms that return the source type (identity, truncate, void) yield
    // unknown, while transforms with a fixed result type still resolve
    sourceType = Types.UnknownType.get();
  }
  return field.transform().getResultType(sourceType);
}
```

`partitionType()` 方法简化为：
```java
structFields.add(
    Types.NestedField.optional(field.fieldId(), field.name(), resultType(field)));
```
原先内联的 `findType` + `getResultType` + null 检查逻辑被移除。

`javaClasses()` 方法中，非 `UnknownTransform` 分支也改为调用 `resultType(field).typeId().javaClass()`，原先内联的 null 检查逻辑被移除，两者现在通过统一方法保持一致。

### `core/src/test/java/org/apache/iceberg/TestPartitioning.java` (+46/-0 lines)

**修改目的**：验证源列删除后固定结果类型 transform 的行为。

**工作逻辑**：

`testPartitionTypeWithDroppedSourceColumn`：
- 创建含 `data`（string）和 `category` 列的表，分区为 `identity(data)` + `bucket(4, category)`。
- 删除 `category_bucket` 分区字段，再删除 `category` 列。
- 断言 `partitionType()` 中 `category_bucket` 仍为 `IntegerType`（bucket 的固定结果类型），`data` 仍为 `StringType`。
- 再删除 `data` 分区字段和 `data` 列。
- 断言 `data` 变为 `UnknownType`（identity 结果类型等于源类型），`category_bucket` 仍为 `IntegerType`。

`testPartitionTypeWithUnknownTransformAndDroppedSourceColumn`：
- 测试 `UnknownTransform`（引擎使用了本库不支持的 transform）场景。
- 当源列也删除时，`UnknownTransform` 的固定结果类型（StringType）仍然返回，而非降级为 unknown。

## 总结

这个提交修复了分区字段源列删除后结果类型推导的一个精度问题：此前对所有 transform 统一降级为 UnknownType，现在让 transform 自行推导，使 bucket、date/time 等固定结果类型的 transform 在源列删除后仍返回正确类型。通过提取统一的 `resultType()` 方法消除了 `partitionType()` 和 `javaClasses()` 之间的重复逻辑并保证两者一致。测试覆盖了 bucket、identity 和 UnknownTransform 三种场景。这是对分区元数据正确性的改进，有助于在 schema 演进（列删除）后仍正确推断分区类型。
