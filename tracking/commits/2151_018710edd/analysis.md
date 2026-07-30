# 提交 2151：Parquet: Fix redundant type conversion in parquet schema

## 提交信息

- **序号**：2151 / 4088
- **哈希**：018710edd56652e915fe858cba512b3064efcfa9
- **短哈希**：018710edd
- **日期**：2025-05-21 00:18:13 -0700
- **作者**：Leon Lin
- **提交说明**：Parquet: Fix redundant type conversion in parquet schema (#13114)
- **PR/Issue**：#13114

## 总体目的

这个提交修复了 TypeToMessageType 类中 Parquet schema 转换时的冗余类型转换问题。在将 Iceberg schema 转换为 Parquet message type 的过程中，代码先调用 `field(field)` 方法获取字段类型并赋值给局部变量 `fieldType`，然后又重复调用 `field(field)` 方法将结果传给 `builder.addField()`。这意味着同一个字段类型转换逻辑被执行了两次，不仅造成了不必要的性能开销，还可能在某些场景下导致问题。修复方法是将已获取的 `fieldType` 变量直接传递给 `builder.addField()`，避免重复调用。

## 如何达成设计目的

1. 在两处代码中，将 `builder.addField(field(field))` 替换为 `builder.addField(fieldType)`，复用已获取的局部变量。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/TypeToMessageType.java` (修改, +2/-2 lines)

**修改目的**：消除冗余的类型转换调用。

**工作逻辑**：在 TypeToMessageType 类的两个方法中（分别对应 struct 类型和 list/map 等嵌套类型的字段处理），代码先将 `field(field)` 的结果存入 `fieldType` 变量并做 null 检查，然后又调用 `field(field)` 传给 `builder.addField()`。修改为直接使用 `fieldType` 变量：`builder.addField(fieldType)`。这避免了重复调用 `field()` 方法，消除了冗余的类型转换。

## 总结

这个提交修复了一个代码冗余问题，在 Parquet schema 转换中避免了重复调用 `field()` 方法。虽然功能上没有改变（两次调用结果相同），但修复后提高了代码的清晰度和执行效率，是一个良好的代码质量改进。
