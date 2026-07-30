# 提交 1226：Build: Bump jackson-bom from 2.14.2 to 2.18.0 (#11226)

## 提交信息

- **序号**：1226 / 4088
- **哈希**：337d05b8d596ccd70f0100d5112b457dbe903591
- **短哈希**：337d05b8d
- **日期**：2024-10-12（Sat Oct 12 21:09:36 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump jackson-bom from 2.14.2 to 2.18.0 (#11226)
- **PR/Issue**：#11226

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级提交。Jackson 是 Java 生态中最主流的 JSON 序列化/反序列化库，`jackson-bom`（Bill of Materials）统一管理 Jackson 全家桶（`jackson-core`、`jackson-databind`、`jackson-annotations` 等）的版本。Iceberg 在多处使用 Jackson 进行 JSON 序列化，包括 REST Catalog 的 HTTP 请求/响应序列化、元数据 JSON 文件读写、Nessie catalog 交互等。

本次将 `jackson-bom` 从 2.14.2 升级到 2.18.0，属于次版本（semver-minor）升级，跨越 4 个次版本（2.15、2.16、2.17、2.18），是本批次依赖升级中版本跨度最大的一个。此次升级旨在获取 Jackson 2.15-2.18 引入的新特性、性能改进和 bug 修复。

值得注意的是：Iceberg 还维护了 `jackson211`、`jackson212`、`jackson213`、`jackson214`、`jackson215` 等独立锁定的 Jackson 版本，这些是为 Spark 集成模块专门定制的（Spark 各版本捆绑了不同的 Jackson 版本，需通过 `strictly` 约束避免冲突）。本次升级仅影响 `jackson-bom`（Iceberg 自身使用的版本），不影响 Spark 集成模块的 Jackson 版本。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中 `jackson-bom` 的版本声明，从 `"2.14.2"` 改为 `"2.18.0"`。通过 BOM 机制和 `version.ref` 引用，`jackson-core`、`jackson-databind`、`jackson-annotations` 三个核心模块会自动同步到 2.18.0。Spark 专用的 Jackson 版本（`jackson211` ~ `jackson215`）使用独立的 `strictly` 版本约束，不受影响。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 jackson-bom 版本号。

**工作逻辑**：将第 55 行的版本声明从：

```toml
jackson-bom = "2.14.2"
```

改为：

```toml
jackson-bom = "2.18.0"
```

该版本变量被以下库条目通过 `version.ref` 引用：
- `com.fasterxml.jackson:jackson-bom`（BOM 本身）
- `com.fasterxml.jackson.core:jackson-core`（核心流式 API）
- `com.fasterxml.jackson.core:jackson-databind`（数据绑定层）
- `com.fasterxml.jackson.core:jackson-annotations`（注解层）

这些模块是 Iceberg 自身 JSON 处理的核心依赖，用于：
1. REST Catalog 模块中 REST API 请求/响应的 JSON 序列化
2. 元数据文件（metadata.json、snapshot files 等）的读写
3. 配置对象的 JSON 序列化

Jackson 2.14 → 2.18 跨越了多个次版本，主要变更包括：
- **2.15**：引入 StreamReadConstraints 和 StreamWriteConstraints，限制 JSON 解析的深度、长度等，防范 DoS 攻击
- **2.16**：改进 Java 时间类型支持、枚举处理增强
- **2.17**：性能优化、bug 修复
- **2.18**：新增特性、API 改进

由于是次版本升级，可能存在行为变化（如默认序列化行为调整、废弃 API 移除等），需要关注兼容性。

## 小结

- **成效**：jackson-bom 从 2.14.2 升级到 2.18.0，获取 4 个次版本的新特性、性能改进和 bug 修复。影响 `jackson-core`、`jackson-databind`、`jackson-annotations` 三个核心模块。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动，无代码逻辑变更。但由于 Jackson 是核心序列化库且版本跨度大，实际影响面较广。Spark 专用 Jackson 版本（jackson211~jackson215）不受影响。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支当前使用的 jackson-bom 版本为 2.14.2（与升级前一致）。此升级是次版本升级（2.14 → 2.18），版本跨度大，存在以下风险：1) Jackson 2.15 引入的 StreamReadConstraints 可能影响超大 JSON 文件的解析（如大型元数据文件），需验证默认限制是否足够；2) 序列化/反序列化行为可能发生变化，影响 REST Catalog 和元数据文件的兼容性；3) 需确认 2.18.0 与 Iceberg 1.4.x 代码中使用的 Jackson API 完全兼容。**建议谨慎回迁**，回迁前必须运行完整的 JSON 序列化相关测试，特别是 REST Catalog 和元数据读写测试。若 1.4.x 作为稳定维护分支，可考虑仅升级到 2.14.x 最新补丁版本而非直接跳到 2.18.0。
