# 提交 3512：Spark 4.1: Add runtime-deps.txt. (#15860)

## 提交信息

- **序号**：3512 / 4088
- **哈希**：66870849f2fa1b40f512971a9ad86bf912e7649d
- **短哈希**：66870849f2
- **日期**：2026-04-10 10:07:38 -0700
- **作者**：Ryan Blue
- **提交说明**：Spark 4.1: Add runtime-deps.txt. (#15860)
- **PR/Issue**：#15860

## 总体目的

为 Spark 4.1 runtime 模块添加 `runtime-deps.txt` 基线文件。这是提交 3497（#15855）引入的运行时依赖守卫机制的一部分。该机制要求每个 bundled 模块都有 check-in 的 `runtime-deps.txt` 基线文件，用于检测意外的传递依赖泄漏。Spark 4.1 是新支持的版本，此前缺少该基线文件，导致 CI 中的 `checkRuntimeDeps` 任务失败。

## 如何达成设计目的

生成 Spark 4.1 runtime 模块的 `runtime-deps.txt` 文件，列出所有解析到的运行时依赖（排除 `org.apache.iceberg:` 前缀的依赖）。

## 修改详情

### `spark/v4.1/spark-runtime/runtime-deps.txt` (+52 lines, 新文件)

**修改目的**：创建 Spark 4.1 runtime 的依赖基线文件。

**工作逻辑**：文件列出 52 个运行时依赖，按 `group:artifact:version` 格式排序，包括：
- Jackson 系列（jackson-annotations, jackson-core, jackson-databind, jackson-datatype-jsr310）
- Caffeine、Gson、Flatbuffers
- Aliyun（credentials-java, tea）
- OkHttp、Okio
- JAXB（jaxb-core, jaxb-impl）
- Failsafe、Aircompressor 2.0.3
- Netty（netty-buffer, netty-common）
- Arrow、Avro、Datasketches
- HTTP Components（httpclient5, httpcore5, httpcore5-h2）
- ORC、Parquet 系列
- Eclipse Collections、MicroProfile OpenAPI
- Kotlin Stdlib、Nessie、RoaringBitmap 等

## 总结

基础设施补全提交，为 Spark 4.1 runtime 模块添加 `runtime-deps.txt` 基线文件。这是提交 3497（#15855）引入的运行时依赖守卫机制的必要组成部分，Spark 4.1 作为新版本此前缺少该文件导致 CI 失败。
