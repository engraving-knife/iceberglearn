# 提交 1298：Bump Azurite to the latest version (#11411)

## 提交信息

- **序号**：1298 / 4088
- **哈希**：a6503f573c3890f691d605b01c20932f58dd9511
- **短哈希**：a6503f573
- **日期**：2024-10-28（Mon Oct 28 18:12:06 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Bump Azurite to the latest version (#11411)
- **PR/Issue**：#11411

## 总体目的

Iceberg 的 Azure 集成模块（`iceberg-azure`）在集成测试中使用 Azurite 作为本地 Azure Blob 存储模拟器。Azurite 是微软提供的开源本地开发工具，模拟 Azure Storage 的 Blob、Queue 和 Table 服务。本次提交将测试中使用的 Azurite Docker 镜像标签从 3.30.0 升级到 3.33.0，以获取上游 bug 修复和功能改进，保持测试环境与最新版本一致。

## 如何达成设计目的

修改 `AzuriteContainer` 类中的默认镜像标签常量，将 `DEFAULT_TAG` 从 `"3.30.0"` 改为 `"3.33.0"`。该类继承自 Testcontainers 的 `GenericContainer`，负责在测试中启动和管理 Azurite 容器实例。

## 修改详情

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/AzuriteContainer.java`

**修改目的**：将 Azurite 模拟器镜像版本从 3.30.0 升级到 3.33.0。

**工作逻辑**：在 `AzuriteContainer` 类中，将默认镜像标签常量修改：

```java
// 修改前
private static final String DEFAULT_TAG = "3.30.0";

// 修改后
private static final String DEFAULT_TAG = "3.33.0";
```

该常量与 `DEFAULT_IMAGE`（`"mcr.microsoft.com/azure-storage/azurite"`）组合，构成完整的 Docker 镜像引用 `mcr.microsoft.com/azure-storage/azurite:3.33.0`。Testcontainers 在测试启动时会拉取该镜像并启动容器，提供 Azure Blob 存储的模拟端点（默认端口 10000）。Azurite 3.33.0 相比 3.30.0 包含若干 bug 修复和兼容性改进，可能涉及对 Azure REST API 更准确的模拟。

该文件中还有 `LOG_WAIT_REGEX`（`"Azurite Blob service is successfully listening at .*"`）用于等待容器就绪，升级版本后该日志格式未变，因此等待逻辑无需调整。

## 小结

- **成效**：Azurite 模拟器升级到 3.33.0，获取上游 bug 修复，提升 Azure 模块集成测试的稳定性和准确性。
- **影响范围**：仅 `AzuriteContainer.java` 一个测试文件，1 行改动，无生产代码变更。
- **回迁到 1.4.x 的注意事项**：这是测试容器镜像版本升级，不影响运行时产物。1.4.x 分支若包含 `iceberg-azure` 模块的测试，可以考虑回迁以获取更稳定的测试环境。回迁风险很低，但需确认 1.4.x 的 Azure SDK 客户端与 Azurite 3.33.0 的 API 兼容性（一般 Azurite 保持向后兼容）。如果 1.4.x 当前测试正常，也可不回迁。
