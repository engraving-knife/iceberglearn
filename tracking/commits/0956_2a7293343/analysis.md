# 提交 0956：Build: Bump org.roaringbitmap:RoaringBitmap from 1.2.0 to 1.2.1 (#10733)

## 提交信息

- **序号**：0956 / 4088
- **哈希**：2a7293343798414d275b0c43cdd9635461fbb325
- **短哈希**：2a7293343
- **日期**：2024-07-22（Mon Jul 22 09:18:17 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 1.2.0 to 1.2.1 (#10733)
- **PR/Issue**：#10733

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。RoaringBitmap 是一种高效的压缩位图数据结构，Iceberg 在删除文件（特别是位置删除文件 position delete files）的索引、manifest 的行级统计、以及位运算相关的核心逻辑中使用它来高效表示和操作大整数集合（如被删除的行号集合）。

本次提交将 `org.roaringbitmap:RoaringBitmap` 从 1.2.0 升级到 1.2.1（semver patch 版本升级），属于 patch 级别的小版本升级，通常仅包含 bug 修复和性能改进，不引入破坏性 API 变更。由于 RoaringBitmap 直接参与 Iceberg 的删除与扫描核心路径，保持其最新修复版本有助于避免位图相关的已知 bug。

## 如何达成设计目的

实现方式为修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将其中 `roaringbitmap` 的版本声明从 `1.2.0` 改为 `1.2.1`。所有引用该版本号的模块在构建时自动同步升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 RoaringBitmap 依赖版本从 1.2.0 升级到 1.2.1。

**工作逻辑**：仅修改版本目录中的一行：

```diff
-roaringbitmap = "1.2.0"
+roaringbitmap = "1.2.1"
```

修改后，所有通过 `${libs.roaringbitmap}` 引用该版本号的模块（如核心 `iceberg-core`、删除相关逻辑模块）在构建时拉取 1.2.1 版本的 RoaringBitmap 库。

## 小结

- **成效**：完成 RoaringBitmap 从 1.2.0 到 1.2.1 的 patch 版本升级，使位图相关核心依赖保持最新修复版本。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 一个文件，1 行改动。影响所有使用 RoaringBitmap 的模块的构建产物依赖版本，但不改变 Iceberg 自身代码逻辑。
- **回迁到 1.4.x 的注意事项**：纯依赖升级，**适合回迁**，风险较低。RoaringBitmap 是 Iceberg 删除/扫描核心路径使用的库，patch 升级通常向后兼容。回迁到 1.4.x 建议运行完整的删除文件与扫描相关测试套件以验证兼容性，特别是位置删除与相等删除的集成测试。
