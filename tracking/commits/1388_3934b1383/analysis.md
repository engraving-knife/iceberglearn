# 提交 1388：Build: Bump software.amazon.awssdk:bom from 2.29.9 to 2.29.15 (#11568)

## 提交信息

- **序号**：1388 / 4088
- **哈希**：3934b1383db31aaeb75009f1fd725ca414a78381
- **短哈希**：3934b1383
- **日期**：2024-11-17（Sun Nov 17 08:29:46 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.29.9 to 2.29.15
- **PR/Issue**：#11568

## 总体目的

AWS SDK for Java v2 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 是 Iceberg `aws` 模块的核心依赖清单，统一管理 S3、DynamoDB、KMS、STS、Glue 等 AWS 服务客户端的版本。Iceberg 在 `gradle/libs.versions.toml` 中以 `awssdk-bom` 变量固定该 BOM 版本，所有 aws 相关依赖均通过该 BOM 对齐版本，避免版本碎片化。

本提交是 Dependabot 发起的补丁版本升级（2.29.9 → 2.29.15），跨 6 个补丁版本。目的是引入 AWS SDK 2.29.x 系列的 bug 修复、安全补丁与小改进，保持 SDK 为较新状态。属于日常依赖维护，无 API 破坏性变更。

## 如何达成设计目的

Dependabot 自动检测到 `gradle/libs.versions.toml` 中 `awssdk-bom = "2.29.9"` 有新版本 2.29.15 发布，遂创建 PR 将版本号升级。这是 `version-update:semver-patch` 类型升级，仅变更版本目录中的一行版本声明。所有引用 `awssdk-bom` 的 Gradle 依赖会自动解析到 2.29.15 对应的子模块版本。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

**修改目的**：升级 AWS SDK BOM 版本声明。

**工作逻辑**：

```diff
-awssdk-bom = "2.29.9"
+awssdk-bom = "2.29.15"
```

`libs.versions.toml` 是 Gradle 版本目录（Version Catalog）文件，`awssdk-bom` 变量被 `aws` 模块的 `build.gradle` 通过 `platform(libs.awssdk.bom)` 引入，确保所有 `software.amazon.awssdk:*` 依赖统一使用 2.29.15 版本。2.29.x 系列是 AWS SDK v2 的稳定补丁线，2.29.9 到 2.29.15 之间包含若干 bug 修复与安全补丁。

## 小结

- **成效**：将 AWS SDK BOM 从 2.29.9 升级到 2.29.15，引入 6 个补丁版本的修复，属纯依赖维护。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行变更，影响 `aws` 模块所有 AWS SDK 依赖的版本解析。不涉及 API 变更，编译和运行时行为应保持兼容。
- **回迁到 1.4.x 的注意事项**：可直接 cherry-pick。需确认 1.4.x 分支的 `libs.versions.toml` 中 `awssdk-bom` 当前版本；若已有其他 Dependabot 升级覆盖该行，按版本号较大者保留。AWS SDK 补丁版本升级通常向后兼容，但建议回迁后运行 `aws` 模块测试套件验证。
