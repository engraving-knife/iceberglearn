# 提交 3543：Core: Fix StructLikeWrapper.equals exception with mismatched partition types (#15945)

## 提交信息

- **序号**：3543 / 4088
- **哈希**：0babf7d171d45fd0b2ab7de55f5002c2fbe0cf61
- **短哈希**：0babf7d17
- **日期**：2026-04-15 20:09:36 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Core: Fix StructLikeWrapper.equals exception with mismatched partition types (#15945)
- **PR/Issue**：#15945

## 总体目的

`StructLikeWrapper` 是 Iceberg 中用于包装 `StructLike`（如 `PartitionData`）并提供基于比较器的 `equals`/`hashCode` 实现的工具类，常用于将分区值作为 Map key 或 Set 元素。其 `equals` 方法通过 `comparator.compare(this.struct, that.struct) == 0` 判断两个 struct 是否相等。

问题在于：当两个 `StructLikeWrapper` 包装的 struct 类型不匹配时（例如一个是 IntegerType 分区数据，另一个是 StringType 分区数据），`comparator.compare(...)` 在尝试比较不同类型的数据时会抛出 `RuntimeException`（如 `ClassCastException`）。`equals` 方法抛异常违反了 Java `Object.equals` 契约——`equals` 应该返回 false 而非抛异常。

这种场景在实际中可能发生：例如表的分区规范演进后，同一张表的历史数据分区类型与新分区类型不一致，当它们被放入同一个 Set/Map 时就会触发异常。本提交在 `equals` 中捕获 `RuntimeException`，类型不匹配时返回 false 而非抛异常。

## 如何达成设计目的

在 `comparator.compare(...)` 调用外包裹 try-catch，捕获 `RuntimeException` 后返回 `false`。这样当比较器因类型不匹配无法比较时，`equals` 安全地返回「不相等」，符合 `Object.equals` 契约。注释说明了典型场景：`PartitionData` 的类型与数据不匹配时会发生此类异常。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/StructLikeWrapper.java` (+7/-1 lines)

**修改目的**：让 `equals` 在比较器抛异常时返回 false。

**工作逻辑**：
```java
-    return comparator.compare(this.struct, that.struct) == 0;
+    try {
+      return comparator.compare(this.struct, that.struct) == 0;
+    } catch (RuntimeException e) {
+      // An exception may occur, for example, when struct is PartitionData and its type does not
+      // match its data.
+      return false;
+    }
```
`equals` 方法此前已经做了引用相等、类型检查和 size 检查，但类型检查只检查 `StructLikeWrapper` 类本身，不检查内部的 struct 数据类型。捕获 RuntimeException 是对类型不匹配的兜底处理。

### `core/src/test/java/org/apache/iceberg/util/TestStructLikeWrapper.java` (+47/-0 lines, new file)

**修改目的**：回归测试类型不匹配时 `equals` 不抛异常。

**工作逻辑**：
```java
@Test
public void equalsTypeAndDataMismatch() {
  Types.StructType intType =
      Types.StructType.of(Types.NestedField.required(1, "a", Types.IntegerType.get()));
  Types.StructType stringType =
      Types.StructType.of(Types.NestedField.required(1, "a", Types.StringType.get()));

  PartitionData intData = new PartitionData(intType);
  intData.set(0, 1);

  PartitionData stringData = new PartitionData(stringType);
  stringData.set(0, "test");

  StructLikeWrapper integerStruct = StructLikeWrapper.forType(intType).set(intData);
  StructLikeWrapper stringStruct = StructLikeWrapper.forType(stringType).set(stringData);

  // StructLikeWrapper.equals previously threw an exception when the type and data mismatch
  assertThat(integerStruct).isNotEqualTo(stringStruct);
}
```
构造两个类型不同（IntegerType vs StringType）但字段名相同的 `PartitionData`，分别用对应类型的 `StructLikeWrapper` 包装，断言两者不相等。修复前会抛异常，修复后返回 false。

## 总结

本提交修复了 `StructLikeWrapper.equals` 在两个 wrapper 内部 struct 类型不匹配时抛出 `RuntimeException` 的问题，违反 `Object.equals` 契约。通过在比较器调用外包裹 try-catch，捕获异常后返回 false。这种场景可能发生在分区规范演进导致历史数据与新数据类型不一致时。配有专门的回归测试验证类型不匹配时 `equals` 安全返回 false。
