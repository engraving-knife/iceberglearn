# 提交 0837：Build: Bump software.amazon.awssdk:bom from 2.25.69 to 2.26.3 (#10505)

## 提交信息
- **序号**：0837 / 4088
- **哈希**：26dc338a4d3ffd6e0f3cda1c7be65f5f299c1824
- **短哈希**：26dc338a4
- **日期**：2024-06-16
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.25.69 to 2.26.3 (#10505)
- **PR/Issue**：#10505

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 项目使用的 AWS SDK for Java v2 的 BOM（Bill of Materials）从 `2.25.69` 升级到 `2.26.3`。AWS SDK BOM 是一个集中管理 AWS SDK 各模块版本的 POM 文件，引入后所有 AWS SDK 子模块（如 s3、kms、sts、dynamodb、glue 等）的版本都由该 BOM 统一对齐，避免逐个模块声明版本导致的不一致。

Iceberg 在 `aws` 模块（`org.apache.iceberg:iceberg-aws`）中重度依赖 AWS SDK v2 来对接 S3、DynamoDB（作为锁与提交统计表）、Glue（作为目录元数据存储）、STS（凭证获取）等服务；`aws-bundle` 模块还会把 AWS SDK 打成 shaded/fat jar。因此 BOM 版本直接关系到与 AWS 服务的兼容性、安全性以及客户端行为（如重试、签名、HTTP 客户端实现等）。

本次升级属于次要版本（minor）升级，从 `2.25.x` 系列跨入 `2.26.x` 系列。AWS SDK v2 在 minor 版本升级时通常不会破坏公共 API，但可能新增特性、修复缺陷、调整默认行为或加入对新的服务 API 版本的支持。维护者在合并此类 PR 时一般会通过 CI（包括 `aws` 模块的集成测试与 `IcebergAwsIntegrationTest` 等测试）验证升级后行为符合预期。

## 如何达成设计目的

提交通过修改版本目录 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本字符串实现升级。Iceberg 的依赖管理采用 Gradle Version Catalog + `platform(...)` 机制：

1. 在 `gradle/libs.versions.toml` 中定义 `awssdk-bom = "2.26.3"` 及其坐标别名 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }`。
2. 在根 `build.gradle` 中通过 `compileOnly(platform(libs.awssdk.bom))` 和 `testImplementation(platform(libs.awssdk.bom))` 引入 BOM 作为平台依赖，从而让所有子项目在编译期与测试期都能从 BOM 解析 AWS SDK 各模块的版本。
3. 在 `aws-bundle/build.gradle` 中通过 `implementation platform(libs.awssdk.bom)` 引入，保证打包时使用一致的 AWS SDK 版本。

由于 BOM 通过 `version.ref` 引用单一变量，只需将版本号从 `2.25.69` 改为 `2.26.3`，整个构建树中所有显式或隐式依赖 AWS SDK 的模块都会自动对齐到 2.26.3 版本，确保运行时一致。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：将 `awssdk-bom` 版本从 `2.25.69` 升级到 `2.26.3`。

**工作逻辑**：版本定义条目修改如下：

```toml
awssdk-bom = "2.25.69"     # 旧
awssdk-bom = "2.26.3"      # 新
```

坐标别名 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 不变。下游 `build.gradle` 中的 `platform(libs.awssdk.bom)` 调用会自动解析到新版本。受影响的关键 AWS SDK 模块包括 `software.amazon.awssdk:s3`、`software.amazon.awssdk:sts`、`software.amazon.awssdk:kms`、`software.amazon.awssdk:glue`、`software.amazon.awssdk:dynamodb`、`software.amazon.awssdk:iam` 等，它们各自的版本将由 BOM 决定。

## 小结
- **成效**：将 AWS SDK v2 BOM 升级到 2.26.3，统一管理 AWS SDK 各模块版本，获取上游缺陷修复、新特性以及与服务 API 的兼容性更新。
- **影响范围**：影响所有直接或间接依赖 AWS SDK 的模块（特别是 `iceberg-aws` 与 `iceberg-aws-bundle`），以及 `IcebergAwsIntegrationTest` 等集成测试；由于是 minor 升级，公共 API 应保持向后兼容，但需注意客户端默认行为可能微调（如 HTTP 客户端、重试策略、region 解析等）。
- **回迁注意事项**：回迁到 1.4.x 时只需将 `gradle/libs.versions.toml` 中 `awssdk-bom` 改为 `2.26.3`。若 1.4.x 当前停留在更早的版本（如 `2.20.131`），从 `2.20.x` 一次性跨到 `2.26.x` 涉及多个 minor 版本，建议回迁后额外跑一次 `aws` 模块的集成测试和 S3 相关 mock 测试，重点验证：S3 客户端配置、`S3AccessGrants` 相关依赖（`awssdk-s3accessgrants = "2.0.0"` 独立版本，不受 BOM 影响，需保持）、签名版本（SigV4 vs SigV2）以及 `software.amazon.awssdk:apache-client` / `url-connection-client` / `netty-nio-client` 等 HTTP 客户端实现的兼容性。AWS SDK 在 2.25→2.26 之间若有 default HTTP client 行为变化，可能在某些 S3 兼容存储（如 MinIO、Ceph RGW）上引发问题，回迁时需要关注。
