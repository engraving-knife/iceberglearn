# 提交 1993：Build: Bump nessie from 0.103.2 to 0.103.3

## 提交信息

- **序号**：1993 / 4088
- **哈希**：41237e0214e1f75d12e652a9258a8b4c29a7f072
- **短哈希**：41237e021
- **日期**：2025-04-14 15:04:14 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.103.2 to 0.103.3 (#12786)
- **PR/Issue**：#12786

## 总体目的

本提交由 Dependabot 自动生成，将 Nessie 依赖从 0.103.2 升级到 0.103.3。Nessie 是一个提供 Git 式版本化数据目录（versioned catalog）的服务，Iceberg 通过 Nessie 客户端与之集成。这是一次补丁版本（patch）升级，属于常规的依赖维护工作。

本次升级影响以下四个 Nessie 制品：
- `org.projectnessie.nessie:nessie-client`
- `org.projectnessie.nessie:nessie-jaxrs-testextension`
- `org.projectnessie.nessie:nessie-versioned-storage-inmemory-tests`
- `org.projectnessie.nessie:nessie-versioned-storage-testextension`

保持 Nessie 依赖的最新补丁版本有助于获取 bug 修复和稳定性改进，确保 Iceberg 与 Nessie 目录的集成测试基于最新的 Nessie 版本运行。

## 如何达成设计目的

通过修改 Gradle 版本目录文件中 nessie 的版本号声明来完成升级。Iceberg 使用 `gradle/libs.versions.toml` 集中管理依赖版本，修改一处即可同步更新所有引用该版本的 Nessie 制品。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 nessie 版本号。

**工作逻辑**：
将版本目录中 nessie 的版本声明从 `nessie = "0.103.2"` 改为 `nessie = "0.103.3"`。该声明被多个 Nessie 制品（nessie-client、nessie-jaxrs-testextension、nessie-versioned-storage-inmemory-tests、nessie-versioned-storage-testextension）共同引用，升级后所有制品同步更新到 0.103.3。

## 总结

本提交是 Dependabot 自动发起的 Nessie 依赖补丁版本升级（0.103.2 → 0.103.3），仅修改版本目录一处声明，影响客户端和测试扩展等多个制品，属于常规依赖维护。
