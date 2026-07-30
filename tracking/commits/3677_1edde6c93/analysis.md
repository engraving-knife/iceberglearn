# 提交 3677：Build: Bump jackson-bom from 2.21.2 to 2.21.3 (#16269)

## 提交信息

- **序号**：3677 / 4088
- **哈希**：1edde6c93652d71f997b970ac925ba0723db40fc
- **短哈希**：1edde6c93
- **日期**：2026-05-09 23:56:16 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jackson-bom from 2.21.2 to 2.21.3 (#16269)
- **PR/Issue**：#16269

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，目的是将 Jackson BOM（Bill of Materials）从 2.21.2 版本升级到 2.21.3 版本。Jackson 是 Java 生态中最流行的 JSON 处理库之一，Iceberg 项目使用它进行 JSON 序列化和反序列化，包括元数据文件（metadata.json）、manifest 文件以及 REST Catalog 通信等场景。

BOM 是一种 POM 文件，用于统一管理多个相关模块的版本号，通过升级 jackson-bom，可以同步升级 jackson-core、jackson-databind、jackson-annotations 等所有 Jackson 组件的版本，确保它们之间的兼容性。这是一个补丁版本（patch version）升级，通常包含 bug 修复、性能改进和小幅度的功能增强，但不会引入破坏性变更。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 文件中的 `jackson-bom` 版本号定义，将依赖版本从 2.21.2 升级到 2.21.3。Gradle 会自动应用这个版本变更到所有使用 jackson-bom 的模块中，包括 jackson-core、jackson-databind 等组件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 jackson-bom 版本号。

**工作逻辑**：

```toml
-jackson-bom = "2.21.2"
+jackson-bom = "2.21.3"
```

仅修改 `jackson-bom` 的版本字符串定义。该变量在 Gradle 构建配置中被引用，通过修改这一处定义即可统一升级所有依赖 jackson-bom 管理的 Jackson 模块。版本号变更属于语义化版本中的补丁级别（2.21.2 → 2.21.3），主要包含 bug 修复。

## 总结

这是一个常规的依赖维护提交，通过 Dependabot 自动化机制将 Jackson JSON 库升级到最新的补丁版本，以获取最新的 bug 修复和改进。这种持续的小版本升级对于保持项目依赖的健康状态、避免已知 bug 影响项目稳定性具有重要意义。
