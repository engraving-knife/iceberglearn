# 提交 0054：Build: Bump software.amazon.awssdk:bom from 2.20.162 to 2.21.0 (#8838)

## 提交信息

- **序号**：0054 / 4088
- **哈希**：738103fb18328a26893679ed3ce45a631d73e830
- **短哈希**：738103fb1
- **日期**：2023-10-16 07:50:04 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.20.162 to 2.21.0 (#8838)
- **PR/Issue**：#8838

## 总体目的

本提交由 Dependabot 自动生成，目的是把 Iceberg 依赖的 AWS SDK for Java（`software.amazon.awssdk:bom`）版本从 `2.20.162` 升级到 `2.21.0`，以获取上游的新版本修复与改进，并保持依赖的最新状态。

背景与动机上，Iceberg 的 AWS 集成模块（`iceberg-aws` 与打包用的 `iceberg-aws-bundle`）依赖 AWS SDK for Java v2 来访问 S3、Glue、DynamoDB、KMS、STS、LakeFormation 等服务。AWS SDK 的 BOM（Bill of Materials）通过 Gradle 的 `platform(libs.awssdk.bom)` 引入，统一管理所有 AWS SDK 子模块的版本，避免各模块版本不一致。Dependabot 会定期扫描依赖并提交 PR 升级到新版本；本次属于 `semver-minor` 级别的升级（2.20.x → 2.21.x），通常会带来新的 API、bug 修复与性能改进，但不应有破坏性变更。

对 Iceberg 演进的意义在于：保持 AWS SDK 这种基础设施依赖的现代化，可以让用户受益于 AWS 服务的最新特性与稳定性修复，同时也减少了 Iceberg 项目自身的维护负担（不必手动跟进每个 patch 版本）。这类自动化的依赖升级是现代开源项目保持活力的常规手段。

## 如何达成设计目的

通过修改 Gradle 版本目录（version catalog）`gradle/libs.versions.toml` 中 `awssdk-bom` 这一项的版本字符串，从 `2.20.162` 改为 `2.21.0`。由于项目用 BOM + `version.ref` 的方式集中管理 AWS SDK 版本，所有引用 `awssdk-bom` 的位置（如 `build.gradle` 中的 `platform(libs.awssdk.bom)` 以及 `iceberg-aws-bundle` 模块）都会自动获得新版本，无需多处改动。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 AWS SDK BOM 版本从 2.20.162 升级到 2.21.0。

**工作逻辑**：

修改前（位于版本目录的 `[versions]` 段，第 13 行附近）：
```toml
awssdk-bom = "2.20.162"
```

修改后：
```toml
awssdk-bom = "2.21.0"
```

该版本号通过 `version.ref` 被 `[libraries]` 段的 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，进而被根 `build.gradle` 的 `compileOnly(platform(libs.awssdk.bom))` 与 `testImplementation(platform(libs.awssdk.bom))` 使用，统一约束所有 `software.amazon.awssdk:*` 子模块（s3、glue、dynamodb、kms、sts、lakeformation、auth、apache-client、url-connection-client、iam、s3control 等）的版本。因此单行改动即可让整个项目的 AWS SDK 依赖（编译期与测试期）以及 `iceberg-aws-bundle` 打包出来的 AWS SDK 版本同步升级到 2.21.0。

版本变化含义：从 `2.20.162`（2.20 系列的某个 patch 版本）升级到 `2.21.0`（2.21 系列的首个 minor 版本），属于 AWS SDK 2.x 内的 minor 升级，按 SemVer 约定向后兼容。

## 小结

本提交通过单行版本目录改动把 AWS SDK for Java BOM 从 2.20.162 升级到 2.21.0，是 Dependabot 的常规依赖升级，使 Iceberg 的 AWS 集成模块与打包的 `iceberg-aws-bundle` 跟上 AWS SDK 2.21.x 系列。
