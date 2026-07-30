# 提交 0800：Bump Azurite test-container to 3.30.0

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 0800 |
| 完整哈希 | 23eb5941cd599183be63676c9d1379ba102b1a97 |
| 短哈希 | 23eb5941c |
| 日期 | 2024-06-02 21:44:00 +0200 |
| 作者 | Fokko Driesprong |
| 提交说明 | Bump Azurite test-container to `3.30.0` |
| PR/Issue | 无（直接提交，无 PR 号） |

## 总体目的

本提交将 Azure ADLSv2 模块测试中使用的 Azurite 测试容器镜像版本从 `3.29.0` 升级到 `3.30.0`。

Azurite 是微软提供的本地 Azure 存储模拟器，Iceberg 的 `azure` 模块在集成测试中通过 Testcontainers 拉起一个 Azurite 容器来模拟 Azure Data Lake Storage Gen2（ADLSv2），从而在没有真实 Azure 云资源的情况下验证 Iceberg 对 ADLSv2 的读写、文件列表、删除等操作的正确性。定期升级 Azurite 版本可以：

1. 跟随上游 Azurite 的 bug 修复和行为更新，避免因模拟器旧版本缺陷导致的测试假阳性/假阴性。
2. 与较新版本的 Azure Storage SDK 行为对齐（Iceberg 使用的 `azure-storage-file-datalake` 等 SDK 会持续演进，模拟器也需要相应跟进）。
3. 减少安全扫描工具对过时镜像的告警。

## 如何达成设计目的

实现方式极为简洁：在 `AzuriteContainer.java` 中将 `DEFAULT_TAG` 常量从 `"3.29.0"` 改为 `"3.30.0"`。

`AzuriteContainer` 类的设计逻辑：
- `DEFAULT_IMAGE = "mcr.microsoft.com/azure-storage/azurite"` 指定 Azurite 官方镜像仓库。
- `DEFAULT_TAG` 指定镜像版本标签。
- 构造函数 `AzuriteContainer()` 调用 `this(DEFAULT_IMAGE + ":" + DEFAULT_TAG)`，即默认使用 `mcr.microsoft.com/azure-storage/azurite:3.30.0`。
- 另一个构造函数 `AzuriteContainer(String image)` 允许外部传入完整镜像名（为 null 时回退到默认镜像+标签），便于在需要时锁定特定镜像或使用私有镜像。
- 容器启动时暴露 10000 端口（Azurite Blob service 默认端口），设置 `AZURITE_ACCOUNTS` 环境变量注入测试账号 `account:key`，并通过 `LogMessageWaitStrategy` 等待日志中出现 "Azurite Blob service is successfully listening at" 正则匹配的行，确保服务就绪后再执行测试。

由于版本号以常量形式集中管理，升级只需修改一处常量，所有依赖 `AzuriteContainer` 默认构造的测试都会自动使用新版本。

## 修改详情

### `azure/src/test/java/org/apache/iceberg/azure/adlsv2/AzuriteContainer.java`（修改）

- 将常量 `DEFAULT_TAG` 的值由 `"3.29.0"` 改为 `"3.30.0"`：
  ```java
  // 修改前
  private static final String DEFAULT_TAG = "3.29.0";
  // 修改后
  private static final String DEFAULT_TAG = "3.30.0";
  ```

仅此一处改动，文件其余部分（端口、镜像、等待策略、账号配置、文件系统操作方法等）保持不变。

## 小结

- **成效**：将 Azure 模块集成测试使用的 Azurite 模拟器从 3.29.0 升级到 3.30.0，跟进上游版本，保持测试环境与 Azure Storage SDK 行为一致。
- **影响范围**：仅影响 `azure` 模块的测试容器镜像版本，不涉及任何生产代码、API 或运行时行为。属于纯测试依赖升级。
- **回迁到 1.4.x 分支的注意事项**：
  - 这是一个低风险的测试基础设施升级，回迁价值不大（除非 1.4.x 的 Azurite 3.29.0 已出现已知 bug 影响测试稳定性）。
  - 若 1.4.x 分支的 `azure` 模块测试偶发失败且与 Azurite 行为相关，可考虑回迁以借助新版本模拟器的修复。
  - 回迁时需确认 1.4.x 分支的 `AzuriteContainer.java` 中 `DEFAULT_TAG` 仍为 `3.29.0`（即未被其他改动覆盖），直接替换即可。
  - 需确认 CI 环境能够拉取 `mcr.microsoft.com/azure-storage/azurite:3.30.0` 镜像（网络可达且镜像已发布）。
