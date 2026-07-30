# 提交 3982：Build: Bump nessie from 0.108.0 to 0.108.1 (#17101)

## 提交信息

- **序号**：3982 / 4088
- **哈希**：f636995eb1cb22a34b8a4c83d05e4ce7ee905496
- **短哈希**：f636995eb
- **日期**：2026-07-05 00:29:59 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump nessie from 0.108.0 to 0.108.1 (#17101)
- **PR/Issue**：#17101

## 总体目的

Dependabot 自动升级 Nessie 相关依赖从 0.108.0 到 0.108.1，补丁版本升级。Nessie 是一个提供 Git-like 数据版本控制的 catalog，Iceberg 支持 Nessie 作为 catalog 后端。本次升级涉及多个 Nessie 模块：nessie-client、nessie-jaxrs-testextension、nessie-versioned-storage-inmemory-tests、nessie-versioned-storage-testextension。

## 如何达成设计目的

修改 Gradle 版本目录中的 `nessie` 版本号，统一升级所有 Nessie 相关模块。

## 修改详情

### `gradle/libs.versions.toml` (版本号修改)

**修改目的**：升级 Nessie 版本。

**工作逻辑**：
```toml
nessie = "0.108.1"  # 原为 "0.108.0"
```
该版本号被多个 Nessie 依赖模块引用，统一升级。

## 总结

常规依赖升级，将 Nessie 从 0.108.0 升级到 0.108.1，涉及客户端和测试扩展模块，属于补丁版本升级，风险较低。
