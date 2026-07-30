# 提交 3755：Arrow: Fix ClassCastException in vectorized reader on int-to-long promotion (#16343)

## 提交信息

- **序号**：3755 / 4088
- **哈希**：e3a4c6422423d8f9c41689098fa6c3c3f0e8a721
- **短哈希**：e3a4c6422
- **日期**：2026-05-20 15:54:20 -0700
- **作者**：Xiening Dai
- **提交说明**：Arrow: Fix ClassCastException in vectorized reader on int-to-long promotion (#16343)
- **PR/Issue**：#16343

## 总体目的

本提交修复了 Arrow 向量化读取器在 int-to-long 类型提升（type promotion）场景下抛出 `ClassCastException` 的 Bug。

**问题场景**：当 Iceberg 表的某列从 `int` 提升为 `long`（通过 `ALTER TABLE ... ALTER COLUMN`）后，使用向量化读取器读取该列在提升前写入的 Parquet 文件（物理类型为 INT32，带有 `INT(32, true)` 逻辑类型注解）时，会抛出 `ClassCastException: BigIntVector cannot be cast to IntVector`。

**根本原因**：在 `VectorizedArrowReader` 的 `LogicalTypeVisitor.visit(IntLogicalTypeAnnotation)` 中，原先通过 `arrowField.createVector(rootAlloc)` 创建 Arrow 向量，而 `arrowField` 是基于 Iceberg schema 类型（反映提升后的 `LongType`）构建的，因此创建的是 `BigIntVector`。随后 `LogicalTypeVisitor` 根据 Parquet 文件的 `INT(32)` 逻辑类型注解，将该向量强制转换为 `IntVector`，导致类型不匹配的 `ClassCastException`。

**对比非向量化路径**：非向量化的 `BaseParquetReaders` 已正确处理此场景——它检查预期的 Iceberg 类型，对提升场景使用 `IntAsLongReader`。向量化读取器则依赖 accessor 层进行类型拓宽（`IntAccessor.getLong()` 将 int 拓宽为 long），因此向量需要匹配物理数据布局（INT32 对应 IntVector），而非 Iceberg schema 类型。

## 如何达成设计目的

修复思路是让 `LogicalTypeVisitor` 根据 Parquet 的物理类型（由逻辑类型注解的 bitWidth 决定）分配向量，而非从基于 Iceberg schema 类型构建的 `arrowField` 创建向量。具体地：
- 对于 bitWidth 为 8/16/32 的 INT 逻辑类型：创建一个新的 `Field`，其类型为 `ArrowType.Int(Integer.SIZE, true)`（即 32 位有符号整数），从中创建 `IntVector`。
- 对于 bitWidth 为 64 的 INT 逻辑类型：创建一个新的 `Field`，其类型为 `ArrowType.Int(Long.SIZE, true)`（即 64 位有符号整数），从中创建 `BigIntVector`。

这样向量类型始终与 Parquet 物理类型一致，accessor 层负责将 int 拓宽为 long，避免类型转换异常。

同时新增三个测试用例覆盖不同场景。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java` (+14/-1 lines)

**修改目的**：修复 `LogicalTypeVisitor` 根据 Parquet 物理类型而非 Iceberg schema 类型分配向量。

**工作逻辑**：
原实现中，`visit(IntLogicalTypeAnnotation)` 方法在方法开头通过 `arrowField.createVector(rootAlloc)` 创建向量（`arrowField` 基于 Iceberg schema 类型，提升后为 LongType，产生 BigIntVector），然后在 bitWidth==32 分支中将其强转为 `IntVector`，导致异常。

修复后，移除方法开头的向量创建，改为在各分支内根据物理类型创建对应向量：
- bitWidth 8/16/32 分支：
```java
Field intField =
    new Field(
        icebergField.name(),
        new FieldType(
            icebergField.isOptional(), new ArrowType.Int(Integer.SIZE, true), null, null),
        null);
FieldVector vector = intField.createVector(rootAlloc);
((IntVector) vector).allocateNew(batchSize);
```
  创建 32 位有符号整数类型的 Field 和 IntVector，与 Parquet INT32 物理类型匹配。
- bitWidth 64 分支：
```java
Field longField =
    new Field(
        icebergField.name(),
        new FieldType(
            icebergField.isOptional(), new ArrowType.Int(Long.SIZE, true), null, null),
        null);
FieldVector vector = longField.createVector(rootAlloc);
((BigIntVector) vector).allocateNew(batchSize);
```
  创建 64 位有符号整数类型的 Field 和 BigIntVector，与 Parquet INT64 物理类型匹配。

新创建的 Field 复用 `icebergField` 的 name 和 optional 性，但类型基于 Parquet 物理类型。

### `arrow/src/test/java/org/apache/iceberg/arrow/vectorized/TestArrowReader.java` (+239/-0 lines)

**修改目的**：新增测试覆盖 int-to-long 提升场景下的向量化读取。

**工作逻辑**：
新增三个测试用例：

1. **`testIntToLongPromotionWithLogicalType`**：复现报告的崩溃场景。
   - 创建 int 列的表。
   - 手工构造带 `INT(32, true)` 逻辑类型注解的 Parquet 文件（模拟 PyArrow、Spark native 等非 Iceberg 写入器产生的文件），写入 int 值 `[1, 2, 3, Integer.MAX_VALUE]`。
   - 将列类型从 int 提升为 long。
   - 用向量化读取器读取，验证：
     - 向量类型为 `IntVector`（匹配物理类型）。
     - `getLong(i)` 正确返回 long 值（accessor 拓宽）。

2. **`testIntToLongPromotionWithoutLogicalType`**：验证无逻辑类型注解的场景。
   - 创建 int 列的表。
   - 通过 Iceberg 自己的 writer 写入 Parquet 文件（产生 bare INT32，无逻辑类型注解）。
   - 将列类型从 int 提升为 long。
   - 用向量化读取器读取，验证向量类型为 `IntVector`，`getLong` 正确拓宽。

3. **`testIntToLongPromotionWithLargeValuesAndReuseContainers`**：验证提升后写入大值（超过 Integer.MAX_VALUE）并启用 reuseContainers 的混合读取场景。
   - 写入提升前的 int 文件（带 INT(32, true) 注解，值 `[1, 2, Integer.MAX_VALUE]`）。
   - 提升列类型为 long。
   - 写入提升后的 long 文件（值包含 `Long.MAX_VALUE` 等大值）。
   - 用向量化读取器读取两个文件，验证 int 文件的向量为 IntVector、long 文件的向量为 BigIntVector，且所有值正确读取。
   - 该测试确保混合读取（pre-promotion int 文件 + post-promotion long 文件）在 reuseContainers 启用时正常工作。

## 总结

本提交修复了 Arrow 向量化读取器在 int-to-long 类型提升场景下抛出 `ClassCastException` 的 Bug。根本原因是 `LogicalTypeVisitor` 从基于 Iceberg schema 类型（提升后的 LongType）的 arrowField 创建 BigIntVector，却根据 Parquet 物理类型 INT32 强转为 IntVector。修复方式是改为根据 Parquet 物理类型（bitWidth）直接创建对应类型的 Field 和 Vector（IntVector 或 BigIntVector），让向量匹配物理数据布局，由 accessor 层负责 int 到 long 的拓宽。新增三个测试覆盖带/不带逻辑类型注解、混合读取 + reuseContainers 等场景。这是一个面向数据正确性和类型兼容性的重要修复。
