# 提交 0206：Core: Remove deprecated code in DeleteFileIndex (#9166)

## 提交信息

- **序号**：0206 / 4088
- **哈希**：d247b20f166ccb0b92443d4b05330b1e0d9c5d49
- **短哈希**：d247b20f1
- **日期**：2023-11-28
- **作者**：Anton Okolnychyi
- **提交说明**：Core: Remove deprecated code in DeleteFileIndex (#9166)
- **PR/Issue**：#9166

## 总体目的

Iceberg 在 1.4.0 版本对 `DeleteFileIndex` 内部表示做了一次重构：原本以 `long[]` 序列号数组和 `DeleteFile[]` 数组的 `Pair` 形式存储删除文件（分别针对全局删除与按分区删除），被替换为更内聚的 `DeleteFileGroup` 类型。重构时为了保持向后兼容、避免一次性破坏所有内部调用方，作者保留了一个旧的 `@Deprecated` 包级构造函数以及三个仅供该构造函数使用的 `index(...)` 辅助静态方法，标记为 "since 1.4.0, will be removed in 1.5.0"。

本提交是 1.5.0 开发周期内的清理工作：在兼容宽限期结束后，把上述被标记为废弃的构造函数及其依赖的三个 `index` 静态方法一并删除。这是 Apache Iceberg 一贯的"先标记 deprecated、下个 minor 版本移除"演进节奏的体现，目的是让 `DeleteFileIndex` 的对外构造入口收敛到 `Builder` + 内部私有构造函数一条路径，消除重复表示，降低后续在 `DeleteFileGroup` 上继续演进时的兼容性包袱。

对 Iceberg 演进的意义在于：保持 core 内部数据结构的整洁，避免新旧两套表示长期并存导致阅读者混淆，也便于后续在删除文件索引上叠加新的优化（如列统计过滤）时不需要维护两份代码路径。

## 如何达成设计目的

提交整体是纯删除性改动，只动了一个文件 `core/src/main/java/org/apache/iceberg/DeleteFileIndex.java`，删除 41 行，没有任何新增。设计思路很简单：既然 1.4.0 已经引入 `DeleteFileGroup` 作为内部统一表示，且旧的 `Pair<long[], DeleteFile[]>` 表示在 1.4.0 时已经标记为废弃，那么在 1.5.0 周期里直接物理删除废弃入口即可。被删除的代码分两块：一个包级可见的 `@Deprecated` 构造函数，以及只服务于该构造函数的三个 `index(...)` 私有静态方法（分别用于把单个 `Pair` 转为 `DeleteFileGroup`、把 `long[]+DeleteFile[]` 转为 `DeleteFileGroup`、把整张按分区 `Map` 转为按分区 `DeleteFileGroup` 的 `Map`）。删除后，类内只剩私有构造函数 + `Builder` 一条对外构建路径，所有内部数据全部以 `DeleteFileGroup` 形态持有。

## 修改详情

### `core/src/main/java/org/apache/iceberg/DeleteFileIndex.java`

**修改目的**：移除 1.4.0 引入的废弃构造函数及其专属辅助方法，使 `DeleteFileIndex` 的构造入口收敛到 `Builder` + 私有构造函数。

**工作逻辑**：

被删除的内容有两处：

1. 字段声明区后方的废弃构造函数：

```java
/** @deprecated since 1.4.0, will be removed in 1.5.0. */
@Deprecated
DeleteFileIndex(
    Map<Integer, PartitionSpec> specs,
    long[] globalSeqs,
    DeleteFile[] globalDeletes,
    Map<Pair<Integer, StructLikeWrapper>, Pair<long[], DeleteFile[]>> deletesByPartition) {
  this(specs, index(specs, globalSeqs, globalDeletes), index(specs, deletesByPartition), true);
}
```

该构造函数接受旧式的 `long[]` 序列号数组 + `DeleteFile[]` 数组（分别针对全局删除和按分区删除的 `Map`），内部通过三个 `index` 辅助方法转换成 `DeleteFileGroup` 后委托给私有构造函数。这是 1.4.0 重构前调用方使用的接口，1.4.0 之后调用方应改用 `builderFor(...)`。

2. 类尾部、`builderFor` 工厂方法之前的三个 `index` 静态辅助方法：

- `index(Map<Integer, PartitionSpec> specs, Pair<long[], DeleteFile[]> pairs)`：把单个 `Pair` 转成 `DeleteFileGroup`，内部委托给下面的重载。
- `index(Map<Integer, PartitionSpec> specs, long[] seqs, DeleteFile[] files)`：核心转换逻辑——为每个 `DeleteFile` 查找其 `PartitionSpec` 与对应的 `applySequenceNumber`，包装成 `IndexedDeleteFile[]`，再与 `seqs` 一起构造 `DeleteFileGroup`。空数组直接返回 `null`。
- `index(Map<Integer, PartitionSpec> specs, Map<Pair<Integer, StructLikeWrapper>, Pair<long[], DeleteFile[]>> deletesByPartition)`：遍历按分区的 `Map`，对每个 `Pair` 调用上面的转换，产出 `Map<Pair<Integer, StructLikeWrapper>, DeleteFileGroup>`。

这三个方法只服务于上述废弃构造函数，没有其他调用点，因此随构造函数一起删除，不留任何遗留。删除后类的字段（`globalDeletes`、`deletesByPartition`）已全部为 `DeleteFileGroup` 类型，`index(...)` 这层"旧表示 → 新表示"的桥接彻底消失。

## 小结

本提交是 Iceberg 内部 API 演进的收尾：按既定的 1.4.0 标记废弃、1.5.0 物理移除节奏，删除 `DeleteFileIndex` 中已被 `DeleteFileGroup` 取代的旧构造函数与桥接方法，使删除文件索引只保留单一的、基于 `Builder` 的构建路径。
