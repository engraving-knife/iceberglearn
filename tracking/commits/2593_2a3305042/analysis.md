# 提交 2593：Arrow: Add a precondition check in allocateFieldVector (#13949)

## 提交信息

- **序号**：2593 / 4088
- **哈希**：2a33050420ac7e9f92985dd5d8b983ce38ee7029
- **短哈希**：2a3305042
- **日期**：2025-09-04 11:07:03 -0500
- **作者**：Huaxin Gao
- **提交说明**：Arrow: Add a precondition check in allocateFieldVector (#13949)
- **PR/Issue**：#13949

## 总体目的

本次提交修复了 `VectorizedArrowReader` 中一个潜在的内存泄漏/双重分配问题，通过添加前置条件检查确保在分配新的 Arrow 字段向量前，旧的向量实例已被正确释放。

在 Iceberg 的向量化读取路径中，`VectorizedArrowReader` 负责将 Parquet 列数据读入 Apache Arrow 的向量中。当读取器需要重新分配向量时（例如类型变化或重复使用读取器），会先关闭旧向量再分配新向量。然而，如果调用路径中存在遗漏（未先关闭旧向量就调用 `allocateFieldVector`），可能导致内存泄漏或状态不一致。

## 如何达成设计目的

1. 在调用 `allocateFieldVector` 前关闭旧向量的地方，增加 `vec = null` 赋值，确保引用被清除。
2. 在 `allocateFieldVector` 方法入口添加 `Preconditions.checkState(vec == null, ...)` 断言，确保调用该方法时 `vec` 必须为 null。如果检测到 vec 非 null，说明调用方未正确释放前一个向量，会立即抛出异常而非静默泄漏内存。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java` (+5/-0 lines)

**修改目的**：防止向量双重分配导致的内存泄漏。

**工作逻辑**：

第一处修改在关闭旧向量的代码块中：
```java
if (vec != null) {
    vec.close();
    vec = null;  // 新增
}
```
在 `vec.close()` 之后立即将 `vec` 置为 null。这确保后续的 `allocateFieldVector` 前置检查能通过。

第二处修改在 `allocateFieldVector` 方法入口：
```java
private void allocateFieldVector(boolean dictionaryEncodedVector) {
    // Allocate-only: caller must ensure there is no active vector in use.
    Preconditions.checkState(
        vec == null,
        "Allocation must be called only when the previous vector instance was released");
    ...
}
```
这是一个防御性检查：如果调用 `allocateFieldVector` 时 `vec` 不为 null，说明前一个向量未被释放，直接抛出 `IllegalStateException`。注释明确说明这是"仅分配"方法，调用者必须确保没有正在使用的向量。

## 总结

这是一个防御性编程改进，通过 fail-fast 机制（前置条件检查）将潜在的内存泄漏问题转化为快速可见的异常。这种模式比静默地覆盖引用（导致旧向量无法被 GC 回收）更安全，能在开发和测试阶段及早发现问题。配合 `vec = null` 的赋值，确保了正常的调用路径不会被误判。
