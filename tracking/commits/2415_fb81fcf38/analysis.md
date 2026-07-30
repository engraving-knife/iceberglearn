# 提交 2415：Core: Use zero-copy wrapper for equalityFieldIds (#13668)

## 提交信息

- **序号**：2415 / 4088
- **哈希**：fb81fcf3827fa14302507da04820f5858f834bfd
- **短哈希**：fb81fcf38
- **日期**：2025-07-25 10:19:44 -0600
- **作者**：Bruno Volpato
- **提交说明**：Core: Use zero-copy wrapper for equalityFieldIds (#13668)
- **PR/Issue**：#13668

## 总体目的

本提交优化了 `BaseFile.equalityFieldIds()` 方法的实现，使用零拷贝（zero-copy）包装器替代原有的数组到列表的转换，避免了不必要的数据拷贝。

`equalityFieldIds()` 返回等值删除（equality delete）操作涉及的字段 ID 列表。此前，该方法通过 `ArrayUtil.toIntList(equalityIds)` 将 `int[]` 数组转换为 `List<Integer>`，这个过程会创建新的 List 对象并逐个装箱（boxing）每个 int 值为 Integer 对象，产生内存分配和拷贝开销。

本提交使用 Guava 的 `Ints.asList(int[])` 方法，该方法返回一个直接 backed by 原始数组的 List 视图，不进行任何数据拷贝。再用 `Collections.unmodifiableList()` 包装使其不可变，保证了返回值的不可变性约束。

## 如何达成设计目的

1. 在 `ArrayUtil` 中新增 `toUnmodifiableIntList(int[])` 方法，使用 `Ints.asList()` 创建零拷贝列表视图，再用 `Collections.unmodifiableList()` 包装
2. 将 `BaseFile.equalityFieldIds()` 从调用 `toIntList` 改为调用 `toUnmodifiableIntList`
3. 在 `GuavaClasses` 中注册 `Ints` 类，确保 bundled-guava 模块包含该类

## 修改详情

### `bundled-guava/src/main/java/org/apache/iceberg/GuavaClasses.java` (+2/-0 lines)

**修改目的**：将 `com.google.common.primitives.Ints` 类注册到 bundled-guava 模块中。

**工作逻辑**：Iceberg 使用 bundled-guava 模块来重定位（relocate）Guava 类，避免与用户应用的 Guava 版本冲突。`GuavaClasses` 类列出了所有需要包含的 Guava 类。新增 `Ints.class.getName()` 确保该类被包含在 bundled jar 中。同时新增了对应的 import 语句。

### `core/src/main/java/org/apache/iceberg/util/ArrayUtil.java` (+9/-0 lines)

**修改目的**：新增零拷贝的不可变 int 数组到 List 转换方法。

**工作逻辑**：新增 `toUnmodifiableIntList(int[] ints)` 方法。当输入数组不为 null 时，使用 `Ints.asList(ints)` 创建一个直接引用原始数组的 List 视图（零拷贝），然后用 `Collections.unmodifiableList()` 包装返回不可变列表。当输入为 null 时返回 null。使用的是 Iceberg 重定位后的 Guava `Ints` 类（`org.apache.iceberg.relocated.com.google.common.primitives.Ints`）。

### `core/src/main/java/org/apache/iceberg/BaseFile.java` (+1/-1 lines)

**修改目的**：使用零拷贝包装器返回 equality field IDs。

**工作逻辑**：将 `equalityFieldIds()` 方法中的 `ArrayUtil.toIntList(equalityIds)` 替换为 `ArrayUtil.toUnmodifiableIntList(equalityIds)`。返回的列表现在是原始数组的零拷贝不可变视图，避免了数据拷贝和装箱开销。

## 总结

本提交通过使用 Guava 的 `Ints.asList()` 零拷贝包装器，优化了 `equalityFieldIds()` 的性能，避免了不必要的数组拷贝和 int 到 Integer 的装箱操作。同时确保返回不可变列表，保持了 API 的安全性。这是一个小而有效的性能优化。
