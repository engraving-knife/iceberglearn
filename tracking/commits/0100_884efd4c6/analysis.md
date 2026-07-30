# 提交 0100：Build: Bump software.amazon.awssdk:bom from 2.21.5 to 2.21.10 (#8943)

## 提交信息

- **序号**：0100 / 4088
- **哈希**：884efd4c6612b801dab71b6137032f5a5e08087a
- **短哈希**：884efd4c6
- **日期**：2023-10-30
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.21.5 to 2.21.10 (#8943)
- **PR/Issue**：#8943

## 总体目的

本提交由 Dependabot 自动生成，将 AWS SDK for Java 2.x 的 BOM（Bill of Materials）`software.amazon.awssdk:bom` 从 2.21.5 升级到 2.21.10（semver patch 级别升级）。

AWS SDK BOM 是 Iceberg 与 AWS 服务集成（S3、DynamoDB、Glue 等，对应 `aws`、`aws-bundle` 模块）时统一管理 AWS SDK 各组件版本的对齐机制。通过在 `gradle/libs.versions.toml` 中以 `awssdk-bom` 版本字符串集中声明，再以 platform 方式引入依赖，可确保所有 AWS SDK 模块版本一致、避免组件间不兼容。2.21.5 → 2.21.10 是同一 2.21 minor 系列内的补丁迭代，通常包含若干 bug 修复、稳定性与安全补丁，不引入破坏性 API 变更。Iceberg 在 S3 表存储、DynamoDB 锁与提交、Glue 目录等场景深度依赖该 SDK，定期跟进补丁版本有助于获取上游修复、降低安全风险、保持与 AWS 服务的兼容性。

## 如何达成设计目的

改动单一：在 `gradle/libs.versions.toml` 中将 `awssdk-bom` 版本字符串从 `2.21.5` 改为 `2.21.10`。由于项目通过 version catalog 统一管理依赖版本，且各模块以 platform 方式消费该 BOM，单一字符串修改即可让所有 AWS SDK 组件同步升级。Dependabot 在 PR 描述中附带了 `updated-dependencies` 元数据，标明依赖类型为 `direct:production`、更新类型为 `version-update:semver-patch`，并由 bot 签名（`support@github.com`）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK BOM 版本从 2.21.5 升级到 2.21.10，统一拉齐所有 AWS SDK 组件版本。

**工作逻辑**：

修改位于 `gradle/libs.versions.toml` 第 30 行附近的版本声明区：

```diff
-awssdk-bom = "2.21.5"
+awssdk-bom = "2.21.10"
```

该键被项目内 AWS 相关模块（如 `aws`、`aws-bundle`）以 `platform("software.amazon.awssdk:bom:${libs.versions.awssdk.bom.get()}")` 形式引入，BOM 会传递性地把 S3、DynamoDB、Glue、STS、KMS 等 SDK 组件版本对齐到 2.21.10。修改后构建会拉取 2.21.10 系列的 AWS SDK 制品。

## 小结

本提交将 AWS SDK for Java BOM 从 2.21.5 升级到 2.21.10，是 Iceberg AWS 集成依赖的常规维护升级，用于获取上游补丁修复与安全改进。
