# 提交 3810：Build: Bump com.azure:azure-sdk-bom from 1.3.6 to 1.3.7 (#16637)

## 提交信息

- **序号**：3810 / 4088
- **哈希**：67cbc1aeabbce52d618f8e3c6fc34747f6695a8d
- **短哈希**：67cbc1aea
- **日期**：2026-05-31 18:03:55 -0700
- **作者**：Yuya Ebihara <ebyhry@gmail.com>
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.3.6 to 1.3.7 (#16637)
- **PR/Issue**：#16637

## 总体目的

本提交将 Iceberg 项目依赖的 Azure SDK for Java BOM（`com.azure:azure-sdk-bom`）从 `1.3.6` 升级到 `1.3.7`，并同步更新 `azure-bundle` 与 `kafka-connect-runtime` 模块中显式锁定的 Azure 子模块版本。Azure SDK BOM 统一管理 Azure 各客户端库（如 `azure-core`、`azure-storage-blob`、`azure-storage-file-datalake` 等）的版本，Iceberg 的 Azure 集成（ADLS catalog、Azure 存储后端）依赖它来访问 Azure 服务。这是一个 patch 级升级（1.3.6 → 1.3.7），属于常规依赖维护。

值得注意的是，本提交由人工提交者（Yuya Ebihara）而非 Dependabot 完成，原因在于除了升级 BOM 版本号外，还需要手动同步更新两个 `runtime-deps.txt` 文件中显式列出的 Azure 子模块版本（这些文件用于生成最终发行包的依赖清单，需要精确锁定每个子模块版本）。Dependabot 通常只升级 BOM 入口，无法自动处理这些次级清单文件。

## 如何达成设计目的

升级分两层进行：第一层是修改 `gradle/libs.versions.toml` 中的 `azuresdk-bom` 版本号；第二层是更新 `azure-bundle/runtime-deps.txt` 与 `kafka-connect/kafka-connect-runtime/runtime-deps.txt` 中显式锁定的 Azure 子模块版本，使其与 BOM 1.3.7 对应的版本一致。这两个 `runtime-deps.txt` 文件用于在打包时生成可重现的依赖清单，确保发行包内的 Azure 子模块版本与 BOM 声明一致。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Azure SDK BOM 版本号。

**工作逻辑**：
将版本目录中的 BOM 版本从 `1.3.6` 改为 `1.3.7`：
```toml
-azuresdk-bom = "1.3.6"
+azuresdk-bom = "1.3.7"
```

### `azure-bundle/runtime-deps.txt` (+5/-5 lines)

**修改目的**：同步 `azure-bundle` 模块发行包中显式锁定的 Azure 子模块版本。

**工作逻辑**：
更新以下子模块版本：
- `com.azure:azure-core`: 1.57 → 1.58
- `com.azure:azure-storage-blob`: 12.33 → 12.34
- `com.azure:azure-storage-common`: 12.32 → 12.33
- `com.azure:azure-storage-file-datalake`: 12.26 → 12.27
- `com.azure:azure-storage-internal-avro`: 12.18 → 12.19

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+5/-5 lines)

**修改目的**：同步 `kafka-connect-runtime` 模块发行包中显式锁定的 Azure 子模块版本。

**工作逻辑**：
与 `azure-bundle` 完全相同的 5 个子模块版本升级，保持两个发行包的 Azure 依赖版本一致。

## 总结

这是一次 Azure SDK 的 patch 级依赖升级，由人工提交者完成。除升级 BOM 版本号外，还同步更新了两个发行包的显式依赖清单，确保发行包内的 Azure 子模块版本与 BOM 一致。及时升级 Azure SDK 有助于获取 bug 修复与安全补丁，保持与 Azure 服务的兼容性。本提交也体现了 Iceberg 对发行包依赖可重现性的重视。
