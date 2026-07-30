# 提交 3561：Build: Bump org.roaringbitmap:RoaringBitmap from 1.6.13 to 1.6.14 (#16042)

## 提交信息

- **序号**：3561 / 4088
- **哈希**：bfccee96be68fa0182b04e6f93251b5cd5a9ea69
- **短哈希**：bfccee96b
- **日期**：2026-04-19 07:17:28 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 1.6.13 to 1.6.14 (#16042)
- **PR/Issue**：#16042

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 RoaringBitmap 库 `org.roaringbitmap:RoaringBitmap` 从版本 1.6.13 升级到 1.6.14。RoaringBitmap 是一种高性能的压缩位图数据结构，Iceberg 在删除向量（Deletion Vector, DV）等场景中使用它来高效表示被删除行的位置集合。这是一个 semver-patch（补丁版本）升级，通常包含 bug 修复和小幅改进。

## 如何达成设计目的

Dependabot 自动检测到版本目录中 RoaringBitmap 版本有新补丁版本可用，自动创建 PR 升级版本声明。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 RoaringBitmap 版本声明。

**工作逻辑**：
将 RoaringBitmap 版本从 `1.6.13` 升级到 `1.6.14`。该库被 Iceberg 用于删除向量等场景下的位图操作，升级后可获取 1.6.14 版本中的 bug 修复和性能改进。

## 总结

这是一个常规的依赖维护提交，通过补丁版本升级保持 RoaringBitmap 依赖的最新状态，获取最新的 bug 修复，对于依赖位图操作的删除向量功能尤为重要。
