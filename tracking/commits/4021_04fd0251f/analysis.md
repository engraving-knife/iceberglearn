# 提交 4021：Flink: handle simultaneous schema evolution and data conversion (#17024)

## 提交信息

- **序号**：4021 / 4088
- **哈希**：04fd0251f6173a5a017dd0af82e84c39953199a4
- **短哈希**：04fd0251f
- **日期**：2026-07-13 13:45:49 +0200
- **作者**：Han You
- **提交说明**：Flink: handle simultaneous schema evolution and data conversion (#17024)
- **PR/Issue**：#17024

## 总体目的

本提交修复 Flink sink 动态 schema 演进中，当某字段类型变更可以由数据转换（data conversion）处理时，`EvolveSchemaVisitor` 仍然错误地标记需要 schema 更新（type update）的 bug。

场景：当上游数据 schema 与表 schema 的字段类型不同，但 `DataConverter` 能够做类型转换（如 int→long、float→double、date→timestamp、decimal 扩展 precision）时，`CompareSchemasVisitor.primitive` 会返回 `DATA_CONVERSION_NEEDED`。但 `EvolveSchemaVisitor.primitive` 在判断是否需要 `needsTypeUpdate` 时，只检查"类型不同"，没有考虑该类型差异已由数据转换处理，导致它额外触发一次 schema 更新（把表列类型改成数据类型），这与数据转换的意图冲突——数据转换的目的就是让数据类型适配表类型，不需要改表 schema。

本提交让 `EvolveSchemaVisitor` 在类型差异可由数据转换处理时，不标记 `needsTypeUpdate`，正确处理"同时发生 schema 演进和数据转换"的场景。

## 如何达成设计目的

1. 将 `CompareSchemasVisitor.primitive` 中的数据转换可行性判断逻辑抽取为静态方法 `isDataConversionPossible(Type dataType, Type tableType)`，集中维护"哪些类型转换 DataConverter 支持"的规则，并加注释要求与 `DataConverter#get` 保持同步。
2. `primitive` 方法简化为：类型相同→SAME；`isDataConversionPossible`→DATA_CONVERSION_NEEDED；否则→SCHEMA_UPDATE_NEEDED。同时修正了原代码对非原始类型未先检查 `isPrimitiveType` 的潜在问题。
3. `EvolveSchemaVisitor.primitive` 中新增 `handledByDataConversion` 判断，`needsTypeUpdate` 增加 `&& !handledByDataConversion` 条件，避免对可转换的类型差异触发 schema 更新。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/CompareSchemasVisitor.java` (+43/-30 lines)

**修改目的**：抽取 `isDataConversionPossible` 静态方法并简化 `primitive`。

**工作逻辑**：
- `primitive` 简化为：
  ```java
  if (primitive.equals(tableSchemaType)) return Result.SAME;
  else if (isDataConversionPossible(primitive, tableSchemaType)) return Result.DATA_CONVERSION_NEEDED;
  else return Result.SCHEMA_UPDATE_NEEDED;
  ```
- `isDataConversionPossible(dataType, tableType)`：
  - 先校验两者都是原始类型（修复原代码未检查的隐患）。
  - 依次判断 int→long、float→double、date→timestamp(withoutZone)、decimal 同 scale 且 precision 扩展，返回 true；否则 false。
  - 注释明确要求与 `DataConverter#get` 的转换保持同步。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/EvolveSchemaVisitor.java` (+4/-2 lines)

**修改目的**：避免对可数据转换的类型差异触发 schema 更新。

**工作逻辑**：
```java
boolean handledByDataConversion =
    CompareSchemasVisitor.isDataConversionPossible(targetField.type(), existingField.type());
boolean needsTypeUpdate =
    targetField.type().isPrimitiveType()
        && !targetField.type().equals(existingField.type())
        && !handledByDataConversion;  // 新增条件
```
当类型差异可由 DataConverter 处理时，不标记 `needsTypeUpdate`，让数据转换负责适配。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestEvolveSchemaVisitor.java` (+38/-14 lines)

**修改目的**：新增测试验证可转换类型不触发 schema 更新。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestRowDataConverter.java` (+38/-14 lines)

**修改目的**：新增测试验证数据转换在 schema 演进场景下的正确性。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestTableUpdater.java` (+88/-0 lines)

**修改目的**：新增 `TestTableUpdater` 测试验证 table updater 不会对可转换类型做多余 schema 更新。

## 总结

本提交修复了 Flink sink 动态 schema 演进中数据转换与 schema 更新冲突的 bug。通过抽取 `isDataConversionPossible` 集中维护可转换类型规则，并让 `EvolveSchemaVisitor` 在类型差异可由数据转换处理时跳过 schema 更新，正确处理了"同时 schema 演进 + 数据转换"的场景。修复避免了不必要的表 schema 变更（把列类型改成数据类型而非保持表类型），保障了数据转换的预期行为。配套补齐了三个测试类的覆盖。该提交随后在 4027 被 backport。
