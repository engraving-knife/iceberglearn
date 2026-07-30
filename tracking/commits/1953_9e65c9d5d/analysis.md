# 提交 1953：AWS: Update the bundle `NOTICE` and `LICENSE` (#12553)

## 提交信息

- **序号**：1953 / 4088
- **哈希**：9e65c9d5d2580800001d4d8f8ab85c5fa7c8fb98
- **短哈希**：9e65c9d5d
- **日期**：2025-04-02 14:27:19 +0200
- **作者**：Sanjay Marreddi
- **提交说明**：AWS: Update the bundle `NOTICE` and `LICENSE` (#12553)
  - AWS: Update the aws-bundle with latest dependencies
  - AWS: Remove the aws-crt dependency
- **PR/Issue**：#12553

## 总体目的

本提交更新 `aws-bundle` 模块的依赖清单与许可证文件，以反映新增的两个 AWS 相关依赖：`aws-s3-accessgrants-java-plugin`（S3 Access Grants 插件，2.3.0）和 `analyticsaccelerator-s3`（S3 Analytics Accelerator，1.0.0）。同时在 `build.gradle` 中显式声明这两个依赖（以及 `crt-core`），使 aws-bundle 的实际打包内容与 LICENSE/NOTICE 一致。

提交说明中提到的"Remove the aws-crt dependency"指的是在 PR 历史中对 aws-crt 依赖的处理，最终 build.gradle 中显式加入了 `software.amazon.awssdk:crt-core`。整体目的是让 aws-bundle 包含最新的 AWS S3 增强组件（Access Grants 与 Analytics Accelerator），并保持法律文件合规。

## 如何达成设计目的

通过修改三处来达成：

1. 在 `aws-bundle/build.gradle` 的 dependencies 块中加入 `libs.awssdk.s3accessgrants`、`software.amazon.awssdk:crt-core`、`libs.analyticsaccelerator.s3`。
2. 在 `aws-bundle/LICENSE` 与 `aws-bundle/NOTICE` 中追加这两个新依赖的许可证条目。
3. 由于 kafka-connect 的 hive/main 运行时 bundle 也传递包含这些 AWS 依赖，同步更新其 LICENSE/NOTICE。

## 修改详情

### `aws-bundle/build.gradle` (修改, +4/-0 lines)

**修改目的**：在 aws-bundle 中声明新增依赖。

**工作逻辑**：在 dependencies 块中新增：
- `implementation libs.awssdk.s3accessgrants`（S3 Access Grants 插件）
- `implementation "software.amazon.awssdk:crt-core"`（CRT 核心）
- `implementation libs.analyticsaccelerator.s3`（S3 Analytics Accelerator）

### `aws-bundle/LICENSE` (修改, +11/-0 lines)

**修改目的**：追加新依赖的许可证声明。

**工作逻辑**：新增两个条目：
- `software.amazon.s3.accessgrants:aws-s3-accessgrants-java-plugin:2.3.0`，Apache License 2.0
- `software.amazon.s3.analyticsaccelerator:analyticsaccelerator-s3:1.0.0`，Apache License 2.0

### `aws-bundle/NOTICE` (修改, +2/-0 lines)

**修改目的**：追加新依赖的 NOTICE 条目。

**工作逻辑**：在 NOTICE 文件中新增上述两个依赖的 NOTICE 行。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE` (修改, +6/-0 lines)

**修改目的**：同步 hive 运行时 bundle 的 LICENSE。

**工作逻辑**：追加 `analyticsaccelerator-s3:1.0.0` 的许可证条目（Apache License 2.0）。

### `kafka-connect/kafka-connect-runtime/hive/NOTICE` (修改, +1/-0 lines)

**修改目的**：同步 hive 运行时 bundle 的 NOTICE。

**工作逻辑**：追加 `analyticsaccelerator-s3:1.0.0` 的 NOTICE 行。

### `kafka-connect/kafka-connect-runtime/main/LICENSE` (修改, +6/-0 lines)

**修改目的**：同步 main 运行时 bundle 的 LICENSE。

**工作逻辑**：追加 `analyticsaccelerator-s3:1.0.0` 的许可证条目。

### `kafka-connect/kafka-connect-runtime/main/NOTICE` (修改, +1/-0 lines)

**修改目的**：同步 main 运行时 bundle 的 NOTICE。

**工作逻辑**：追加 `analyticsaccelerator-s3:1.0.0` 的 NOTICE 行。

## 总结

本提交为 `aws-bundle` 添加了 S3 Access Grants 插件与 S3 Analytics Accelerator 两个新依赖，并相应更新 aws-bundle 及 kafka-connect 两个运行时 bundle 的 LICENSE/NOTICE 文件，确保打包依赖与法律声明保持一致合规。
