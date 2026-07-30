# 提交 1834：Build: Bump software.amazon.awssdk:bom from 2.30.26 to 2.30.31 (#12439)

## 提交信息

- **序号**：1834 / 4088
- **哈希**：cbc3ebfc6da216c106e8264cd389f3c4a02309cc
- **短哈希**：cbc3ebfc6
- **日期**：2025-03-09 07:44:22 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.30.26 to 2.30.31 (#12439)
- **PR/Issue**：#12439

## 总体目的

本提交由 Dependabot 自动生成，将 AWS SDK for Java 的 BOM（Bill of Materials）版本从 2.30.26 升级到 2.30.31。AWS SDK BOM 用于统一管理所有 AWS SDK 模块的版本，确保各模块之间版本兼容。这是一次补丁级别的版本升级（2.30.26 → 2.30.31），属于常规的依赖维护，通常包含 bug 修复和小改进，不引入破坏性变更。

Iceberg 的 AWS 集成模块（`iceberg-aws`、`iceberg-aws-bundle` 等）依赖 AWS SDK 进行 S3、DynamoDB、Glue 等服务的交互，保持 SDK 版本最新有助于获取最新的 bug 修复和安全补丁。

## 如何达成设计目的

Dependabot 自动识别 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本声明，将其从 `2.30.26` 更新为 `2.30.31`。同时，由于 `aws-bundle` 和 `kafka-connect-runtime` 模块打包了 AWS SDK 的依赖（通过 fat jar / uber jar），这些模块的 LICENSE 和 NOTICE 文件中列出的 AWS SDK 组件版本也需要同步更新，从原来的 2.30.21（此前一次升级的残留）更新为 2.30.31。

## 修改详情

### `gradle/libs.versions.toml` (修改, 1 line)

**修改目的**：升级 AWS SDK BOM 版本声明。

**工作逻辑**：将 `awssdk-bom = "2.30.26"` 改为 `awssdk-bom = "2.30.31"`。这个版本号通过 Gradle 的 version catalog 机制被所有 AWS SDK 依赖引用，修改后所有 AWS SDK 模块版本统一升级到 2.30.31。

### `aws-bundle/LICENSE` (修改, 72 lines)

**修改目的**：同步更新 aws-bundle fat jar 中打包的 AWS SDK 组件许可证版本信息。

**工作逻辑**：LICENSE 文件中列出了 aws-bundle 打包的所有第三方依赖及其版本和许可证。本次将所有 `software.amazon.awssdk` 组的组件版本从 `2.30.21` 批量更新为 `2.30.31`（36 个条目，每个条目修改 2 行共 72 行变更）。这些组件包括 annotations、apache-client、arns、auth、aws-core、aws-json-protocol、aws-query-protocol、aws-xml-protocol、checksums、client-sessions、core、auth-crt 等所有 SDK 模块。

### `aws-bundle/NOTICE` (修改, 76 lines)

**修改目的**：同步更新 aws-bundle 的 NOTICE 文件。

**工作逻辑**：NOTICE 文件中列出 aws-bundle 依赖的 AWS SDK 版本信息，同样从 2.30.21 更新为 2.30.31。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE` 和 `NOTICE` (修改, 152 lines)

**修改目的**：同步更新 kafka-connect-runtime hive 配置中的许可证信息。

**工作逻辑**：与 aws-bundle 类似，将 LICENSE/NOTICE 中的 AWS SDK 组件版本从 2.30.21 更新为 2.30.31。

### `kafka-connect/kafka-connect-runtime/main/LICENSE` 和 `NOTICE` (修改, 150 lines)

**修改目的**：同步更新 kafka-connect-runtime main 配置中的许可证信息。

**工作逻辑**：同上，将 AWS SDK 组件版本从 2.30.21 更新为 2.30.31。

## 小结

本提交是 Dependabot 自动生成的依赖升级，将 AWS SDK BOM 从 2.30.26 升级到 2.30.31，并同步更新了 4 个模块的 LICENSE/NOTICE 文件。改动共 7 个文件、226 行（均为版本号文本替换），不涉及任何代码逻辑。回迁到 1.4.x 时可直接应用，需确保 1.4.x 的 AWS SDK 版本一致性。
