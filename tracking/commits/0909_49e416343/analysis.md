# 提交 0909：Build: Bump org.roaringbitmap:RoaringBitmap from 1.1.0 to 1.2.0 (#10655)

## 提交信息

- **序号**：0909 / 4088
- **哈希**：49e416343a1647587072b4f0c4c2bae995d23835
- **短哈希**：49e416343
- **日期**：2024-07-08
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.roaringbitmap:RoaringBitmap from 1.1.0 to 1.2.0 (#10655)
- **PR/Issue**：#10655

## 总体目的

Iceberg 使用 RoaringBitmap（`org.roaringbitmap:RoaringBitmap`）作为高性能压缩位图库，主要用于位置删除（position deletes）的行号追踪和扫描时的删除标记匹配。dependabot 定期检查该库的新版本并提交 PR 升级。本次将 RoaringBitmap 从 `1.1.0` 升级到 `1.2.0`，这是一个 minor 版本升级（semver-minor），引入新功能和改进，同时保持向后兼容。RoaringBitmap 以直接依赖（`direct:production`）形式引入，升级有助于获得性能优化和新特性。

## 如何达成设计目的

采用 Gradle version catalog 统一管理依赖版本。Iceberg 在 `gradle/libs.versions.toml` 中定义了 `roaringbitmap` 版本常量。升级时只需修改 toml 文件中 `roaringbitmap` 这一行版本号，所有引用该常量的模块自动同步到新版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 RoaringBitmap 版本从 1.1.0 升级到 1.2.0。

**工作逻辑**：

```diff
-roaringbitmap = "1.1.0"
+roaringbitmap = "1.2.0"
```

该行位于 `[versions]` 段。Gradle 构建脚本通过 catalog alias 引用 `org.roaringbitmap:RoaringBitmap`，其版本绑定到 `roaringbitmap` 常量。RoaringBitmap 1.1.0 → 1.2.0 是 minor 版本升级，按语义化版本约定保持向后兼容，Iceberg 使用 RoaringBitmap 的 API（如 `RoaringBitmap`、`FastAggregation` 等）无需修改。RoaringBitmap 主要用于 Iceberg core 模块的删除向量（delete vector）和位置删除匹配逻辑，升级后相关功能行为不变。

## 小结

- **成效**：将 RoaringBitmap 从 1.1.0 升级到 1.2.0，引入 minor 版本的新功能和性能改进。
- **影响范围**：1 个文件 `gradle/libs.versions.toml`，1 行改动，无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：可以回迁。RoaringBitmap minor 版本升级向后兼容，风险低。1.4.x 分支若使用 1.1.0 可安全升级到 1.2.0。建议升级后回归测试 position delete 和 row-level filter 相关场景，确保位图操作行为一致。RoaringBitmap 是 Iceberg core 的关键依赖，涉及删除匹配正确性，虽然 API 兼容但仍建议通过集成测试验证。
