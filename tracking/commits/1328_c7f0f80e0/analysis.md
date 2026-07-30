# 提交 1328：Build: Bump com.azure:azure-sdk-bom from 1.2.28 to 1.2.29 (#11453)

## 提交信息

- **序号**：1328 / 4088
- **哈希**：c7f0f80e030866b4bb6795a20c50d9c1c2ed726b
- **短哈希**：c7f0f80e0
- **日期**：2024-11-04（Mon Nov 4 11:39:56 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.28 to 1.2.29 (#11453)
- **PR/Issue**：#11453

## 总体目的

由 Dependabot 自动发起的依赖版本升级：将 Azure SDK for Java BOM（`com.azure:azure-sdk-bom`）从 `1.2.28` 升级到 `1.2.29`，属 patch 升级。Iceberg 在 Azure 集成（`azure` 模块、`azure-bundle`）中使用 Azure SDK 访问 Azure Blob Storage / ADLS Gen2，通过该 BOM 统一管理 Azure SDK 各客户端库版本。升级目的是获取 1.2.29 中 bug 修复与改进。

Dependabot 标注 `update-type: version-update:semver-patch`，属低风险升级。

## 如何达成设计目的

只修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `azuresdk-bom` 这一个版本键的值。所有通过 `com.azure:azure-sdk-bom` 导入的 Azure SDK 子模块（如 `azure-storage-blob`、`azure-storage-file-datalake`、`azure-identity` 等）会自动解析到 1.2.29 对齐的版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Azure SDK BOM 版本号。

**工作逻辑**：将第 33 行附近的版本声明由

```toml
azuresdk-bom = "1.2.28"
```

改为

```toml
azuresdk-bom = "1.2.29"
```

其他版本键（如 `awssdk-bom`、`awssdk-s3accessgrants`）保持不变。BOM 升级后，所有引用 `azuresdk-bom` 的模块在依赖解析时使用 1.2.29 版本对应的 Azure SDK 子模块版本。

## 小结

- **成效**：Azure SDK for Java BOM 升级至 1.2.29，获取 patch 修复。属依赖维护性升级，主要影响 Azure 集成模块的依赖解析。
- **影响范围**：仅 1 个文件、1 行版本号变更。运行时影响取决于 1.2.28→1.2.29 之间 Azure SDK 子模块（尤其是 `azure-storage-blob` 与 `azure-storage-file-datalake`）的具体改动；patch 升级理论上向后兼容。
- **回迁到 1.4.x 的注意事项**：**视情况可选回迁**。1.4.x 同样使用 `libs.versions.toml` 管理 Azure SDK BOM。patch 级升级风险低，若 1.4.x 当前 Azure SDK BOM 版本为 1.2.28 或更低且回迁能获取相关修复，则可回迁。回迁前应确认 1.4.x 当前版本是否已高于 1.2.29，并验证 Azure 集成测试通过。若 1.4.x Azure 集成稳定且无相关 bug，可不必回迁。
