# 提交 0035：Build: Bump nessie from 0.71.0 to 0.71.1 (#8771)

## 提交信息

- **序号**：0035 / 4088
- **哈希**：dbaa92e0ba1ded6a55888de374277e211e0082fa
- **短哈希**：dbaa92e0b
- **日期**：2023-10-11 09:08:04 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.71.0 to 0.71.1 (#8771)
- **PR/Issue**：#8771

## 总体目的

这个提交由 dependabot 自动生成，把 Iceberg 依赖的 Projectnessie（Nessie）相关组件从 0.71.0 升级到 0.71.1，更新类型为 `version-update:semver-patch`。

Nessie 是 Iceberg 生态中重要的"版本化 catalog"实现，Iceberg 提供 `NessieCatalog` 用于把表元数据的版本管理与 Nessie 的 Git 风格分支/提交模型集成。本次升级涉及 4 个 Nessie 坐标，分别对应不同的使用场景：

- `org.projectnessie.nessie:nessie-client`：生产依赖，`NessieCatalog` 通过它与 Nessie 服务通信（获取/提交引用、读取表元数据）。
- `org.projectnessie.nessie:nessie-jaxrs-testextension`：测试依赖，用于在 JUnit 中以嵌入式方式启动 Nessie REST 服务进行集成测试。
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory`：测试依赖，提供内存版版本化存储后端，被测试 extension 使用。
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`：测试依赖，提供版本化存储的测试扩展（供 storage backend 测试用）。

0.71.0 → 0.71.1 是 Nessie 0.71.x 系列内的 patch 版本，通常包含 bugfix、稳定性改进以及对 client API 的小幅修复。对于 Iceberg 而言，升级能确保 `NessieCatalog` 在与 0.71.1 Nessie 服务对接时的兼容性，并让集成测试在较新的 Nessie 测试扩展下运行，减少误报。由于是 patch 版本，保持 API 兼容，Iceberg 源码无需任何改动。

## 如何达成设计目的

通过单行版本号变更完成升级。所有 4 个 Nessie 坐标都通过 `version.ref = "nessie"` 引用同一个版本变量，因此只需在 [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml) 中把 `nessie` 这一个版本号从 `0.71.0` 改为 `0.71.1`，4 个坐标（client、jaxrs-testextension、versioned-storage-inmemory、versioned-storage-testextension）即同步升级。

## 修改详情

### [gradle/libs.versions.toml](file:///Users/fengxiaohang/trae/iceberglearn/gradle/libs.versions.toml)

**修改目的**：把 Projectnessie 版本号从 0.71.0 升级到 0.71.1，同步升级 client 与 3 个测试扩展。

**工作逻辑**：在 `[versions]` 段把 `nessie = "0.71.0"` 改为 `nessie = "0.71.1"`。该版本号被 `[libraries]` 段的 4 个坐标引用：
- `nessie-client = { module = "org.projectnessie.nessie:nessie-client", version.ref = "nessie" }`（生产）
- `nessie-jaxrs-testextension = { module = "org.projectnessie.nessie:nessie-jaxrs-testextension", version.ref = "nessie" }`（测试）
- `nessie-versioned-storage-inmemory = { module = "org.projectnessie.nessie:nessie-versioned-storage-inmemory", version.ref = "nessie" }`（测试）
- `nessie-versioned-storage-testextension = { module = "org.projectnessie.nessie:nessie-versioned-storage-testextension", version.ref = "nessie" }`（测试）

改一行即让上述 4 个模块同步升级到 0.71.1。无其他源码或配置改动。

## 小结

本提交是 dependabot 自动把 Projectnessie 从 0.71.0 升级到 0.71.1 的单行依赖升级，同步覆盖 `NessieCatalog` 生产用的 client 与 3 个测试用扩展，属低风险 patch 版本升级，确保 Iceberg 与 Nessie 0.71.x 服务的对接兼容性与集成测试稳定性。
