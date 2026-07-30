# 提交 3222：Build: Bump io.netty:netty-buffer from 4.2.9.Final to 4.2.10.Final (#15260)

## 提交信息

- **序号**：3222 / 4088
- **哈希**：af0e7568c49d5fdc4cffdf6fda5b9f3fa2e4c8a9
- **短哈希**：af0e7568c
- **日期**：2026-02-08
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.2.9.Final to 4.2.10.Final (#15260)
- **PR/Issue**：#15260

## 总体目的

这是一次 dependabot 发起的依赖版本升级，针对 Netty 的缓冲区制品 `io.netty:netty-buffer`。`netty-buffer` 提供 `ByteBuf` 及相关缓冲区分配/管理能力，是 Netty 网络栈的基础组件，常被 gRPC、AWS SDK（HTTP 客户端）、S3/FileIO 相关的异步传输链路作为传递依赖引入。Iceberg 在 `gradle/libs.versions.toml` 中以 `netty-buffer` 变量显式声明其版本以便统一对齐，同时在根 `build.gradle` 中存在多处 `exclude group: 'io.netty', module: 'netty-buffer'` 配置，用以在某些模块（避免版本冲突的场景）中排除默认传递进来的 netty-buffer，再按需统一引入受控版本。

本次把 `netty-buffer` 从 `4.2.9.Final` 提升到 `4.2.10.Final`，属于 `semver-patch`（修订号）升级。Netty 4.2.x 线上的 patch 升级通常包含缺陷修复与安全性改进，保持 API 兼容。升级动机是跟进上游修复（含潜在安全补丁）并保持依赖新鲜。

## 如何达成设计目的

作为 dependabot 自动化升级，整体思路是在集中式版本目录 `gradle/libs.versions.toml` 中把 `netty-buffer` 版本变量从 `4.2.9.Final` 改为 `4.2.10.Final`。该变量通过 `version.ref` 被对应制品引用，单点修改即生效。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将统一管理的 `netty-buffer` 版本变量从 4.2.9.Final 升到 4.2.10.Final。

**工作逻辑**：
在 `[versions]` 段中，将 `netty-buffer = "4.2.9.Final"` 修改为 `netty-buffer = "4.2.10.Final"`。该变量被 `[libraries]` 段中 `netty-buffer = { module = "io.netty:netty-buffer", version.ref = "netty-buffer" }` 引用，同时与 `netty-buffer-compat`（兼容版本）等邻近变量并列。修订号升级预期保持 `ByteBuf` API 二进制兼容，对 Iceberg 通过传递依赖使用 Netty 缓冲区的链路（如 gRPC、AWS SDK 异步 HTTP）应为透明或仅带来修复收益。根 `build.gradle` 中针对 `io.netty:netty-buffer` 的若干 `exclude` 配置本次未改动，仅升级受控版本号本身。

## 总结

本提交由 dependabot 将集中式版本目录中的 `io.netty:netty-buffer` 版本变量从 4.2.9.Final 升级到 4.2.10.Final（修订号升级，`ByteBuf` API 兼容），用于跟进上游修复与安全改进并保持依赖新鲜；改动为单点版本号替换，不涉及代码或 exclude 配置逻辑。
