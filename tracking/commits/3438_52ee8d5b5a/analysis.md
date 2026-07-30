# 提交 3438：Build: Bump jackson-bom from 2.21.1 to 2.21.2 (#15722)

## 提交信息

- **序号**：3438 / 4088
- **哈希**：52ee8d5b5ab06d04796f747851a0166e5ea96999
- **短哈希**：52ee8d5b5a
- **日期**：2026-03-21 23:42:42 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jackson-bom from 2.21.1 to 2.21.2 (#15722)
- **PR/Issue**：#15722

## 总体目的

这是一个由 Dependabot 自动生成的依赖版本升级提交。将 Jackson BOM 从 2.21.1 升级到 2.21.2。Jackson 是 Java 生态中最流行的 JSON 处理库，BOM 用于统一管理 Jackson 各模块的版本。

此次升级影响以下 Jackson 组件：
- `com.fasterxml.jackson:jackson-bom`
- `com.fasterxml.jackson.core:jackson-core`
- `com.fasterxml.jackson.core:jackson-databind`

## 如何达成设计目的

- Dependabot 自动检测到 jackson-bom 有新版本发布
- 在 `gradle/libs.versions.toml` 版本目录文件中更新 BOM 版本号
- BOM 更新后，jackson-core 和 jackson-databind 等模块版本会自动同步

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Jackson BOM 版本号。

**工作逻辑**：
- 将 jackson-bom 的版本号从 `2.21.1` 修改为 `2.21.2`
- 该文件是 Gradle 版本目录，集中管理项目所有依赖版本
- BOM 更新后所有 Jackson 模块版本统一对齐

## 总结

这是 Dependabot 自动生成的常规依赖升级提交，将 Jackson BOM 及其关联模块（jackson-core、jackson-databind）从 2.21.1 升级到 2.21.2，属于补丁版本升级，风险较低。
