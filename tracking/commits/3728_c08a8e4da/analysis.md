# 提交 3728：Core: Use ArrayList for manifest list materialization (#15640)

## 提交信息

- **序号**：3728 / 4088
- **哈希**：c08a8e4daf44c972b1e1a2a74fb4dc9621745fff
- **短哈希**：c08a8e4da
- **日期**：2026-05-18 12:55:09 +0200
- **作者**：Manu Zhang
- **提交说明**：Core: Use ArrayList for manifest list materialization (#15640)
- **PR/Issue**：#15640

## 总体目的

本提交将 manifest list（清单列表）物化时所用的集合类型从 `LinkedList` 改为 `ArrayList`，以提升性能和内存效率。

`ManifestLists.read()` 用于读取快照的 manifest list 文件并返回 `List<ManifestFile>`，原实现使用 `Lists.newLinkedList(files)` 将读取到的 manifest 文件收集到链表中。同样地，`RewriteTablePathUtil.manifestFilesInSnapshot()` 也使用 `Lists.newLinkedList()` 初始化 manifest 文件列表。

`LinkedList` 相比 `ArrayList` 有以下劣势：
- 内存开销更大：每个节点都需要额外存储前驱/后继指针，对象头开销显著。
- 随机访问性能差：O(n) vs ArrayList 的 O(1)。
- 缓存局部性差：节点在堆上分散，无法利用 CPU 缓存。

对于 manifest list 这种通常只读、按索引遍历的场景，`ArrayList` 是更合适的选择。manifest list 在 Iceberg 表的读取、规划、提交等路径中被频繁访问，使用 `ArrayList` 可以减少内存占用和 GC 压力，提升整体性能。

## 如何达成设计目的

直接将 `Lists.newLinkedList(...)` 替换为 `Lists.newArrayList(...)`，无需改变方法签名（返回类型仍为 `List<ManifestFile>`），保持 API 兼容性。改动涉及两处：`ManifestLists.read()` 的返回值构造，以及 `RewriteTablePathUtil.manifestFilesInSnapshot()` 的局部变量初始化。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ManifestLists.java` (+1/-1 lines)

**修改目的**：将 manifest list 读取结果的集合类型从 LinkedList 改为 ArrayList。

**工作逻辑**：
`ManifestLists.read()` 使用 try-with-resources 打开 manifest list 文件并通过 `ManifestReader` 读取所有 `ManifestFile`，将结果收集到 List 中返回。将 `Lists.newLinkedList(files)` 改为 `Lists.newArrayList(files)`，使返回的列表为基于数组的实现，提升后续访问性能。

```java
return Lists.newArrayList(files);  // 原为 Lists.newLinkedList(files);
```

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (+1/-1 lines)

**修改目的**：将 rewrite table path 工具中的 manifest 文件列表初始化改为 ArrayList。

**工作逻辑**：
`manifestFilesInSnapshot()` 方法从快照的 manifest list location 读取 manifest 文件列表。原代码先初始化一个空的 `LinkedList`，然后在 try 块中重新赋值为 `ManifestLists.read()` 的结果。将初始化语句从 `Lists.newLinkedList()` 改为 `Lists.newArrayList()`，与 `ManifestLists.read()` 返回类型保持一致。

```java
List<ManifestFile> manifestFiles = Lists.newArrayList();  // 原为 Lists.newLinkedList();
```

## 总结

本提交将 manifest list 物化时使用的集合类型从 `LinkedList` 改为 `ArrayList`，是一个面向性能的微优化。`ArrayList` 在内存开销、随机访问性能和缓存局部性方面均优于 `LinkedList`，更适合 manifest list 这种按索引遍历、不频繁增删的场景。改动简单且不影响 API 兼容性，但能在 manifest list 频繁访问的读/写路径上减少内存占用和 GC 压力。
