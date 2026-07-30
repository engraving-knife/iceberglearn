# 提交 3299：Build: Bump org.roaringbitmap:RoaringBitmap from 1.3.0 to 1.6.10 (#15404)

## 提交信息

- **序号**：3299 / 4088
- **哈希**：e74562d051ca834b4e2d6034b94d0c0966252945
- **短哈希**：e74562d05
- **日期**：2026-02-22
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 1.3.0 to 1.6.10 (#15404)
- **PR/Issue**：#15404

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 RoaringBitmap 从 1.3.0 升级到 1.6.10。RoaringBitmap 是一种高性能的压缩位图数据结构，对稀疏和密集的整数集合均能保持较低的内存占用和快速的集合运算性能。与传统的位图相比，RoaringBitmap 通过将 32/64 位整数空间分桶并针对不同密度采用不同的容器（数组容器、位图容器、运行长度编码容器），在内存和速度上都有显著优势。

在 Iceberg 中，RoaringBitmap 是位置删除（position delete）机制的核心数据结构。具体而言，`core` 模块的 `BitmapPositionDeleteIndex` 类使用 `Roaring64Bitmap` 来记录被删除数据文件中的行位置，`SortingPositionOnlyDeleteWriter` 在写入位置删除文件时使用 `PeableLongIterator` 遍历位图中的删除位置。这些组件直接影响 Iceberg 表的行级删除（row-level delete）性能，包括 COPY-ON-WRITE 和 MERGE-ON-READ 两种删除模式的执行效率。

本次升级跨三个次版本（1.3.0 → 1.6.10），`update-type` 为 `version-update:semver-minor`。次版本升级通常包含新功能、性能优化和 bug 修复，按照语义化版本约定应保持向后兼容。对于 Iceberg 而言，RoaringBitmap 的性能优化（如更快的序列化/反序列化、更优的内存布局）会直接惠及位置删除索引的构建与查询效率，尤其在处理大量删除行的场景下。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `roaringbitmap` 版本变量，从 `1.3.0` 提升到 `1.6.10`。项目中 RoaringBitmap 库声明通过 `version.ref = "roaringbitmap"` 引用该变量，因此一处修改即完成升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 RoaringBitmap 版本变量。

**工作逻辑**：
将第 84 行的 `roaringbitmap = "1.3.0"` 改为 `roaringbitmap = "1.6.10"`。该变量在 `[libraries]` 段中被 `roaringbitmap = { module = "org.roaringbitmap:RoaringBitmap", version.ref = "roaringbitmap" }` 引用，进而被 core 模块的 `BitmapPositionDeleteIndex`、`SortingPositionOnlyDeleteWriter` 等类用于构建位置删除索引。升级后，这些组件在序列化删除位图到 manifest、读取时反序列化、以及执行集合运算（如合并多个删除文件的位图）时，将使用 RoaringBitmap 1.6.x 的优化实现。

## 总结

本次提交将 RoaringBitmap 升级三个次版本至 1.6.10，作为位置删除索引的底层数据结构，该升级有望带来序列化性能和内存效率的改进，直接惠及 Iceberg 行级删除操作。作为 semver-minor 级别升级，保持 API 向后兼容，属于对核心删除链路有实际性能价值的依赖维护。
