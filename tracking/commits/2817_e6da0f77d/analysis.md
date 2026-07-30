# 提交 2817：Build: Bump jackson-bom from 2.20.0 to 2.20.1 (#14471)

## 提交信息

- **序号**：2817 / 4088
- **哈希**：e6da0f77d81f20551cd61dc0d6d832499670b147
- **短哈希**：e6da0f77d
- **日期**：2025-11-01 23:10:30 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jackson-bom from 2.20.0 to 2.20.1 (#14471)
- **PR/Issue**：#14471

## 总体目的

这是 Dependabot 自动生成的依赖版本升级提交。Jackson 是 Java 生态中最广泛使用的 JSON 处理库，Iceberg 通过 `jackson-bom`（Bill of Materials）统一管理 Jackson 各模块（`jackson-core`、`jackson-databind`、`jackson-annotations` 等）的版本，确保各模块间版本兼容。

本次将 `jackson-bom` 从 2.20.0 升至 2.20.1，属于补丁版本（patch）升级，包含错误修复。同时连带升级 `jackson-core` 和 `jackson-databind` 到对应版本。Jackson 在 Iceberg 中用于序列化/反序列化元数据、配置和 REST 通信等场景，是基础性依赖。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 中的 `jackson-bom` 版本属性，从 `2.20.0` 更新为 `2.20.1`。BOM 会自动将所有引用的 Jackson 模块对齐到该版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Jackson BOM 版本，统一管理 Jackson 各模块。

**工作逻辑**：将 `jackson-bom = "2.20.0"` 改为 `jackson-bom = "2.20.1"`。通过 BOM 机制，所有 Jackson 模块（jackson-core、jackson-databind 等）会自动使用 2.20.1 版本，保证模块间兼容性。注意文件中还有针对不同 Spark/Flink 版本的严格锁定 Jackson 版本（如 jackson211、jackson212 等），这些不受 BOM 影响。

## 总结

将 Jackson BOM 从 2.20.0 升级到 2.20.1，属于低风险的补丁版本升级，连带升级 jackson-core 和 jackson-databind。Jackson 是 JSON 处理的核心依赖，升级用于获取错误修复。这是 Dependabot 批量依赖升级（2811-2819）的一部分。
