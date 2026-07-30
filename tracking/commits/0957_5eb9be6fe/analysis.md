# 提交 0957：Build: Bump software.amazon.awssdk:bom from 2.26.20 to 2.26.21 (#10729)

## 提交信息

- **序号**：0957 / 4088
- **哈希**：5eb9be6fed667d8f07b7f3f50aa01312ae535add
- **短哈希**：5eb9be6fe
- **日期**：2024-07-22（Mon Jul 22 09:18:30 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.26.20 to 2.26.21 (#10729)
- **PR/Issue**：#10729

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。AWS SDK for Java 2.x 的 BOM（Bill of Materials）用于统一管理 AWS SDK 各模块（如 S3、DynamoDB、Glue、STS、KMS 等）的版本，避免模块间版本不一致。Iceberg 的 `iceberg-aws` 模块通过该 BOM 引用 AWS SDK 来实现 S3FileIO、GlueCatalog、DynamoDB 锁定等与 AWS 服务交互的能力。

本次提交将 `software.amazon.awssdk:bom` 从 2.26.20 升级到 2.26.21（semver patch 版本升级），属于 patch 级别的小版本升级，通常仅包含 bug 修复和小幅改进。目的是保持 AWS SDK 依赖的最新修复版本，跟进上游对 S3 客户端、签名、HTTP 客户端等方面的修复，降低与 AWS 服务交互时的已知问题风险。

## 如何达成设计目的

实现方式为修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将其中 `awssdk-bom` 的版本声明从 `2.26.20` 改为 `2.26.21`。由于 BOM 通过 platform 机制统一管理所有 AWS SDK 模块版本，单点修改即可让所有 AWS SDK 模块同步升级到 2.26.21 对应的版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AWS SDK BOM 依赖版本从 2.26.20 升级到 2.26.21。

**工作逻辑**：仅修改版本目录中的一行：

```diff
-awssdk-bom = "2.26.20"
+awssdk-bom = "2.26.21"
```

修改后，所有通过该 BOM 引用 AWS SDK 模块的子项目（如 `iceberg-aws`、`iceberg-aws-bundle`、S3 集成测试等）在构建时拉取 2.26.21 版本对应的 AWS SDK 各模块。

## 小结

- **成效**：完成 AWS SDK BOM 从 2.26.20 到 2.26.21 的 patch 版本升级，使 AWS 集成依赖保持最新修复版本。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 一个文件，1 行改动。影响所有使用 AWS SDK 的模块（`iceberg-aws`、`iceberg-aws-bundle`、S3/Glue/DynamoDB 集成测试等）的构建产物依赖版本，但不改变 Iceberg 自身代码逻辑。
- **回迁到 1.4.x 的注意事项**：纯依赖升级，**适合回迁**，风险较低。AWS SDK 2.26.x 系列内 patch 升级通常向后兼容。回迁到 1.4.x 建议运行 `iceberg-aws` 模块的单元测试与（如有条件）S3/Glue 集成测试以验证兼容性。需注意 1.4.x 分支若已使用 `iceberg-aws-bundle` 的 shaded 形式，确认 bundle 重新打包后版本一致。
