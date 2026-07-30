# 提交 3188：Build: Bump com.aliyun.oss:aliyun-sdk-oss from 3.18.4 to 3.18.5 (#15198)

## 提交信息

- **序号**：3188 / 4088
- **哈希**：3848f1358a35b5f539116e56cb6ee0fa51fa3906
- **短哈希**：3848f1358
- **日期**：2026-01-31
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump com.aliyun.oss:aliyun-sdk-oss from 3.18.4 to 3.18.5 (#15198)
- **PR/Issue**：#15198

## 总体目的

这是 Dependabot 自动发起的依赖升级，将阿里云 OSS（Object Storage Service）Java SDK 从 `3.18.4` 升至 `3.18.5`。在 Iceberg 中，`com.aliyun.oss:aliyun-sdk-oss` 是阿里云 OSS `FileIO` 实现的底层依赖，用于在阿里云对象存储上读写 Iceberg 的数据文件与元数据文件，是面向阿里云部署场景的生产依赖。

本次为语义化版本的 **patch** 升级（`3.18.4` → `3.18.5`）。按 SemVer 约定，patch 升级仅包含向后兼容的缺陷修复，不引入新 API 也不破坏既有行为。因此该升级风险低，预期效果是获得阿里云 OSS SDK 在 `3.18.5` 中提供的稳定性修复（如上传/下载、鉴权、重试或边界条件相关的修复），同时保持 Iceberg 阿里云 OSS FileIO 的调用方式不变，无需改动任何业务代码。

## 如何达成设计目的

Dependabot 只修改版本目录文件 `gradle/libs.versions.toml` 中 `aliyun-sdk-oss` 这一个版本变量。该变量经 `[libraries]` 段的 `aliyun-sdk-oss = { module = "com.aliyun.oss:aliyun-sdk-oss", version.ref = "aliyun-sdk-oss" }` 引用，Gradle 解析时自动把新版本应用到 OSS FileIO 模块的依赖图，无需改动源码。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将阿里云 OSS SDK 版本从 `3.18.4` 提升至 `3.18.5`。

**工作逻辑**：
将 `[versions]` 段中的 `aliyun-sdk-oss = "3.18.4"` 改为 `aliyun-sdk-oss = "3.18.5"`。由于 `[libraries]` 段对应条目通过 `version.ref` 引用该变量，这一处改动即可让阿里云 OSS SDK 升级到 `3.18.5`，从而把上游 patch 修复纳入 Iceberg 的阿里云 OSS 集成。

## 总结

本次提交以最小改动将阿里云 OSS SDK patch 升级到 `3.18.5`，为 Iceberg 阿里云 OSS FileIO 引入上游缺陷修复，属于低风险、无代码改动的常规依赖维护。
