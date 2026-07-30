# 提交 2345：Build: Bump nessie from 0.104.2 to 0.104.3 (#13541)

## 提交信息

- **序号**：2345 / 4088
- **哈希**：7c4793142d82091ba0fed50a676ee0672d5a5ef7
- **短哈希**：7c4793142
- **日期**：2025-07-14 09:19:48 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.104.2 to 0.104.3 (#13541)
- **PR/Issue**：#13541

## 总体目的

本提交由 Dependabot 自动生成，将 Nessie 依赖从 0.104.2 升级到 0.104.3。这是一个补丁版本（patch version）升级，包含 bug 修复和小的改进。

Nessie 是 Iceberg 支持的 catalog 实现之一，提供 Git-like 的数据版本控制能力。Iceberg 项目通过 Gradle 版本目录管理 Nessie 的多个依赖（nessie-client、nessie-jaxrs-testextension、nessie-versioned-storage-inmemory-tests、nessie-versioned-storage-testextension），它们共享同一个版本号。

此次升级从 0.104.2（在提交 2332 中引入）升级到 0.104.3，属于语义化版本中的补丁升级，通常包含 bug 修复而不引入破坏性变更。

## 如何达成设计目的

在 Gradle 版本目录中更新 Nessie 版本号即可，所有 Nessie 相关依赖会自动引用新版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Nessie 版本号。

**工作逻辑**：将 `nessie = "0.104.2"` 改为 `nessie = "0.104.3"`。版本目录中定义的 `nessie` 版本变量被所有 Nessie 相关依赖引用，一次修改即可同步升级所有 Nessie 组件。

## 总结

本提交是 Dependabot 自动生成的依赖升级，将 Nessie 从 0.104.2 升级到 0.104.3（补丁版本）。这是继提交 2332 升级到 0.104.2 后的后续小版本更新。
