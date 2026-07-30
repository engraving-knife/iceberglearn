# 提交 0867：Build: Bump software.amazon.awssdk:bom from 2.26.3 to 2.26.7 (#10554)

## 提交信息

- **序号**：0867 / 4088
- **哈希**：87d2fdd173db5813869435f2747920f2bdc8eda8
- **短哈希**：87d2fdd17
- **日期**：2024-06-24（Mon Jun 24 10:24:27 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.26.3 to 2.26.7 (#10554)
- **PR/Issue**：#10554

## 总体目的

Iceberg 的 `aws` 模块依赖 AWS SDK for Java v2（`software.amazon.awssdk`），通过 BOM（Bill of Materials）方式统一管理所有 AWS SDK 子模块的版本。该 BOM 在 2.26.3 之后陆续发布了 2.26.4/5/6/7 共 4 个 patch 版本。本提交由 dependabot 自动生成，目的是把 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本从 2.26.3 升级到 2.26.7，跟进 AWS SDK 的累积补丁修复（bug fix、安全修复、小幅 API 改进），保持依赖最新并避免已知问题。

AWS SDK 是 `aws` 模块（`S3FileIO`、`GlueCatalog`、`DynamoDbLockManager` 等）的运行时核心依赖，因此 BOM 版本升级会影响所有使用 AWS 集成的用户。但因为 semver patch 升级不含破坏性变更，理论上向后兼容。

## 如何达成设计目的

实现方式非常直接：修改 `gradle/libs.versions.toml` 中 `awssdk-bom` 的版本钉，从 `2.26.3` 改为 `2.26.7`。Gradle 通过 version catalog 引用此变量来统一管理 AWS SDK 各子模块版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 AWS SDK for Java v2 BOM 版本从 2.26.3 升级到 2.26.7。

**工作逻辑**：仅修改一行，diff 如下：

```diff
 avro = "1.11.3"
 assertj-core = "3.26.0"
 awaitility = "4.2.1"
-awssdk-bom = "2.26.3"
+awssdk-bom = "2.26.7"
 azuresdk-bom = "1.2.24"
 awssdk-s3accessgrants = "2.0.0"
 caffeine = "2.9.3"
```

`awssdk-bom` 是 Iceberg 通过 Gradle platform 方式引入的 BOM 坐标，所有 `software.amazon.awssdk:*` 子模块（s3、glue、dynamodb、sts、kms 等）的版本都由此 BOM 统一对齐。

## 小结

- **成效**：把 AWS SDK for Java v2 BOM 从 2.26.3 升级到 2.26.7，跟进 AWS SDK 累积 patch 修复（2.26.4 ~ 2.26.7 共 4 个 patch 版本），覆盖 `aws` 模块所有子模块依赖。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动。运行时影响：所有使用 `S3FileIO`、`GlueCatalog`、`DynamoDbLockManager`、`AssumeRoleAwsClientFactory` 等 AWS 集成功能的用户会切换到新的 AWS SDK 版本。
- **回迁到 1.4.x 的注意事项**：本提交是 AWS SDK patch 版本升级，**适合回迁**到 1.4.x 分支，特别是当 1.4.x 用户依赖 AWS 集成时，能受益于上游 bug/安全修复。注意事项：(1) AWS SDK patch 版本通常向后兼容，但 2.26.x 系列跨越 4 个 patch 仍可能存在小行为差异，回迁后建议运行 `aws` 模块集成测试（S3/Glue/DynamoDB 相关）；(2) 1.4.x 分支可能已独立钉了不同的 awssdk-bom 版本，回迁时按目标分支当前版本对照决定升级幅度；(3) 该升级不依赖其他提交，可独立 cherry-pick；(4) 如果 1.4.x 上同时回迁了 nessie 升级（提交 0867/0870），需注意 nessie 也可能传递依赖 awssdk，BOM 升级会传递覆盖 nessie 的传递依赖版本。
