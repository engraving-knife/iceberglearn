# 提交 2391：Build: Bump jackson-bom from 2.19.1 to 2.19.2 (#13600)

## 提交信息

- **序号**：2391 / 4088
- **哈希**：6e361a657b1b295be0aab93c368c196d066c2f7d
- **短哈希**：6e361a657
- **日期**：2025-07-23 18:11:30 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jackson-bom from 2.19.1 to 2.19.2 (#13600)
- **PR/Issue**：#13600

## 总体目的

此提交由 dependabot 自动生成，将 Jackson BOM（Bill of Materials）从 2.19.1 版本升级到 2.19.2 版本。Jackson 是 Java 生态中最广泛使用的 JSON 处理库，Iceberg 项目依赖它进行 JSON 序列化和反序列化操作（例如表元数据、配置等的 JSON 处理）。

这是一个 patch 级别的版本升级（semver-patch），通常包含 bug 修复、安全补丁和小幅性能改进，不引入破坏性变更。BOM 升级会同时同步 jackson-core、jackson-databind、jackson-annotations 等所有 Jackson 核心组件的版本，确保它们之间的版本兼容性。

## 如何达成设计目的

通过修改 Gradle 版本目录（Version Catalog）文件 `gradle/libs.versions.toml` 中 `jackson-bom` 的版本声明从 `"2.19.1"` 改为 `"2.19.2"`。版本目录机制会自动将新版本传播到所有引用 jackson-bom 的依赖项，无需逐个修改各模块的 build 文件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Jackson BOM 版本。

**工作逻辑**：将 `jackson-bom = "2.19.1"` 修改为 `jackson-bom = "2.19.2"`。该文件中还有其他固定版本的 Jackson 配置（如 jackson211、jackson212、jackson213 等），这些是因 Spark/Hive 等组件的兼容性要求而锁定的特定版本，不受此次 BOM 升级影响。

## 总结

这是一个常规的依赖维护提交，通过 dependabot 自动升级 Jackson BOM 到最新 patch 版本，保持依赖的最新状态以获取 bug 修复和安全补丁。修改仅涉及一行版本号变更，风险极低。
