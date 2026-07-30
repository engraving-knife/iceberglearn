# 提交 2554：Build: Bump software.amazon.awssdk:bom from 2.32.24 to 2.32.29 (#13911)

## 提交信息

- **序号**：2554 / 4088
- **哈希**：adc02fd0684a6d69b9098a1ed98af2edb86534c1
- **短哈希**：adc02fd06
- **日期**：2025-08-24 09:19:58 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.32.24 to 2.32.29 (#13911)
- **PR/Issue**：#13911

## 总体目的

该提交由 Dependabot 自动生成，将 AWS SDK for Java 的 BOM（Bill of Materials）版本从 2.32.24 升级到 2.32.29。AWS SDK BOM 用于统一管理所有 AWS SDK 组件的版本，Iceberg 的 AWS 模块（`iceberg-aws`）依赖该 BOM 来引入 S3Client、DynamoDBClient、KMSClient 等 AWS 服务客户端。

此次升级为补丁版本升级（2.32.24 -> 2.32.29），属于向后兼容的维护性更新，通常包含 bug 修复、性能改进和安全补丁。通过 BOM 统一管理版本，确保所有 AWS SDK 组件版本一致，避免版本冲突。

## 如何达成设计目的

- 在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中将 `awssdk-bom` 版本变量从 `2.32.24` 修改为 `2.32.29`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1)

**修改目的**：升级 AWS SDK BOM 版本号。

**工作逻辑**：将 `awssdk-bom = "2.32.24"` 修改为 `awssdk-bom = "2.32.29"`，所有引用该 BOM 的 AWS SDK 组件将自动使用新版本。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 AWS SDK BOM 从 2.32.24 升级到 2.32.29（补丁版本升级），修改仅一行版本号配置。
