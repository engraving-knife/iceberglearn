# 提交 0809：Build: Bump software.amazon.awssdk:bom from 2.25.60 to 2.25.64 (#10421)

## 提交信息

- **序号**：0809 / 4088
- **哈希**：45bdf3fd438aa8d2919282947e6c820117453ec6
- **短哈希**：45bdf3fd4
- **日期**：2024-06-04 08:22:28 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.25.60 to 2.25.64 (#10421)
- **PR/Issue**：#10421（Dependabot 自动创建）

## 总体目的

本提交由 Dependabot 自动生成，目的是将 AWS SDK for Java 2 的 BOM（Bill of Materials）版本从 `2.25.60` 升级到 `2.25.64`，跨 4 个 patch 版本。这是一次依赖版本更新（update-type: version-update:semver-patch），属于常规的依赖维护工作，用于获取 AWS SDK 在 2.25.61~2.25.64 期间发布的 bug 修复、安全补丁与小改进，保持依赖的最新状态。

AWS SDK BOM 是一个"物料清单"型 POM，它本身不含代码，而是集中声明了 AWS SDK 各模块（s3、kms、glue、sts、dynamodb、lakeformation、iam、auth、apache-client、url-connection-client 等）的统一版本。通过引入 BOM（`platform(libs.awssdk.bom)`），项目可以在引用各个 AWS SDK 模块时不显式指定版本，由 BOM 统一管控，避免版本不一致。因此本次升级会一次性把所有被 BOM 管控的 AWS SDK 模块版本同步抬升到 2.25.64。

按 AWS SDK for Java 2 的语义化版本承诺，patch 版本升级保持 API 兼容，仅包含修复与改进，不应引入破坏性变更。

## 如何达成设计目的

Dependabot 扫描到 `gradle/libs.versions.toml` 中 `awssdk-bom` 版本落后，自动提交 PR 将版本字符串从 `2.25.60` 改为 `2.25.64`。由于项目使用 Gradle Version Catalog（`libs.versions.toml`）集中管理版本，且 `awssdk-bom` 通过 `version.ref` 被 `software.amazon.awssdk:bom` 库引用，因此只需修改这一处版本号，所有通过 `platform(libs.awssdk.bom)` 引入该 BOM 的模块都会自动应用新版本。

升级内容（2.25.60 → 2.25.64）：属于 AWS SDK for Java 2 的 patch 级别迭代，按其版本策略包含 bug 修复与依赖内部更新，不涉及 API 破坏性变更。

影响路径：
- `build.gradle`（根项目）：`compileOnly(platform(libs.awssdk.bom))` 与 `testImplementation(platform(libs.awssdk.bom))`，以及一系列 `compileOnly("software.amazon.awssdk:...")`、`testImplementation("software.amazon.awssdk:...")` 的模块（s3、kms、glue、sts、dynamodb、lakeformation、auth、apache-client、url-connection-client、iam、s3control）。
- `aws-bundle/build.gradle`：`implementation platform(libs.awssdk.bom)` 及多个 `implementation "software.amazon.awssdk:..."` 模块（apache-client、auth、iam、sso、s3、kms、glue、sts、dynamodb、lakeformation）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK BOM 版本从 2.25.60 升级到 2.25.64。

**工作逻辑**：

```toml
# 修改前
awssdk-bom = "2.25.60"

# 修改后
awssdk-bom = "2.25.64"
```

该版本号通过 `version.ref = "awssdk-bom"` 被 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用。所有以 `platform(libs.awssdk.bom)` 引入 BOM 的 Gradle 配置（根 `build.gradle` 的 compileOnly/testImplementation、`aws-bundle/build.gradle` 的 implementation）会自动使用新版本，进而统一管控所有 `software.amazon.awssdk:*` 模块的版本。这是 Version Catalog 的单点修改，无需改动任何 build.gradle。

## 小结

- **成效**：将 AWS SDK for Java 2 全套模块版本统一从 2.25.60 升级到 2.25.64，获取 4 个 patch 版本累积的 bug 修复与依赖更新。
- **影响范围**：影响所有依赖 AWS SDK 的模块，主要是 `aws`、`aws-bundle`，以及根项目 build.gradle 中声明的 compileOnly/testImplementation 配置。涉及 S3、KMS、Glue、STS、DynamoDB、LakeFormation、IAM、SSO、auth、apache-client、url-connection-client、s3control 等 AWS SDK 模块。由于是 patch 级别升级，按 AWS SDK 语义化版本承诺保持 API 兼容，运行时行为不应有破坏性变化。
- **兼容性**：semver-patch 升级，API 兼容。AWS SDK 2.x 在 patch 版本中只发布修复与内部改进，不引入破坏性 API 变更。Iceberg 的 `aws` 模块代码无需任何适配。
- **回迁注意事项**：(1) 依赖升级类提交，回迁到 1.4.x 安全且推荐，可保持 1.4.x 与 main 的依赖版本一致，减少安全风险；(2) 回迁时需确认 1.4.x 的 `gradle/libs.versions.toml` 中 `awssdk-bom` 当前版本（若 1.4.x 已通过其它 backport 升级到更高版本，则无需回迁本提交；若仍为 2.25.60 或更低，则可直接套用本改动）；(3) 回迁后建议运行 `aws`、`aws-bundle` 模块的测试以验证升级无回归；(4) 若 1.4.x 的 AWS SDK 模块集合与 main 有差异，需注意 BOM 升级对所有 `software.amazon.awssdk:*` 模块生效，应确认无被移除的模块导致解析失败。
