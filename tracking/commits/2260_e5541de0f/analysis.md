# 提交 2260：Azure: Bump AzuriteContainer to 3.34.0 (#13321)

## 提交信息

- **序号**：2260 / 4088
- **哈希**：e5541de0fd1e850f188ff89cac1417b3ae3500a4
- **短哈希**：e5541de0f
- **日期**：2025-06-19 21:02:11 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Azure: Bump AzuriteContainer to 3.34.0
- **PR/Issue**：#13321

## 总体目的

本提交将 Azure 集成测试中使用的 AzuriteContainer（Azure 存储模拟器）从版本 3.33.0 升级到 3.34.0。Azurite 是 Microsoft 提供的本地 Azure 存储模拟器，Iceberg 的 Azure ADLS Gen2 模块在集成测试中使用它来模拟 Azure Blob 存储服务，无需实际 Azure 云资源。

依赖版本升级是项目维护的常规操作，确保测试环境使用最新的模拟器版本，以获得最新的 bug 修复和功能改进，同时保持与生产环境 Azure 存储服务的行为一致性。

## 如何达成设计目的

- 修改 `AzuriteContainer` 类中的 `DEFAULT_TAG` 常量从 `"3.33.0"` 改为 `"3.34.0"`。

## 修改详情

### `azure/src/integration/java/org/apache/iceberg/azure/adlsv2/AzuriteContainer.java` (修改, +1/-1 lines)

**修改目的**：升级 Azurite 模拟器版本。

**工作逻辑**：将 `DEFAULT_TAG` 常量从 `"3.33.0"` 修改为 `"3.34.0"`。该常量与 `DEFAULT_IMAGE`（`"mcr.microsoft.com/azure-storage/azurite"`）组合形成 Docker 镜像标签，Testcontainers 框架在启动集成测试容器时使用此镜像标签拉取对应版本的 Azurite 镜像。

## 总结

本提交是一个简单的依赖版本升级，将 Azure 集成测试中使用的 Azurite 存储模拟器从 3.33.0 升级到 3.34.0。仅修改 1 行代码，属于常规的项目维护工作，确保测试环境与最新的 Azurite 版本保持同步。
