# 提交 3039：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#14900)

## 提交信息

- **序号**：3039 / 4088
- **哈希**：d6d44a7803b218e7da6d514e96ad98282f22a2b6
- **短哈希**：d6d44a780
- **日期**：2025-12-20
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#14900)
- **PR/Issue**：#14900

## 总体目的

本提交是 Dependabot 自动生成的依赖版本升级，将 Apache HttpClient 5 `org.apache.httpcomponents.client5:httpclient5` 从 `5.5.1` 升级到 `5.6`。Apache HttpClient 5 是 Java 生态中广泛使用的 HTTP 客户端库，在 Iceberg 中主要用于 REST Catalog 客户端与远程 REST 服务端的 HTTP 通信，以及 AWS SDK 的 Apache HTTP 传输层（`software.amazon.awssdk:apache-client` 依赖 httpclient5）。它负责连接池管理、TLS/SSL 握手、重定向处理、超时控制等底层 HTTP 语义。

在 Iceberg 项目中，`httpcomponents-httpclient5` 通过 `gradle/libs.versions.toml` 的 `httpcomponents-httpclient5` 条目定义，被 `iceberg-core`（REST 客户端）与 `iceberg-aws`（AWS SDK Apache 传输）等模块引用。Dependabot 元数据显示该依赖为 `direct:production` 类型，进入发布制品运行时路径。本次升级属于语义版本的次版本升级（semver-minor，`5.5.1` → `5.6`），按语义化版本约定可包含向后兼容的新功能，不破坏现有 API。

此次升级的动机是常规依赖维护：HttpClient 5 的次版本发布通常包含连接池行为改进、HTTP/2 支持增强、TLS 兼容性修复以及潜在的安全加固。由于该库位于 REST Catalog 与 AWS 通信的关键路径上，保持版本同步有助于提升远程交互的稳定性与安全性。

## 如何达成设计目的

改动仅涉及 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `httpcomponents-httpclient5` 版本号的更新。该版本通过 `version.ref = "httpcomponents-httpclient5"` 被相关模块的 `build.gradle` 引用，单点修改即可同步升级使用该库的模块。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Apache HttpClient 5 版本号。

**工作逻辑**：
将版本目录中 `httpcomponents-httpclient5 = "5.5.1"` 修改为 `httpcomponents-httpclient5 = "5.6"`。该版本通过 `httpcomponents-httpclient5 = { module = "org.apache.httpcomponents.client5:httpclient5", version.ref = "httpcomponents-httpclient5" }` 定义，被 REST Catalog 客户端与 AWS SDK Apache 传输相关模块引用。修改后，使用 HttpClient 5 的模块将自动获得 5.6 版本的改进。

## 总结

本提交是 Dependabot 自动维护的 Apache HttpClient 5 次版本升级（5.5.1 → 5.6），属于常规的生产依赖版本维护。HttpClient 5 是 Iceberg REST Catalog 客户端与 AWS SDK HTTP 传输的基础，次版本升级带来向后兼容的改进与缺陷修复，确保远程通信的稳定性与安全性。
