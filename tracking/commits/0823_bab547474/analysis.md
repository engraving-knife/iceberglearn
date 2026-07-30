# 提交 0823：Build: Bump software.amazon.awssdk:bom from 2.25.64 to 2.25.69 (#10466)

## 提交信息

- **序号**：0823 / 4088
- **哈希**：bab547474e2dcae7fab76164107404419f4d4fe1
- **短哈希**：bab547474
- **日期**：2024-06-10 07:34:28 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.25.64 to 2.25.69 (#10466)
- **PR/Issue**：#10466

## 总体目的

本提交由 Dependabot 自动生成，将 AWS SDK for Java 的 BOM（Bill of Materials，依赖版本清单）从 `2.25.64` 升级到 `2.25.69`，跨越 5 个 patch 版本。AWS SDK BOM 是 Iceberg 项目访问 AWS 云服务（S3、Glue、DynamoDB、KMS、STS、LakeFormation、IAM 等）的核心依赖，统一管理所有 AWS SDK 模块的版本，确保各模块版本一致。

此次升级属于 SemVer patch 级别升级（2.25.64 → 2.25.69），目的是获取 AWS SDK 在该版本区间内的缺陷修复、安全补丁和小幅改进，保持与 AWS 服务的最新兼容性。

## 如何达成设计目的

提交仅修改了 Gradle 版本目录（Version Catalog）文件 `gradle/libs.versions.toml` 中的一行版本号声明：

1. **版本号声明**：将 `awssdk-bom = "2.25.64"` 改为 `awssdk-bom = "2.25.69"`。该变量通过 `version.ref` 被 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用。

2. **BOM 传递机制**：AWS SDK BOM 是一个 POM 文件，通过 Gradle 的 platform 机制引入后，所有 `software.amazon.awssdk:*` 模块（如 `s3`、`glue`、`kms` 等）的版本由 BOM 统一管理，无需逐个声明版本号。因此只需修改 BOM 版本号，所有 AWS SDK 子模块版本随之统一升级。

3. **无需修改使用处**：`build.gradle` 中通过 `compileOnly("software.amazon.awssdk:s3")`、`compileOnly("software.amazon.awssdk:glue")` 等方式引用各模块（不带版本号，由 BOM 控制），升级 BOM 版本后这些引用自动指向新版本，无需改动。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK BOM 版本从 2.25.64 升级到 2.25.69。

**工作逻辑**：
- 修改位于版本目录的 `[versions]` 段，第 31 行附近。
- `awssdk-bom` 版本变量被同文件的 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 库声明引用（约第 82 行）。
- BOM 在 `build.gradle` 中通过 `implementation platform(libs.awssdk.bom)` 引入（作为依赖平台），此后所有 AWS SDK 模块依赖的版本由该 BOM 决定。
- Iceberg 使用的 AWS SDK 模块包括（见 `build.gradle` 第 464-472、484 行）：`url-connection-client`、`apache-client`、`auth`、`s3`、`kms`、`glue`、`sts`、`dynamodb`、`lakeformation`、`iam`。这些模块在 2.25.64 到 2.25.69 区间内的 patch 更新将一并生效。
- 2.25.64 到 2.25.69 的 patch 升级通常包含：HTTP 客户端稳定性修复、服务模型（service model）更新（反映 AWS 服务 API 的最新变更）、已知缺陷修复等，不涉及破坏性 API 变更。

## 小结

- **成效**：AWS SDK for Java 升级到 2.25.69，获取了 5 个 patch 版本累积的缺陷修复和服务模型更新，提升了与 AWS 服务的兼容性和客户端稳定性。
- **影响范围**：影响所有使用 AWS SDK 的模块，包括 S3 文件系统（S3FileIO）、Glue 目录服务集成、DynamoDB 锁服务、KMS 加密、STS 认证、LakeFormation 权限集成等。由于是 patch 级升级，API 兼容，运行时行为基本不变，但可能修复特定场景下的客户端缺陷。
- **回迁注意事项**：回迁到 1.4.x 分支时需注意版本差异——当前 1.4.x 分支的 `awssdk-bom` 版本为 `2.20.131`（远低于 2.25.69），说明 1.4.x 分支与 main 分支的 AWS SDK 版本差距较大。直接回迁此单个提交（2.25.64→2.25.69）不适用，因为 1.4.x 分支尚未升级到 2.25.x 系列。建议在 1.4.x 分支上整体评估是否需要将 AWS SDK 升级到 2.25.x 系列，而非单独应用此 patch 升级。AWS SDK 2.x 内部 patch 升级通常向后兼容，回迁风险低。
