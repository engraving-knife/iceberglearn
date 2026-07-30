# 提交 1177：Build: Bump org.roaringbitmap:RoaringBitmap from 1.2.1 to 1.3.0 (#11187)

## 提交信息

- **序号**：1177 / 4088
- **哈希**：5a2c1c9c8dd8d61e08bbad713c24e6b726eee323
- **短哈希**：5a2c1c9c8
- **日期**：2024-09-23（Mon Sep 23 14:44:07 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 1.2.1 to 1.3.0 (#11187)
- **PR/Issue**：#11187

## 总体目的

本提交由 Dependabot 自动生成，将 RoaringBitmap（`org.roaringbitmap:RoaringBitmap`）从 1.2.1 升级到 1.3.0，属于 `version-update:semver-minor` 次版本升级。

RoaringBitmap 是一种高性能压缩位图数据结构，Iceberg 在 `iceberg-core` 中用它实现位置删除（position delete）的删除文件索引——即把被删除行的行号压缩存储为 RoaringBitmap，读取时通过位图判断某行是否被删除，避免逐行扫描。升级获取 1.3.0 的性能优化、bug 修复与新特性。

## 如何达成设计目的

仅修改 `gradle/libs.versions.toml`，把 `roaringbitmap` 版本号从 `1.2.1` 改为 `1.3.0`。`iceberg-core` 等模块通过 `libs.roaringbitmap` 引用该版本。无代码改动，说明 1.2.1 → 1.3.0 的 API 变化未影响 Iceberg 已有调用。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 roaringbitmap 版本号。

**工作逻辑**：在 `[versions]` 段把：

```toml
roaringbitmap = "1.2.1"
```

改为：

```toml
roaringbitmap = "1.3.0"
```

该变量在 `[libraries]` 段被 `roaringbitmap = { module = "org.roaringbitmap:RoaringBitmap", version.ref = "roaringbitmap" }` 引用（本提交未改这一行）。`iceberg-core/build.gradle` 中通过 `implementation libs.roaringbitmap` 引入该依赖，运行时被位置删除相关代码使用。

**升级背景**：RoaringBitmap 1.3.0 是次版本升级，包含性能优化与 API 扩展，向后兼容 1.2.1 的 API。位置删除是 Iceberg 行级操作的核心数据结构之一，性能改进对删除密集型负载有正向收益。

## 小结

- **成效**：RoaringBitmap 升级到 1.3.0，`iceberg-core` 的位置删除实现获取最新性能优化与 bug 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更，无代码改动。运行时影响涉及所有使用位置删除（position deletes）的读取路径，因为是 `iceberg-core` 的核心依赖。
- **回迁到 1.4.x 的注意事项**：RoaringBitmap 1.2.1 → 1.3.0 是次版本升级，API 兼容。1.4.x 回迁风险较低，但需注意两点：(1) RoaringBitmap 序列化格式在不同主版本间保持兼容（同主版本内兼容），1.2.x 与 1.3.x 同属 1.x 主版本，序列化兼容，已写入的删除文件位图可被新版本正确读取；(2) 建议回迁后跑位置删除相关测试（`TestPositionDeletes` 等）。若 1.4.x 无位置删除相关 bug，**可不回迁**；如有性能或稳定性需求可安全回迁。
