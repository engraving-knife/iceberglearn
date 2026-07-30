# 提交 3128：Build: Bump com.aliyun:tea from 1.2.1 to 1.4.1 (#15078)

## 提交信息

- **序号**：3128 / 4088
- **哈希**：6134dd2181135ba8abc45312889b86a01cf13646
- **短哈希**：6134dd218
- **日期**：2026-01-17
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.aliyun:tea from 1.2.1 to 1.4.1 (#15078)
- **PR/Issue**：#15078

## 总体目的

这是 GitHub Dependabot 自动生成的依赖升级提交，将阿里云 `com.aliyun:tea` 库从 `1.2.1` 升级到 `1.4.1`。

`tea`（Tea SDK）是阿里云开源的通用 Java SDK 基础框架，提供请求/响应模型、序列化、HTTP 传输、异常处理等底层抽象，是阿里云 OSS SDK 及其它阿里云服务的公共运行时依赖。在 Iceberg 项目中，该依赖被 `iceberg-aliyun` 模块以 `implementation` 方式引入（见 `build.gradle` 中 `project(':iceberg-aliyun')` 子工程的依赖声明 `implementation libs.aliyun.tea`），与 `aliyun-sdk-oss`、`credentials-java` 一起共同支撑阿里云 OSS 表存储后端的请求通信。

本次升级类型为 `version-update:semver-minor`，即次版本升级（1.2.1 → 1.4.1，跨越 2 个次版本）。按语义化版本约定，次版本升级引入向后兼容的新功能，但 API 兼容性通常得以保持。相比前两个补丁升级，本次跨度更大，可能带来新的传输层特性或对阿里云新接口的支持，同时保持现有调用方式不变。

## 如何达成设计目的

改动仅更新 `gradle/libs.versions.toml` 中 `aliyun-tea` 的版本号常量，由 `iceberg-aliyun` 模块通过 `version.ref` 引用自动解析到新版本，无需改动源码。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 aliyun-tea 版本从 1.2.1 提升到 1.4.1。

**工作逻辑**：
该文件是集中式依赖版本目录。改动位于 `[versions]` 段，将 `aliyun-tea = "1.2.1"` 改为 `aliyun-tea = "1.4.1"`。在 `[libraries]` 段中已声明 `aliyun-tea = { module = "com.aliyun:tea", version.ref = "aliyun-tea" }`，`iceberg-aliyun` 子工程通过 `implementation libs.aliyun.tea` 消费。只需修改一处版本常量，OSS 通信栈底层的 Tea 运行时即统一升级。

## 总结

本次为阿里云 Tea SDK 的次版本升级，通过更新版本目录一处版本号，将 `iceberg-aliyun` 模块依赖的 Tea 运行时从 1.2.1 推进到 1.4.1，获取上游新增功能与改进，属于常规依赖维护，对现有 OSS 集成保持向后兼容。
