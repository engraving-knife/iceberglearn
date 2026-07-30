# 提交 0473：Build: Bump software.amazon.awssdk:bom from 2.23.12 to 2.23.17 (#9633)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0473 |
| 完整哈希 | e61b0e51e151fb64cb5e39b49ea47c0809c3f84a |
| 短哈希 | e61b0e51e |
| 日期 | 2024-02-06（Tue Feb 6 07:48:51 2024 -0800） |
| 作者 | dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com> |
| 说明 | Build: Bump software.amazon.awssdk:bom from 2.23.12 to 2.23.17 (#9633) |
| PR | #9633 |
| 依赖类型 | direct:production |
| 更新类型 | version-update:semver-patch（补丁版本升级） |

提交统计：1 个文件修改，1 行新增，1 行删除。

涉及文件：`gradle/libs.versions.toml`

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 项目依赖的 AWS SDK for Java 2.x 的 BOM（Bill of Materials，`software.amazon.awssdk:bom`）从 `2.23.12` 升级到 `2.23.17`，跨 5 个补丁版本。AWS SDK 的 BOM 是一个“版本清单”工件，它本身不引入任何代码，而是统一管理 AWS SDK 各模块（`s3`、`kms`、`glue`、`sts`、`dynamodb`、`lakeformation`、`iam`、`sso`、`auth`、`apache-client`、`url-connection-client`、`s3control` 等）的版本，确保所有模块版本互相兼容。Iceberg 通过 Gradle 的 `platform(libs.awssdk.bom)` 在三处引入该 BOM：`aws-bundle/build.gradle`（implementation 作用域，打包进 `iceberg-aws-bundle`）以及根 `build.gradle`（compileOnly 与 testImplementation 作用域，分别用于主代码编译期与测试期）。

升级补丁版本（2.23.12 → 2.23.17）属于 AWS SDK 的常规维护性升级，通常包含缺陷修复、稳定性改进与小幅性能优化，不引入新的 API 破坏性变更。Dependabot 将其归类为 `version-update:semver-patch`，意味着按语义化版本约定这是向后兼容的补丁更新。定期跟进这类补丁升级是 Iceberg 保持依赖健康、及时获得上游 bug 修复（例如 S3 客户端、凭证提供链、HTTP 客户端稳定性等方面）的常规实践，也能减小未来升级到次版本时累积的 diff 体量。由于仅改动 BOM 版本字符串且为补丁级别，本提交对 Iceberg 自身代码与公共 API 零影响，回迁到 1.4.x 风险极低。

## 如何达成设计目的

实现路径是单点修改：在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，把 `[versions]` 段下的 `awssdk-bom = "2.23.12"` 改为 `awssdk-bom = "2.23.17"`。该版本键被 `[libraries]` 段的 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 通过 `version.ref` 引用，而后者又通过 `libs.awssdk.bom` 在各 `build.gradle` 中以 `platform(...)` 形式被消费，因此这一处字符串变更会自动传导到所有受 BOM 管控的 AWS SDK 模块版本，无需修改任何构建脚本或代码。

## 修改详情

### `gradle/libs.versions.toml`

修改目的：把 AWS SDK BOM 的版本号从 `2.23.12` 提升到 `2.23.17`。

工作逻辑：

- 该文件是 Gradle 版本目录（Version Catalog），集中声明项目所有依赖的版本与坐标。`[versions]` 段第 31 行原为 `awssdk-bom = "2.23.12"`，本提交改为 `awssdk-bom = "2.23.17"`，其余行不变。
- 该版本键在 `[libraries]` 段被引用为 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }`，即库坐标 `software.amazon.awssdk:bom` 的版本由 `awssdk-bom` 版本键决定。
- 在构建脚本侧，该 BOM 通过 `platform(libs.awssdk.bom)` 被引入三处：
  - `aws-bundle/build.gradle` 第 27 行 `implementation platform(libs.awssdk.bom)`，随后以无版本形式声明 `software.amazon.awssdk:apache-client`、`auth`、`iam`、`sso`、`s3`、`kms`、`glue`、`sts`、`dynamodb`、`lakeformation` 等模块（版本由 BOM 提供），这些模块会被打进 `iceberg-aws-bundle` 胖包；
  - 根 `build.gradle` 第 463 行 `compileOnly(platform(libs.awssdk.bom))`，随后 `compileOnly` 声明 `url-connection-client`、`apache-client`、`auth`、`s3`、`kms`、`glue`、`sts`、`dynamodb`、`lakeformation`（编译期可见，不打包）；
  - 根 `build.gradle` 第 483 行 `testImplementation(platform(libs.awssdk.bom))`，随后 `testImplementation` 声明 `iam`、`s3control` 等用于测试。
- 因此本次 BOM 版本提升后，上述所有 AWS SDK 模块的版本会统一从 2.23.12 系列跳到 2.23.17 系列，由 BOM 内部保证各模块互相兼容；Iceberg 自身代码无需任何改动。

## 小结

本提交是 Dependabot 触发的常规依赖补丁升级：将 `software.amazon.awssdk:bom` 在 `gradle/libs.versions.toml` 中由 `2.23.12` 升至 `2.23.17`，通过 `version.ref` 与 `platform(...)` 机制自动传导到 `aws-bundle/build.gradle` 与根 `build.gradle` 中所有受 BOM 管控的 AWS SDK 模块（s3/kms/glue/sts/dynamodb/lakeformation/iam/sso/auth/客户端等）。升级属补丁级别、向后兼容，不涉及代码与 API 变更，主要用于跟进 AWS SDK 上游的缺陷修复与稳定性改进，是 Iceberg 依赖健康维护的典型一环，回迁 1.4.x 风险极低。
