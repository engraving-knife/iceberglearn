# 提交 1905：Build: Bump nessie from 0.103.0 to 0.103.2 (#12615)

## 提交信息

- **序号**：1905 / 4088
- **哈希**：c94fae4fac71f72d56de316595b26612bb04a515
- **短哈希**：c94fae4fa
- **日期**：2025-03-23 15:24:21 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.103.0 to 0.103.2 (#12615)
- **PR/Issue**：#12615

## 总体目的

这个提交是 Dependabot 自动生成的依赖升级，将 Iceberg 的 Nessie 依赖从 0.103.0 升级到 0.103.2。

Nessie 是一个提供 Git-like 版本控制的数据目录服务，Iceberg 通过 Nessie catalog 支持对其进行集成。本次升级涉及以下 Nessie 组件：

- `nessie-client`
- `nessie-jaxrs-testextension`
- `nessie-versioned-storage-inmemory-tests`
- `nessie-versioned-storage-testextension`

这是一个 patch 版本升级（0.103.0 → 0.103.2），通常包含 bug 修复和改进。

## 如何达成设计目的

通过修改 Gradle 版本目录文件中的 Nessie 版本号来完成升级。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 Nessie 依赖版本。

**工作逻辑**：将 `nessie = "0.103.0"` 改为 `nessie = "0.103.2"`。所有引用此版本号的 Nessie 组件（client、test extension 等）都会自动使用新版本。

## 总结

本提交将 Nessie 依赖从 0.103.0 升级到 0.103.2，涉及 client 和测试扩展等 4 个组件。这是一个常规的 patch 版本依赖升级。
