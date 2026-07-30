# 提交 3326：Build: Bump org.roaringbitmap:RoaringBitmap from 1.6.10 to 1.6.12 (#15486)

## 提交信息

- **序号**：3326 / 4088
- **哈希**：dab3f9d8f6949676372d3b41fb2c9c86f5d79977
- **短哈希**：dab3f9d8f
- **日期**：2026-02-28 22:08:15 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 1.6.10 to 1.6.12 (#15486)
- **PR/Issue**：#15486

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，目标是将 `org.roaringbitmap:RoaringBitmap` 从 `1.6.10` 升级到 `1.6.12`。

RoaringBitmap 是一种高性能的压缩位图（compressed bitmap）库，在 Iceberg 中被用于高效表示与运算大规模整数集合。典型用途包括 manifest 中的分区/文件 ID 集合表达、删除文件中删除位置的位图表示（positional delete 的 DV——deletion vector 实现），以及元数据索引等需要紧凑且可快速集合运算的场景。其 `RoaringBitmap` 数据结构相比传统 `BitSet` 在稀疏场景下能显著节省内存，同时保留 O(log n) 级别的随机访问与并/交/差运算能力。

本次升级属于语义化版本的 **patch（补丁）** 升级（`1.6.10` → `1.6.12`，`update-type: version-update:semver-patch`），即仅跨越两个补丁版本。按照 SemVer 约定，patch 升级只包含 bug 修复与小的内部改进，不引入 API 破坏性变更，因此属于低风险升级。Dependabot 将其归类为 `direct:production`（直接生产依赖），意味着该库直接出现在 Iceberg 的运行时 classpath 中，而非仅用于构建或测试。预期影响是获得上游两个补丁版本带来的缺陷修复（如序列化、内存占用或并发场景的边界修复），对 Iceberg 的功能行为无可见变化。

## 如何达成设计目的

改动极简，仅修改 Iceberg 统一的 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `roaringbitmap` 这一项的版本字符串，由 `1.6.10` 改为 `1.6.12`。所有引用该版本变量的模块（通过 `libs.roaringbitmap` 等坐标引用）会在下一次构建时自动拉取新版本，无需逐模块改动。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 RoaringBitmap 版本从 1.6.10 提升到 1.6.12。

**工作逻辑**：
在版本目录的 `[versions]` 段中，将 `roaringbitmap = "1.6.10"` 修改为 `roaringbitmap = "1.6.12"`。该变量被 `roaringbitmap` 库坐标引用，下游模块（如 core、spark 等）通过 `libs.roaringbitmap` 引用此坐标时，会解析到新的 `1.6.12` 版本。这是一次纯版本号变更，不涉及任何代码或配置逻辑调整。

## 总结

本次提交通过 Dependabot 将生产依赖 RoaringBitmap 从 1.6.10 升级到 1.6.12（patch 级），以获取上游的缺陷修复与内部改进。改动局限于版本目录文件单行，风险低，对 Iceberg 的外部行为无影响，属于日常依赖维护的一部分。
