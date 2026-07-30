# 提交 0985：Build: Bump software.amazon.awssdk:bom from 2.26.21 to 2.26.25 (#10800)

## 提交信息

- **序号**：0985 / 4088
- **哈希**：265bce76f7a8cac19b231aa8b559f95d392ecdb2
- **短哈希**：265bce76f
- **日期**：2024-07-29 09:10:42 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.26.21 to 2.26.25 (#10800)
- **PR/Issue**：#10800

## 总体目的

Iceberg 在多个模块（S3FileIO、GlueCatalog、DynamoDB 锁等）中依赖 AWS SDK for Java v2。AWS SDK 通过 BOM（`software.amazon.awssdk:bom`）统一管理其子模块的版本号。该提交把 BOM 版本从 2.26.21 升到 2.26.25，覆盖 4 个 patch 版本，目的是跟随上游修复（bugfix、安全补丁、依赖更新）以保持依赖新鲜并解决潜在问题。

这种 patch 级别升级属于 Dependabot 自动化维护流程的常规操作，主要由 Dependabot 在 GitHub 上检测到新版本后自动生成 PR 并合入。

## 如何达成设计目的

Iceberg 使用 Gradle version catalog（`gradle/libs.versions.toml`）统一管理所有依赖版本。`awssdk-bom` 在 catalog 中被声明为一个版本别名，所有引用 `software.amazon.awssdk:*` 的子模块在 `build.gradle` 中通过 `platform(libs.awssdk.bom)` 引入 BOM 来获得版本。因此升级 BOM 只需要在 catalog 中改一个版本号字符串，所有 AWS SDK 子模块（s3、sts、glue、dynamodb、kms 等）会自动跟随。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 `awssdk-bom` 的版本号从 2.26.21 升级到 2.26.25。

**工作逻辑**：仅修改一行：

```diff
-awssdk-bom = "2.26.21"
+awssdk-bom = "2.26.25"
```

由于该版本号通过 version catalog 集中管理，所有引用 BOM 的子模块（iceberg-aws、iceberg-aws-bundle、iceberg-glue、iceberg-dell 等）都会自动应用新版本，无需逐个修改 build.gradle。

## 小结

- **成效**：完成 AWS SDK BOM 的一次 patch 级别升级（4 个 patch 版本），获取上游 bugfix 与潜在安全补丁。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行；间接影响所有使用 AWS SDK 的模块（运行时与测试）。
- **回迁到 1.4.x 的注意事项**：纯依赖升级，回迁风险低，但 1.4.x 是否需要此升级取决于其上已有版本与是否有具体问题需要修复。建议按需回迁，且回迁时确认 1.4.x 上 `awssdk-bom` 仍位于 catalog 同一 key；若 1.4.x 上版本差距较大，建议先在 PR 中跑完整 CI（特别是 AWS 集成测试）再合入。
