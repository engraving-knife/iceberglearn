# 提交 2075：Build: Bump nessie from 0.103.5 to 0.103.6

## 提交信息

- **序号**：2075 / 4088
- **哈希**：13aebe5d3120f1f05689b6142cc18a1155ee850a
- **短哈希**：13aebe5d3
- **日期**：2025-05-05 08:14:59 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.103.5 to 0.103.6 (#12963)
- **PR/Issue**：#12963

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Nessie 依赖从 0.103.5 升级到 0.103.6。这是一个 patch 版本升级，通常包含 Bug 修复和稳定性改进。本次升级涉及四个 Nessie 构件：`nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`，均从 0.103.5 升级到 0.103.6。

Nessie 是一个提供 Git 式版本化数据目录的服务，Iceberg 通过 NessieCatalog 集成 Nessie 作为目录服务。保持 Nessie 依赖为最新版本有助于获得最新的 Bug 修复。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `nessie` 版本号定义，统一升级所有 Nessie 构件版本。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：将 Nessie 版本号从 0.103.5 升级到 0.103.6。

**工作逻辑**：
将 `nessie = "0.103.5"` 修改为 `nessie = "0.103.6"`。该版本变量被 `nessie-client`、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension` 四个构件引用，一次修改完成全部升级。

## 总结

本提交由 Dependabot 自动生成，将 Nessie 依赖从 0.103.5 升级到 0.103.6（patch 版本升级），涉及四个 Nessie 构件，仅修改版本目录文件中一行版本号定义。
