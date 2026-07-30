# 提交分析：3868 - Build: Bump AWS SDK BOM

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 3868 |
| 短哈希 | 70b903305 |
| 完整哈希 | 70b90330582e4023a097330620a3e63746b2375b |
| 日期 | 2026-06-12 10:01:58 -0700 |
| 作者 | Huaxin Gao |
| 提交说明 | Build: Bump software.amazon.awssdk:bom from 2.44.12 to 2.45.1 (#16709) |

## 总体目的

将 AWS SDK for Java 的 BOM（Bill of Materials）从 2.44.12 升级到 2.45.1，并重新生成 `aws-bundle` 和 `kafka-connect-runtime` 的运行时依赖基线文件。

## 修改详情

### 1. AWS SDK 版本升级

**文件路径**: `gradle/libs.versions.toml`（通过 BOM 管理版本）

AWS SDK 从 2.44.x 系列升级到 2.45.x 系列。

### 2. 重新生成运行时依赖基线

**涉及文件**:
- `aws-bundle/runtime-deps.txt`
- `kafka-connect-runtime/runtime-deps.txt`

这些文件列出了打包时需要包含的依赖及其版本。升级后所有 `software.amazon.awssdk` 组件的版本从 `2.44` 更新为 `2.45`，`aws-crt` 从 `0.45` 升级到 `0.46`。

### 主要变更内容

`aws-bundle/runtime-deps.txt` 中涉及约 40 个 AWS SDK 组件的版本更新，例如：
- `software.amazon.awssdk:annotations:2.44` → `2.45`
- `software.amazon.awssdk:s3:2.44` → `2.45`
- `software.amazon.awssdk:glue:2.44` → `2.45`
- `software.amazon.awssdk:netty-nio-client:2.44` → `2.45`
- `software.amazon.awssdk.crt:aws-crt:0.45` → `0.46`

## 依赖升级类提交说明

此提交属于依赖升级类，将 AWS SDK for Java 从 2.44.12 升级到 2.45.1。这是一个次要版本升级（2.44 → 2.45），同时伴随 AWS CRT（Common Runtime）从 0.45 升级到 0.46。升级后需要重新生成运行时依赖基线文件，以确保打包时包含正确版本的传递依赖。

## 总结

AWS SDK 的例行版本升级，跨一个次要版本。通过重新生成 `runtime-deps.txt` 基线文件来同步所有 AWS SDK 组件的版本号，确保 `aws-bundle` 和 `kafka-connect-runtime` 的打包一致性。
