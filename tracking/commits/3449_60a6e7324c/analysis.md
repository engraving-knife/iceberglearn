# 提交 3449：Core: Fix rewrite_position_delete_files failure with array/map columns (#15632)

## 提交信息

- **序号**：3449 / 4088
- **哈希**：60a6e7324c4b6b234882f9d464c9ddf6cbcb6bd3
- **短哈希**：60a6e7324c
- **日期**：2026-03-23 11:50:56 -0700
- **作者**：Szehon Ho
- **提交说明**：Core: Fix rewrite_position_delete_files failure with array/map columns (#15632)
- **PR/Issue**：#15632

## 总体目的

修复当表包含 array/map 类型列时，`rewrite_position_delete_files` 存储过程失败的问题。根本原因在于 `ExpressionUtil.extractByIdInclusive()` 方法使用 `identitySpec` 创建分区规范，而分区规范不支持 array/map 等非原始类型，导致构建分区规范时抛出异常。

## 如何达成设计目的

- 重写 `ExpressionUtil.extractByIdInclusive()` 方法，不再使用 `PartitionSpec` 和 `Projections` 机制
- 改为直接遍历表达式树，通过新的 `RetainPredicatesByFieldIdVisitor` 保留引用指定字段 ID 的谓词
- 修改 `PositionDeletesRowReader`，使其保留所有非恒定字段 ID（不再仅限于原始类型字段）

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java` (+93/-31 lines)

**修改目的**：重写 `extractByIdInclusive` 方法，使其支持 array/map 等嵌套类型。

**工作逻辑**：

1. **方法签名变更**：
   - 旧实现：创建 `identitySpec` 并使用 `Projections.inclusive()` 投影
   - 新实现：使用 `RetainPredicatesByFieldIdVisitor` 直接遍历表达式

2. **新增空 ID 处理**：
   ```java
   if (ids == null || ids.length == 0) {
       return Expressions.alwaysTrue();
   }
   ```

3. **新增 `RetainPredicatesByFieldIdVisitor` 内部类**：
   - 继承 `ExpressionVisitor<Expression>`
   - 对 and/or/not 表达式递归处理
   - 对 `BoundPredicate`：检查字段 ID 是否在 retainFieldIds 集合中，是则保留谓词，否则返回 `alwaysTrue()`
   - 对 `UnboundPredicate`：先绑定到 schema，再按相同逻辑处理
   - 关键：不再依赖 PartitionSpec，因此支持所有类型包括 array/map

4. **删除 `identitySpec` 方法**：不再需要通过分区规范来过滤表达式。

### `api/src/test/java/org/apache/iceberg/expressions/TestExpressionUtil.java` (+158/-0 lines)

**修改目的**：添加 `extractByIdInclusive` 的测试用例。

**工作逻辑**：
- `testExtractByIdInclusive()`：测试基本场景，包括空 ID、单个谓词、AND/OR 组合
- `testExtractByIdInclusiveNestedTypes()`：测试嵌套类型（struct、list、map），验证方法正确处理这些之前会失败的场景

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/PositionDeletesRowReader.java` (+8/-13 lines)

**修改目的**：修改 PositionDeletesRowReader 以保留所有非恒定字段（包括 array/map）。

**工作逻辑**：
- 旧实现：`nonConstantFieldIds()` 方法只保留原始类型字段（`isPrimitiveType()`）
- 新实现：直接使用 `expectedSchema().idToName().keySet()` 中所有不在 constantsMap 中的字段 ID
- 删除了 `nonConstantFieldIds()` 私有方法
- 这样 array/map 列的字段 ID 也会被传递给 `extractByIdInclusive`，而新的实现可以正确处理它们

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFilesProcedure.java` (+62/-0 lines)

**修改目的**：添加包含 array/map 列的 rewrite_position_delete_files 测试。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestPositionDeletesTable.java` (+54/-0 lines)

**修改目的**：添加包含 array/map 列的 position_deletes 元数据表测试。

## 总结

该提交修复了表包含 array/map 类型列时 `rewrite_position_delete_files` 失败的问题。根本原因是 `ExpressionUtil.extractByIdInclusive()` 通过 `PartitionSpec` 实现过滤，而分区规范不支持嵌套类型。修复方案是重写该方法，使用表达式访问者直接遍历并保留引用指定字段 ID 的谓词，同时修改 `PositionDeletesRowReader` 保留所有非恒定字段（不再限制为原始类型）。
