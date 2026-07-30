# 提交 1262：Build: Bump software.amazon.awssdk:bom from 2.28.21 to 2.28.26 (#11359)

## 提交信息

- **序号**：1262 / 4088
- **哈希**：0640a38b6b030b0e1c3ef7bb3d92e4c64b621dc7
- **短哈希**：0640a38b6
- **日期**：2024-10-21（Mon Oct 21 09:07:34 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.28.21 to 2.28.26
- **PR/Issue**：#11359

## 总体目的

Dependabot 自动生成的依赖升级提交，把 AWS SDK for Java v2 的 BOM（Bill of Materials）从 `2.28.21` 升级到 `2.28.26`（semver-patch 补丁版本升级，跨 5 个 patch 版本）。

`software.amazon.awssdk:bom` 是 AWS SDK v2 的依赖版本管理 BOM，Iceberg 的 `aws` 模块（`S3FileIO`、`GlueCatalog`、`DynamoDbCatalog`、`STS`、`LakeFormation` 等）通过它统一管理所有 AWS SDK 子模块（`s3`、`glue`、`sts`、`dynamodb`、`lakeformation`、`s3-access-grants` 等）的版本。升级动机是跟进上游 2.28.22~2.28.26 的 bug 修复与改进，保持 AWS SDK 最新。

## 如何达成设计目的

Iceberg 的依赖版本统一集中在 `gradle/libs.versions.toml` 中维护，`awssdk-bom` 共享一个版本变量，只需把版本号从 `2.28.21` 改为 `2.28.26` 即可。BOM 机制会自动把所有 AWS SDK 子模块版本对齐到 BOM 指定的版本。属于 patch 版本升级，理论上不包含破坏性 API 变更。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

**修改目的**：升级 AWS SDK v2 BOM 版本。

**工作逻辑**：

```diff
-awssdk-bom = "2.28.21"
+awssdk-bom = "2.28.26"
```

该版本变量被 `awssdk-bom = { module = "software.amazon.awssdk:bom", version.ref = "awssdk-bom" }` 引用，所有通过 `platform(libs.awssdk.bom)` 引入 BOM 的模块（主要是 `aws` 模块）会同步升级其使用的所有 AWS SDK 子模块版本。

## 小结

- **成效**：把 AWS SDK v2 BOM 从 2.28.21 升级到 2.28.26，跟进上游 5 个 patch 版本的 bug 修复。仅修改 1 行 1 个文件，无源代码改动。
- **影响范围**：影响 `aws` 模块的所有 AWS SDK 子模块版本（S3、Glue、STS、DynamoDB、LakeFormation、S3 Access Grants 等），以及依赖 `aws` 模块的引擎集成（Flink/Spark 的 AWS 集成）。生产环境使用 AWS 服务的用户在升级 Iceberg 后会随之升级 AWS SDK 版本。
- **回迁到 1.4.x 的注意事项**：纯依赖版本升级，回迁零风险。1.4.x 分支的 `gradle/libs.versions.toml` 可直接 cherry-pick。需注意 1.4.x 上是否有针对特定 AWS SDK 版本的 workaround（如提交 1206 中的 `XMLStreamException`/503 令牌桶 workaround 是针对 SDK 通用问题的，与具体版本无关，应不受影响）。建议回迁后跑一次 `aws` 模块测试确认无回归。
