# 提交 3557：Build: Bump com.azure:azure-sdk-bom from 1.3.5 to 1.3.6 (#16037)

## 提交信息

- **序号**：3557 / 4088
- **哈希**：3111ba588b7e5b1bb2ee3e3aa7855e12e42420eb
- **短哈希**：3111ba588
- **日期**：2026-04-19 07:15:37 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.3.5 to 1.3.6 (#16037)
- **PR/Issue**：#16037

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 Azure SDK for Java 的 BOM（Bill of Materials）依赖 `com.azure:azure-sdk-bom` 从版本 1.3.5 升级到 1.3.6。Azure SDK BOM 用于统一管理 Azure 相关依赖的版本，确保各 Azure 模块版本兼容。这是一个 semver-patch（补丁版本）升级，通常包含 bug 修复和小幅改进，向后兼容。

## 如何达成设计目的

Dependabot 自动扫描项目依赖，发现 `gradle/libs.versions.toml` 中定义的 `azuresdk-bom` 版本有新的补丁版本可用，于是自动创建 PR 进行升级。升级仅修改版本号声明，不涉及代码逻辑变更。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 Azure SDK BOM 版本声明。

**工作逻辑**：
将 `azuresdk-bom` 的版本从 `1.3.5` 升级到 `1.3.6`：

```toml
-azuresdk-bom = "1.3.5"
+azuresdk-bom = "1.3.6"
```

该版本声明通过 Gradle 版本目录（version catalog）机制被项目中的 Azure 相关模块引用，升级后所有依赖该 BOM 的 Azure SDK 组件版本会统一更新到 1.3.6 对应的兼容版本。

## 总结

这是一个常规的依赖维护提交，通过补丁版本升级保持 Azure SDK 依赖的最新状态，获取最新的 bug 修复和改进，同时保持向后兼容性。
