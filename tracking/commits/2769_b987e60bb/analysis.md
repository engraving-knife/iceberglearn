# 提交 2769：Build: Bump nessie from 0.105.4 to 0.105.5 (#14370)

## 提交信息

- **序号**：2769 / 4088
- **哈希**：b987e60bbd581d6e9e583107d5a85022261ff0d8
- **短哈希**：b987e60bb
- **日期**：2025-10-19 07:26:02 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.105.4 to 0.105.5 (#14370)
- **PR/Issue**：#14370

## 总体目的

本提交由 dependabot 自动生成，将 Projectnessie（Nessie）依赖从 0.105.4 升级到 0.105.5（补丁版本升级）。

背景在于：Nessie 是一个提供 Git 风格版本化数据目录（catalog）的服务，Iceberg 支持 Nessie 作为 catalog 实现（`nessie` 模块）。Iceberg 在依赖中引入了多个 Nessie 模块：`nessie-client`（客户端，用于生产代码连接 Nessie 服务）、`nessie-jaxrs-testextension`、`nessie-versioned-storage-inmemory-tests`、`nessie-versioned-storage-testextension`（后三者用于测试，提供嵌入式 Nessie 服务）。这些模块通过统一的 `nessie` 版本引用管理。dependabot 定期检查 Nessie 版本更新。本次是 0.105.x 系列内的补丁升级（0.105.4 → 0.105.5），属于 `version-update:semver-patch`，通常包含 bug 修复和改进。

本次升级同时更新了上述 4 个 Nessie 模块的版本（它们共享 `nessie` 版本引用）。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中将 `nessie` 版本引用从 `0.105.4` 改为 `0.105.5`。所有引用该版本的 Nessie 模块（nessie-client、nessie-jaxrs-testextension、nessie-versioned-storage-inmemory-tests、nessie-versioned-storage-testextension）将统一升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Nessie 版本。

**工作逻辑**：将 `nessie = "0.105.4"` 改为 `nessie = "0.105.5"`。引用 `nessie` 版本的 4 个模块坐标（nessie-client、nessie-jaxrs-testextension、nessie-versioned-storage-inmemory-tests、nessie-versioned-storage-testextension）随之升级到 0.105.5。

## 总结

本提交是 dependabot 自动发起的 Nessie 补丁升级（0.105.4 → 0.105.5），同时更新 4 个 Nessie 模块（1 个生产客户端 + 3 个测试扩展）。作为 semver-patch 级别升级，预期包含 bug 修复和改进。对 Iceberg 的 Nessie catalog 集成无破坏性影响，风险较低。建议升级后运行 Nessie 相关集成测试验证。
