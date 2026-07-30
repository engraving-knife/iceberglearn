# 提交 1234：Build: Bump software.amazon.awssdk:bom from 2.28.16 to 2.28.21 (#11311)

## 提交信息

- **序号**：1234 / 4088
- **哈希**：c55a078fc17580b9bd91ebe3fb1e43bc2def04b3
- **短哈希**：c55a078fc
- **日期**：2024-10-14（Mon Oct 14 12:46:54 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.28.16 to 2.28.21 (#11311)
- **PR/Issue**：#11311

## 总体目的

这是 Dependabot 自动生成的依赖版本升级 PR。`software.amazon.awssdk:bom` 是 AWS SDK for Java v2 的 BOM（Bill of Materials），用于统一管理 Iceberg `aws` 模块及其相关依赖中所有 AWS SDK 子模块（如 s3、sts、glue、dynamodb、kms 等）的版本。本次把 BOM 版本从 `2.28.16` 升级到 `2.28.21`，跨 5 个 patch 版本，目的是获得 AWS SDK 在该窗口期内的 bug 修复与小幅改进，保持依赖处于较新且安全的版本。

Dependabot 元信息标注 `update-type: version-update:semver-patch`，即仅是 patch 级别升级，按照语义化版本约定应保持向后兼容。

## 如何达成设计目的

只修改 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本字符串。Gradle 在解析依赖时通过 BOM 引入的版本号会随之传递给所有 AWS SDK 子模块的传递依赖，无需逐个修改各子模块的版本号。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：

```diff
-awssdk-bom = "2.28.16"
+awssdk-bom = "2.28.21"
```

`libs.versions.toml` 是 Gradle 版本目录（Version Catalog）文件，仓库内所有模块的依赖版本集中在此声明。`awssdk-bom` 这一项通过 `platform("software.amazon.awssdk:bom:${Versions.awssdkBom}")` 等方式被引用，作为整个仓库 AWS SDK 系列依赖版本的唯一来源。修改后，所有依赖 AWS SDK 子模块（如 `software.amazon.awssdk:s3`、`software.amazon.awssdk:sts`、`software.amazon.awssdk:glue` 等）的版本会自动跟随 BOM 升到 2.28.21 对应的版本。

## 小结

- **成效**：AWS SDK BOM 从 2.28.16 升级到 2.28.21，引入该区间内的 patch 修复，无 API 变更。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件、一行版本号变更；无代码逻辑改动。影响范围理论上覆盖所有依赖 AWS SDK 的模块（`aws`、`aws-bundle`、以及任何使用 S3FileIO / GlueCatalog / DynamoDB 的代码路径），但 patch 升级预期兼容。
- **回迁到 1.4.x 的注意事项**：1.4.x 中 AWS SDK 版本可能更老（例如 1.4.x 维护分支早期可能停留在 2.24.x 或更早）。是否回迁需考虑：
  - 直接跳跃到 2.28.21 跨度较大，需评估 1.4.x 已使用的 AWS SDK API 是否在该窗口内有过 deprecation/行为变化；
  - 建议先确认 1.4.x 当前 BOM 版本，再决定是直接套用 2.28.21 还是只升到中间版本；
  - 升级后需运行 `aws` 模块测试套件，特别是 S3FileIO、GlueCatalog、DynamoDB 相关集成测试。
