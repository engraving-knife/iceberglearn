# 提交 0639：Build: Bump com.azure:azure-sdk-bom from 1.2.20 to 1.2.21

## 提交信息

- **序号**：0639 / 4088
- **哈希**：66a0954e4a77da72cb0cf398ca4a9b17208b939b
- **短哈希**：66a0954e4
- **日期**：2024-03-27（Wed Mar 27 17:40:26 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump com.azure:azure-sdk-bom from 1.2.20 to 1.2.21 (#9857)
- **PR/Issue**：#9857

## 总体目的

本提交由 GitHub Dependabot 自动生成，将 Azure SDK for Java 的 BOM（Bill of Materials）`com.azure:azure-sdk-bom` 从 1.2.20 升级到 1.2.21，是一次 `version-update:semver-patch` 级别的补丁升级，依赖类型为 `direct:production`。BOM 本身不提供任何类库，它只是一个 POM 清单，用来集中声明 Azure SDK 各个制品（如 `azure-storage-file-datalake`、`azure-security-keyvault-keys`、`azure-identity` 等）的统一版本，消费方通过 Gradle 的 `platform(...)` 把它作为版本对齐平台引入，从而让所有 Azure 制品版本由 BOM 统一锁定、彼此兼容。

Azure SDK BOM 在 Iceberg 中的角色是 `:iceberg-azure` 模块的版本对齐底座。`:iceberg-azure` 是 Iceberg 对接 Azure 存储的集成模块（支持 ADLS Gen2 / Key Vault / 托管身份等场景），通过 `compileOnly platform(libs.azuresdk.bom)` 引入 BOM，再以 `compileOnly` 形式按需引入 `azure-storage-file-datalake`、`azure-security-keyvault-keys`、`azure-identity` 三个制品（不在 BOM 中显式写版本号，版本由 BOM 提供）。`compileOnly` 表明这些 Azure 制品在编译期可见、不进入 Iceberg 发布 jar，由用户运行时环境提供，避免与用户应用自带的 Azure SDK 版本冲突。

本次升级的目的：跟进 Azure SDK BOM 1.2.21，让 `:iceberg-azure` 编译期所对齐的各 Azure 制品版本集体升至 BOM 1.2.21 所锁定的版本，获取 1.2.20 之后 Azure SDK 各制品的 bug 修复与兼容性改进，降低与新版 Azure 运行时的兼容性风险。

## 如何达成设计目的

Iceberg 采用 Gradle 版本目录集中管理依赖版本。Azure SDK BOM 的版本通过单个版本别名 `azuresdk-bom` 统一声明，再被 `azuresdk-bom` 库别名以 `version.ref = "azuresdk-bom"` 引用。因此本次升级只需在 `gradle/libs.versions.toml` 中修改一行：

```toml
- azuresdk-bom = "1.2.20"
+ azuresdk-bom = "1.2.21"
```

库别名被 `build.gradle` 的 `project(':iceberg-azure')` 依赖块以 `compileOnly platform(libs.azuresdk.bom)` 消费，作为版本平台导入。一处版本号变更即把 BOM 平台升到 1.2.21，进而带动该模块 `compileOnly` 引入的三个 Azure 制品版本由 BOM 1.2.21 统一解析。由于 BOM 是版本对齐清单而非构件本身，升级 BOM 不会改变 Iceberg 自身代码或发布产物的依赖结构，只改变编译期可见的 Azure 制品版本，风险面较小。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Azure SDK BOM 的锁定版本从 `1.2.20` 提升到 `1.2.21`。

**工作逻辑**：

改动位于版本声明区（第 32 行附近），原行 `azuresdk-bom = "1.2.20"` 被改为 `azuresdk-bom = "1.2.21"`，上下文如下：

```toml
avro = "1.11.3"
assertj-core = "3.25.3"
awaitility = "4.2.1"
awssdk-bom = "2.24.5"
azuresdk-bom = "1.2.21"   # 由 1.2.20 升级
awssdk-s3accessgrants = "2.0.0"
caffeine = "2.9.3"
calcite = "1.10.0"
```

该版本别名被库定义区库别名引用（约第 113 行）：

- `azuresdk-bom = { module = "com.azure:azure-sdk-bom", version.ref = "azuresdk-bom" }`

该库别名又被根 `build.gradle` 的 `project(':iceberg-azure')` 依赖块消费（约第 614 行）：

- `compileOnly platform(libs.azuresdk.bom)`——把 BOM 作为版本平台导入（compileOnly，不进发布产物）。
- `compileOnly "com.azure:azure-storage-file-datalake"`——ADLS Gen2 文件存储客户端，版本由 BOM 提供。
- `compileOnly "com.azure:azure-security-keyvault-keys"`——Key Vault 密钥管理客户端，版本由 BOM 提供。
- `compileOnly "com.azure:azure-identity"`——Azure 身份认证（托管身份、服务主体等），版本由 BOM 提供。

升级后，这三个制品的编译期版本由 BOM 1.2.21 统一锁定，CI 验证 `:iceberg-azure` 在 1.2.21 下编译并通过模块测试与集成测试。

## 小结

本提交是 Dependabot 触发的 Azure SDK BOM patch 升级：仅修改 `gradle/libs.versions.toml` 一行，把 `azuresdk-bom` 由 `1.2.20` 升至 `1.2.21`。Azure SDK BOM 是 `:iceberg-azure` 模块对接 Azure 存储（ADLS Gen2 / Key Vault / 身份认证）的版本对齐平台，以 `compileOnly platform(...)` 形式消费，不进入发布产物，仅控制编译期 Azure 制品版本。

- **影响范围**：仅依赖版本声明一处，无源码或测试代码改动；运行时影响为 `:iceberg-azure` 编译期所对齐的 Azure 制品版本集体升至 BOM 1.2.21 锁定值。对 Iceberg 公共 API 无影响。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支当前的 `azuresdk-bom` 版本为 `1.2.16`，比本提交的前置版本 `1.2.20` 低 4 个 patch。1.4.x 上 `:iceberg-azure` 模块存在，`azuresdk-bom` 库别名与 `compileOnly platform(...)` 消费方式一致，cherry-pick 上下文行 `azuresdk-bom = "1.2.20"` 在 1.4.x 上实际为 `1.2.16`，会产生小幅冲突但可手工解决（直接把 1.4.x 的 `1.2.16` 改为 `1.2.21`，跨越 1.2.17~1.2.21 共 5 个 patch）。BOM 升级仅影响版本对齐、不改依赖结构，回迁风险较低、可行性较高；建议回迁后回归 `:iceberg-azure` 的单元测试与（若启用）集成测试以确认 Azure 制品版本协同正常。
