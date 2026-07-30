# 提交 4069：Build: Bump org.roaringbitmap:RoaringBitmap from 1.6.14 to 1.6.15 (#17290)

## 提交信息

- **序号**：4069 / 4088
- **哈希**：7786a264f5fd88dd37f045955c190dcc20648651
- **短哈希**：7786a264f
- **日期**：2026-07-18 22:27:29 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 1.6.14 to 1.6.15 (#17290)
- **PR/Issue**：#17290

## 总体目的

Dependabot 自动升级提交，将 `org.roaringbitmap:RoaringBitmap` 从 1.6.14 升级到 1.6.15（semver patch 补丁版本升级）。RoaringBitmap 是一种高性能的压缩位图数据结构，Iceberg 在删除向量（deletion vectors）等特性中使用它来高效表示已删除行的位置集合。patch 版本升级包含 bug 修复和性能改进，属于低风险维护升级。

## 如何达成设计目的

通过 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `roaringbitmap` 版本变量管理。Dependabot 将该变量从 `1.6.14` 改为 `1.6.15`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 RoaringBitmap 版本。

**工作逻辑**：
```toml
roaringbitmap = "1.6.15"
```
将 `roaringbitmap` 变量从 `1.6.14` 改为 `1.6.15`。

## 总结

常规的 RoaringBitmap 压缩位图库补丁版本升级，保持删除向量等特性的位图操作基于最新补丁版本，获取上游 bug 修复与性能改进。patch 级别升级风险很低。
