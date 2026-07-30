# 提交 2939：Build: Bump com.azure:azure-sdk-bom from 1.3.2 to 1.3.3 (#14717)

## 提交信息

- **序号**：2939 / 4088
- **哈希**：f3949cea069abd9339a150e6e8df8b90ee9f6675
- **短哈希**：f3949cea0
- **日期**：2025-11-30
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.3.2 to 1.3.3 (#14717)
- **PR/Issue**：#14717

## 总体目的

这是由 Dependabot 自动发起的依赖升级，目标是将 `com.azure:azure-sdk-bom` 从 1.3.2 抬升到 1.3.3。

`azure-sdk-bom` 是 Azure SDK for Java 官方提供的 BOM（Bill of Materials），通过 Gradle 的 `platform(...)` 语法引入后，会统一对齐所有 `com.azure:*` 模块（如 `azure-storage-file-datalake`、`azure-identity` 等）的版本，避免各个 Azure 子模块之间出现版本不匹配的兼容性问题。在 Iceberg 项目里，这个 BOM 主要服务于 Azure 数据湖（ADLS）相关集成：

- 在 `build.gradle` 第 534 行附近以 `compileOnly platform(libs.azuresdk.bom)` 形式被 `azure` 模块引入，同时引入 `com.azure:azure-storage-file-datalake` 与 `com.azure:azure-identity`；
- 在 `azure-bundle/build.gradle` 第 27 行以 `implementation platform(libs.azuresdk.bom)` 形式被打包模块使用，用于 shade 打包 Azure 相关依赖。

从语义版本看，本次为 `semver-patch` 补丁版本升级（1.3.2 → 1.3.3），按 Azure SDK 团队的版本策略，patch 版本一般只包含 bug 修复和兼容性改进，不引入新 API、不破坏既有契约。Dependabot 元数据也明确标注为 `version-update:semver-patch`、`direct:production`，属于最低风险的依赖维护。

## 如何达成设计目的

改动只涉及 Gradle 版本目录 `gradle/libs.versions.toml` 中 `azuresdk-bom` 别名的版本字符串，从 `1.3.2` 修改为 `1.3.3`。由于下游引用（`build.gradle` 与 `azure-bundle/build.gradle`）都通过 `version.ref = "azuresdk-bom"` 间接指向该别名，一处抬升即可让所有使用 Azure BOM 的模块同步获得新版本，无需逐处修改引用代码。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将版本目录中 `azuresdk-bom` 别名的固定版本从 `1.3.2` 修改为 `1.3.3`。

**工作逻辑**：
该文件是 Gradle 集中依赖版本目录。第 32 行定义了别名版本，第 83 行定义了 BOM 坐标：

```toml
azuresdk-bom = "1.3.3"   # 第 32 行
azuresdk-bom = { module = "com.azure:azure-sdk-bom", version.ref = "azuresdk-bom" }  # 第 83 行
```

本次仅修改第 32 行的版本号字符串。下游通过 `platform(libs.azuresdk.bom)` 引用，会自动拉取 1.3.3 版本的 BOM，进而把 `azure-storage-file-datalake`、`azure-identity` 等子模块的版本统一对齐到 BOM 内部声明的版本，确保 Azure 相关集成的版本一致性。由于是 patch 升级，预期只是修复 BOM 内部某些子模块的兼容性或缺陷，不会引入新 API。

## 总结

本次提交是常规的依赖维护，把 Azure SDK for Java 的 BOM 从 1.3.2 抬升到 1.3.3 一个 patch 版本，保持 Iceberg Azure（ADLS）集成所用的 Azure 子模块版本与上游同步。由于是 BOM 且为 patch 升级，对运行时影响极小，主要价值在于纳入 Azure 团队最新发布的兼容性修复。
