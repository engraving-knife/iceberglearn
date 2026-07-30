# 提交 2145：Build: Bump nessie from 0.103.6 to 0.104.1

## 提交信息

- **序号**：2145 / 4088
- **哈希**：a654f3f91c770a96aa81c31f17274d9391d8f7fc
- **短哈希**：a654f3f91
- **日期**：2025-05-19 08:19:16 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.103.6 to 0.104.1 (#13025)
- **PR/Issue**：#13025

## 总体目的

这个提交是 Dependabot 自动生成的依赖更新，将 Nessie（Project Nessie，一个版本化数据目录）相关依赖从 0.103.6 升级到 0.104.1。Iceberg 使用 Nessie 作为支持的 catalog 类型之一，相关依赖包括 nessie-client（客户端）、nessie-jaxrs-testextension（JAX-RS 测试扩展）、nessie-versioned-storage-inmemory-tests（内存存储测试）和 nessie-versioned-storage-testextension（存储测试扩展）。这是一个 minor 版本升级（从 0.103 到 0.104），可能包含新功能和接口变更。

## 如何达成设计目的

1. 在 gradle/libs.versions.toml 中将 Nessie 相关依赖的版本引用从 0.103.6 更新到 0.104.1。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 line)

**修改目的**：升级 Nessie 依赖版本。

**工作逻辑**：将 Nessie 的版本号引用从 0.103.6 改为 0.104.1。由于使用版本引用（version.ref），一处修改即可更新所有 Nessie 相关组件的版本，包括 nessie-client、nessie-jaxrs-testextension、nessie-versioned-storage-inmemory-tests 和 nessie-versioned-storage-testextension。

## 总结

这是一个由 Dependabot 自动生成的 Nessie 依赖 minor 版本升级，从 0.103.6 到 0.104.1。作为 minor 版本升级，可能包含新的功能特性，需要关注是否有兼容性变化。该更新确保 Iceberg 的 Nessie catalog 支持保持最新。
