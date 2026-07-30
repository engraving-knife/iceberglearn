# 提交 3297：Build: Bump software.amazon.awssdk:bom from 2.41.29 to 2.41.34 (#15402)

## 提交信息

- **序号**：3297 / 4088
- **哈希**：8e162e6a18290bc21476cf47c1c9322f87670642
- **短哈希**：8e162e6a1
- **日期**：2026-02-22
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.41.29 to 2.41.34
- **PR/Issue**：#15402

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.41.29 升级到 2.41.34。AWS SDK BOM 是一个集中管理 AWS SDK 各模块版本号的 POM 制品，通过引入 BOM 可以让所有 AWS SDK 模块（如 S3、DynamoDB、Glue、STS、KMS 等）的版本保持一致且相互兼容。

Iceberg 深度集成 AWS 生态：`aws` 模块提供 S3 文件存储访问（`S3FileIO`）、Glue 目录集成（`GlueCatalog`）、DynamoDB 目录（`DynamoDbCatalog`）等核心能力。此外，`awssdk-s3accessgrants` 等扩展依赖也受该 BOM 管控。这意味着 AWS SDK 的版本升级直接影响 Iceberg 与 AWS 存储和目录服务的交互链路，包括文件读写、清单存储、表元数据操作等关键路径。

本次升级跨 5 个补丁版本（2.41.29 → 2.41.34），`update-type` 为 `version-update:semver-patch`，属于补丁级别升级。补丁版本通常包含 bug 修复、安全漏洞修补和服务端 API 适配，不引入新的 API 或行为变更，因此预期对 Iceberg 现有 AWS 集成代码无破坏性影响。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本变量，从 `2.41.29` 提升到 `2.41.34`。项目中所有 AWS SDK 模块依赖均通过该 BOM 进行版本对齐，因此仅修改 BOM 版本变量即可让全部 AWS SDK 组件同步升级到一致版本，无需逐个调整模块依赖声明。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本变量。

**工作逻辑**：
将第 36 行的 `awssdk-bom = "2.41.29"` 改为 `awssdk-bom = "2.41.34"`。该变量在 `[libraries]` 段中被 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，并以 platform/BOM 形式导入到各模块的依赖配置中，从而统一管理 S3、Glue、DynamoDB、STS、KMS 等 AWS 服务客户端的版本。这种 BOM 机制确保了 AWS SDK 各模块间的二进制兼容性，避免因单独升级某个模块而引入版本冲突。

## 总结

本次提交通过版本目录升级 AWS SDK BOM 至 2.41.34，同步更新所有 AWS 服务客户端版本，获取上游 bug 修复与安全补丁。作为 semver-patch 级别升级，对 Iceberg 的 S3 文件 IO、Glue/DynamoDB 目录集成等 AWS 相关功能无破坏性影响，属于常规的依赖健康维护。
