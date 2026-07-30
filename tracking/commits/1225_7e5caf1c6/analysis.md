# 提交 1225：Build: Bump software.amazon.awssdk:bom from 2.28.11 to 2.28.16 (#11268)

## 提交信息

- **序号**：1225 / 4088
- **哈希**：7e5caf1c6a8db6f978be1a617eba7cb1c8bb5a71
- **短哈希**：7e5caf1c6
- **日期**：2024-10-12（Sat Oct 12 21:09:25 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.28.11 to 2.28.16 (#11268)
- **PR/Issue**：#11268

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级提交。AWS SDK for Java v2（`software.amazon.awssdk`）是 Iceberg 与 AWS 服务（S3、Glue、DynamoDB、KMS 等）交互的核心依赖。Iceberg 通过引入 `awssdk-bom`（Bill of Materials）来统一管理所有 AWS SDK 模块的版本，确保各模块版本一致。

本次将 `awssdk-bom` 从 2.28.11 升级到 2.28.16，属于补丁版本（semver-patch）升级，跨越 5 个小版本（2.28.12 ~ 2.28.16），目的是获取最新的 bug 修复和服务端点更新。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本声明，从 `"2.28.11"` 改为 `"2.28.16"`。通过 BOM 机制，所有 AWS SDK 子模块（如 `s3`、`sts`、`glue`、`dynamodb`、`kms` 等）会自动同步到 BOM 中声明的对应版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 awssdk-bom 版本号。

**工作逻辑**：将第 32 行的版本声明从：

```toml
awssdk-bom = "2.28.11"
```

改为：

```toml
awssdk-bom = "2.28.16"
```

`awssdk-bom` 是 AWS SDK v2 的 BOM（物料清单），通过 Gradle 的 `platform` 机制引入后，会统一约束所有 `software.amazon.awssdk:*` 模块的版本。Iceberg 的 `aws` 模块、`s3` 模块等都依赖此 BOM 来管理 AWS SDK 版本。升级后，所有 AWS SDK 子模块（S3 客户端、STS、IAM、Glue Catalog 客户端等）将同步升级到 2.28.16 对应版本。

AWS SDK 2.28.x 系列的补丁版本通常包含：HTTP 客户端稳定性修复、服务模型更新（新增 API 参数或端点）、序列化/反序列化 bug 修复等。对于 Iceberg 而言，最相关的是 S3 客户端的改进和 Glue Catalog 客户端的稳定性修复。

## 小结

- **成效**：awssdk-bom 从 2.28.11 升级到 2.28.16，获取 5 个补丁版本的 bug 修复和服务模型更新。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动，无代码逻辑变更。但由于 BOM 约束所有 AWS SDK 模块，实际影响的依赖范围较广。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支当前使用的 awssdk-bom 版本远低于 main（1.4.x 通常使用 2.x 较低版本）。此升级属于补丁版本更新，风险较低。但 AWS SDK 是 Iceberg S3/AWS 集成的核心依赖，回迁后需重点验证：S3 文件读写、Glue Catalog 操作、STS 认证等关键路径是否正常工作。建议回迁后运行 AWS 相关集成测试。
