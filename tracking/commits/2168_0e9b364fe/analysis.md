# 提交 2168：Build: Bump software.amazon.awssdk:bom from 2.31.45 to 2.31.50 (#13148)

## 提交信息

- **序号**：2168 / 4088
- **哈希**：0e9b364fe23175c4b454fdc788cf2ae662616a68
- **短哈希**：0e9b364fe
- **日期**：2025-05-27 14:26:35 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.31.45 to 2.31.50 (#13148)
- **PR/Issue**：#13148

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM（Bill of Materials）版本从 2.31.45 升级到 2.31.50。AWS SDK BOM 用于统一管理所有 AWS SDK 模块的版本，确保各模块版本兼容。此次升级为 semver-patch 版本升级（2.31.45 -> 2.31.50），包含多个补丁版本的累积更新，通常包含 bug 修复、性能改进和安全补丁。Iceberg 的 AWS 集成模块（`aws/`）依赖 AWS SDK 进行 S3 等服务的交互，保持 SDK 版本更新对安全性和稳定性至关重要。

## 如何达成设计目的

- 在 `gradle/libs.versions.toml` 版本目录中，将 `awssdk-bom` 的版本号从 `2.31.45` 修改为 `2.31.50`。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：在版本目录文件中，将 `awssdk-bom = "2.31.45"` 修改为 `awssdk-bom = "2.31.50"`。所有引用该 BOM 的 AWS SDK 模块版本将随之统一升级。

## 总结

这是一个 Dependabot 自动生成的依赖升级提交，将 AWS SDK BOM 从 2.31.45 升级到 2.31.50，属于常规的依赖安全维护工作，确保 Iceberg 的 AWS 集成使用最新的 SDK 补丁版本。
