# 提交 2058：Build: Bump jackson-bom from 2.18.3 to 2.19.0 (#12903)

## 提交信息

- **序号**：2058 / 4088
- **哈希**：3aac0643c69ecb2120cdc5870a0fac20657bbc36
- **短哈希**：3aac0643c
- **日期**：2025-04-30 10:56:29 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jackson-bom from 2.18.3 to 2.19.0 (#12903)
- **PR/Issue**：#12903

## 总体目的

由 Dependabot 自动发起的依赖升级，将 Jackson BOM（`com.fasterxml.jackson:jackson-bom`）从 `2.18.3` 升级到 `2.19.0`。Jackson 是 Iceberg 广泛使用的 JSON 序列化库（用于 REST catalog、配置序列化等），BOM 用于统一管理 jackson-core/jackson-databind/jackson-annotations 等子模块的版本。此次为 semver-minor 升级，可获取 Jackson 2.19 系列的改进与修复。

注意：仓库中还存在针对 Spark/Flink 特定版本钉死的 jackson211/212/213 等别名（使用 `strictly` 严格版本约束），这些是为兼容旧版 Spark/Flink 而保留的，本次升级不影响它们。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `jackson-bom` 别名的版本字符串实现。所有引用 `jackson-bom` 的模块会自动获取 2.19.0。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 Jackson BOM 版本。

**工作逻辑**：
将 `jackson-bom = "2.18.3"` 改为 `jackson-bom = "2.19.0"`。该别名作为平台依赖引入 jackson-core、jackson-databind、jackson-annotations 等模块，统一升级到 2.19.0。其余 `jackson211`/`jackson212`/`jackson213` 等严格钉死版本保持不变。

## 总结

Dependabot 自动升级 Jackson BOM 版本（2.18.3 → 2.19.0），单行配置变更，引入 Jackson 2.19 系列的改进与修复，不影响为 Spark/Flink 旧版本保留的钉死 Jackson 版本。
