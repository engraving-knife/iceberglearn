# 提交 0504：Build: Bump software.amazon.awssdk:bom from 2.23.17 to 2.24.0 (#9701)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0504 |
| 完整哈希 | fcd0663ca4addd86501f287b147cb70b544c3b0d |
| 短哈希 | fcd0663ca |
| 日期 | 2024-02-14（Wed Feb 14 21:16:18 2024 +0100） |
| 作者 | dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com> |
| 说明 | Build: Bump software.amazon.awssdk:bom from 2.23.17 to 2.24.0 (#9701) |
| PR | #9701 |
| 依赖类型 | direct:production |
| 更新类型 | version-update:semver-minor（次版本升级） |

提交统计：1 个文件修改，1 行新增，1 行删除。

涉及文件：`gradle/libs.versions.toml`

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg AWS 集成所依赖的 AWS SDK for Java v2 BOM（Bill of Materials）从 `2.23.17` 升级到 `2.24.0`，跨 1 个次版本。AWS SDK BOM 是 AWS 官方提供的依赖版本清单（`software.amazon.awssdk:bom`），通过 Gradle 的 `platform(...)` 机制导入后，统一约束所有 `software.amazon.awssdk:*` 构件（S3、KMS、Glue、STS、DynamoDB、Lake Formation、IAM、S3Control 等）的版本与互兼容性，避免逐个显式声明版本导致的版本漂移。Iceberg 的 `:iceberg-aws` 模块以该 BOM 为基准消费多个 AWS 服务客户端，`iceberg-aws-bundle` 模块则把 BOM 管控的客户端打进可分发的运行时胖包。

`2.23.17 → 2.24.0` 属次版本级别（`version-update:semver-minor`），按 AWS SDK v2 的版本策略，次版本升级通常新增服务客户端、新增 API 操作或扩展异步/同步客户端能力，同时保持既有 API 向后兼容（v2 在 2.x 系列内承诺兼容）。与同批次的 0502（assertj，patch）、0503（tez，patch）相比，本提交是次版本升级，幅度略大，但仍落在 v2 兼容承诺范围内。由于 AWS SDK 既出现在 `:iceberg-aws` 的 `compileOnly`/`testImplementation`（编译期与测试期），又出现在 `:iceberg-aws-bundle` 的 `implementation`（运行期打包），升级后 `iceberg-aws-bundle` 发布产物内携带的 AWS 客户端版本随之提升，但对外暴露的 Iceberg API 不变。回迁 1.4.x 时需注意：1.4.x 若已发布 `iceberg-aws-bundle`，升级 BOM 会改变其内嵌 AWS 客户端的次版本，但 Iceberg 自身 API 无影响。

## 如何达成设计目的

实现路径是单点修改：在 `gradle/libs.versions.toml` 的版本声明区把 `awssdk-bom = "2.23.17"` 改为 `awssdk-bom = "2.24.0"`。库定义区 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 通过 `version.ref` 引用该变量，无需改动；根 `build.gradle` 与 `aws-bundle/build.gradle` 中通过 `libs.awssdk.bom` 访问器并以 `platform(...)` 形式消费的位置亦无需改动。一处版本号变更即把 BOM 管控的全部 AWS 客户端统一约束到 2.24.0 对应的版本集。

## 修改详情

### `gradle/libs.versions.toml`

修改目的：把 AWS SDK for Java v2 BOM 的锁定版本从 `2.23.17` 提升到 `2.24.0`。

工作逻辑：

- 版本声明区第 28 行附近：`awssdk-bom = "2.23.17"` → `awssdk-bom = "2.24.0"`。
- 该版本变量被库定义区第 82 行附近的 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用。BOM 本身不含代码，仅是一份版本清单，导入后由 Gradle 的依赖约束机制统一管理 `software.amazon.awssdk:*` 各构件版本。
- 该 BOM 在三处被作为 platform 导入：
  - `build.gradle` 第 463 行（`:iceberg-aws` 项目，约第 447 行起）：`compileOnly(platform(libs.awssdk.bom))`，随后以 `compileOnly("software.amazon.awssdk:s3")`、`...:kms`、`...:glue`、`...:sts`、`...:dynamodb`、`...:lakeformation`、`...:auth`、`...:url-connection-client`、`...:apache-client` 等形式声明编译期所需客户端（版本由 BOM 约束，不显式写版本号）。
  - `build.gradle` 第 483 行（同 `:iceberg-aws` 项目）：`testImplementation(platform(libs.awssdk.bom))`，并追加 `...:iam`、`...:s3control` 等仅测试期需要的客户端，配合 `s3mock-junit5` 做 S3 模拟测试。
  - `aws-bundle/build.gradle` 第 27 行（`:iceberg-aws-bundle` 项目）：`implementation platform(libs.awssdk.bom)`，把 BOM 管控的客户端以 `implementation` 方式打进分发包，供终端用户免手动管理 AWS 依赖。
- 升级为次版本级别，AWS SDK v2 在 2.x 内承诺向后兼容，因此 `:iceberg-aws` 与 `:iceberg-aws-bundle` 源码中针对 AWS 客户端（`S3Client`、`GlueClient` 等）的调用代码无需改动；新增的服务客户端与 API 操作属可选增量，Iceberg 不强制使用。同文件中独立的 `awssdk-s3accessgrants = "2.0.0"`（S3 访问授权插件）采用独立版本变量，不受 BOM 升级影响。

## 小结

本提交是 Dependabot 触发的 AWS SDK v2 BOM 次版本升级：将 `gradle/libs.versions.toml` 中 `awssdk-bom` 由 `2.23.17` 升至 `2.24.0`。该 BOM 经 `platform(...)` 在 `:iceberg-aws`（`compileOnly`/`testImplementation`）与 `:iceberg-aws-bundle`（`implementation` 打包）三处导入，统一约束 S3/KMS/Glue/STS/DynamoDB/Lake Formation/IAM/S3Control 等客户端版本。升级属次版本级别，落在 AWS SDK v2 的 2.x 兼容承诺内，Iceberg 调用代码无需配合改动；对 Iceberg 公共 API 无影响，但 `iceberg-aws-bundle` 发布产物内嵌的 AWS 客户端版本随之提升。回迁 1.4.x 风险较低，仅需同步该一行版本号。
