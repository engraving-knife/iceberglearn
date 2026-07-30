# 提交 2239：Build: Bump jackson-bom from 2.19.0 to 2.19.1

## 提交信息

- **序号**：2239 / 4088
- **哈希**：077af5fec20b662113beb42c1744e1ca79943204
- **短哈希**：077af5fec
- **日期**：2025-06-15 09:14:38 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jackson-bom from 2.19.0 to 2.19.1
- **PR/Issue**：#13315

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Jackson BOM 从 2.19.0 升级到 2.19.1。Jackson 是 Java 生态中最广泛使用的 JSON 处理库，Iceberg 使用 Jackson 进行 REST API 的 JSON 序列化和反序列化。Jackson BOM 统一管理 Jackson 核心模块及扩展模块的版本。此次升级为 patch 级别更新，包含 bug 修复。

## 如何达成设计目的

- 在 Gradle 版本目录文件中修改 Jackson BOM 的版本号。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 Jackson BOM 版本。

**工作逻辑**：将 `jackson-bom = "2.19.0"` 修改为 `jackson-bom = "2.19.1"`，所有引用该 BOM 的 Jackson 模块将自动使用新版本。

## 总结

常规依赖升级提交，将 Jackson BOM 从 2.19.0 升级到 2.19.1，获取最新的 bug 修复。
