# 提交 2718：Build: Bump com.azure:azure-sdk-bom from 1.2.38 to 1.3.0

## 提交信息

- **序号**：2718 / 4088
- **哈希**：a9a237fd7aece1e5ec23553d32a84109026a085f
- **短哈希**：a9a237fd7
- **日期**：2025-10-05 09:33:12 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.38 to 1.3.0
- **PR/Issue**：#14257

## 总体目的

此提交是 Dependabot 自动生成的依赖版本升级，将 Azure SDK for Java 的 BOM（Bill of Materials）从 1.2.38 升级到 1.3.0。

Azure SDK BOM 是 Iceberg 中 Azure 模块（`iceberg-azure`）使用的依赖，用于与 Azure Blob Storage、Data Lake Storage Gen2、Azure Active Directory 等 Azure 服务交互。BOM 用于统一管理 Azure SDK 各子模块的版本号，确保版本兼容。

1.2.38 到 1.3.0 是一个 semver-minor 版本升级（版本号从 1.2.x 跳到 1.3.x），意味着包含新功能和改进。值得注意的是，Azure SDK BOM 的版本号体系从 `1.2.38` 直接跳到 `1.3.0`，这表明 Azure SDK 团队调整了版本策略。

## 如何达成设计目的

通过在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中将 `azuresdk-bom` 的版本号从 `1.2.38` 更新为 `1.3.0` 来完成升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Azure SDK BOM 版本号。

**工作逻辑**：将 `azuresdk-bom` 变量从 `"1.2.38"` 更改为 `"1.3.0"`。所有通过 `version.ref = "azuresdk-bom"` 引用此版本的 Azure SDK 模块将自动使用新版本。这是一次 minor 版本升级，通常包含新 API 和功能增强，同时保持向后兼容。

## 总结

此提交是例行依赖维护，将 Azure SDK for Java 从 1.2.38 升级到 1.3.0。作为 semver-minor 升级，预计包含新功能和改进。这确保了 Iceberg 的 Azure 集成使用最新的 Azure SDK，获得最新的功能和安全修复。需要注意 Azure SDK 版本号体系的跳变可能伴随新的发布策略。
