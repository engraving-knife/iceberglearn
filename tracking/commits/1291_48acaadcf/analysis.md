# 提交 1291：Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#11405)

## 提交信息

- **序号**：1291 / 4088
- **哈希**：48acaadcf94f43572f11b7b589d1fb3857fc6b9d
- **短哈希**：48acaadcf
- **日期**：2024-10-28（Mon Oct 28 12:03:10 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin (#11405)
- **PR/Issue**：#11405

## 总体目的

AWS S3 Access Grants 是 AWS 提供的 S3 访问授权机制，`aws-s3-accessgrants-java-plugin` 是其 Java 插件，可作为 AWS SDK v2 的 `S3Plugin` 注入到 `S3Client`，使 Iceberg 的 `iceberg-aws` 在访问 S3 时通过 Access Grants 获取数据访问凭证。该插件版本在 `gradle/libs.versions.toml` 中以 `awssdk-s3accessgrants` 声明。dependabot 检测到插件从 2.2.0 升级到 2.3.0（minor 发布），本提交把版本号对齐，使 Access Grants 插件与同批升级的 AWS SDK BOM 2.29.1（见 #11400）版本配套，获得上游修复。

## 如何达成设计目的

在版本目录把 `awssdk-s3accessgrants = "2.2.0"` 改为 `awssdk-s3accessgrants = "2.3.0"`。引用 `libs.awssdk.s3accessgrants` 的依赖随之整体升级。这是 dependabot 自动生成的单行 minor 升级，无代码逻辑变更。本提交与 #11400（`awssdk-bom` 2.28.26 → 2.29.1）配套，使 Access Grants 插件与 SDK 主版本对齐。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 AWS S3 Access Grants 插件从 2.2.0 升到 2.3.0。

**工作逻辑**：

```toml
-awssdk-s3accessgrants = "2.2.0"
+awssdk-s3accessgrants = "2.3.0"
```

此时相邻的 `awssdk-bom = "2.29.1"`（已由 #11400 升级），二者配套。`azuresdk-bom`、`caffeine` 等不受影响。

## 小结

- **成效**：AWS S3 Access Grants 插件升级到 2.3.0（minor），与 AWS SDK BOM 2.29.1 版本配套，使 `iceberg-aws` 的 Access Grants 凭证获取路径获得上游修复。
- **影响范围**：改动 1 个文件、1 行，仅构建依赖版本变更，无源代码变更。影响 `aws` 模块中启用 S3 Access Grants 的产物与测试 classpath。
- **回迁到 1.4.x 的注意事项**：
  - **谨慎回迁**：Access Grants 插件是 `iceberg-aws` 的**产物依赖**（当用户启用 S3 Access Grants 时生效），minor 升级可能引入行为变化。回迁前应确认 1.4.x 在 2.3.0 下无回归——重点跑启用 Access Grants 的 S3FileIO 测试（若 1.4.x 有相应集成测试）。
  - **配套性**：建议与 #11400（`awssdk-bom` 2.28.26 → 2.29.1）配套回迁，保持插件与 SDK 主版本对应；二者在 1.4.x 上需独立验证兼容性。
  - **风险**：中（产物依赖 minor 升级，但仅影响启用 Access Grants 的用户路径）。若 1.4.x 不重点维护 Access Grants 场景，可不强求回迁。
