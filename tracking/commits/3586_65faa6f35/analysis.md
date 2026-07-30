# 提交 3586：Runtimes, Bundles: Add runtime-deps.txt files to track dependencies (#16081)

## 提交信息

- **序号**：3586 / 4088
- **哈希**：65faa6f350ee67361b6ef27450432f5df4f83a98
- **短哈希**：65faa6f35
- **日期**：2026-04-24 15:16:07 -0700
- **作者**：Kevin Liu
- **提交说明**：Runtimes, Bundles: Add runtime-deps.txt files to track dependencies (#16081)
- **PR/Issue**：#16081

## 总体目的

该提交为 Iceberg 的运行时分发包（runtime distributions）和捆绑包（bundles）添加 `runtime-deps.txt` 文件，用于跟踪每个分发包中包含的依赖及其版本。之前只有 Spark 运行时模块有 `runtime-deps.txt` 文件，而 Flink 运行时、Kafka Connect 运行时、AWS/Azure/GCP 捆绑包缺少此类文件。

`runtime-deps.txt` 文件的作用是作为运行时依赖的基线（baseline），CI 中的 `checkAllRuntimeDeps` 任务会将实际打包的依赖与该文件进行比较，检测意外的依赖变更（如新增、移除或版本变化）。这有助于在依赖升级或代码变更时及时发现意外的依赖变化，避免分发包中引入不期望的依赖或版本冲突。该提交为所有缺少 `runtime-deps.txt` 的运行时/捆绑包模块创建了初始基线文件。

## 如何达成设计目的

为以下 10 个模块创建 `runtime-deps.txt` 文件，列出该模块运行时分发包中的所有依赖（格式为 `group:artifact:version`）：
- `aws-bundle/runtime-deps.txt`（70 条目）
- `azure-bundle/runtime-deps.txt`（44 条目）
- `flink/v1.20/flink-runtime/runtime-deps.txt`（33 条目）
- `flink/v2.0/flink-runtime/runtime-deps.txt`（33 条目）
- `flink/v2.1/flink-runtime/runtime-deps.txt`（33 条目）
- `gcp-bundle/runtime-deps.txt`（114 条目）
- `kafka-connect/kafka-connect-runtime/runtime-deps.txt`（233 条目）
- `spark/v3.4/spark-runtime/runtime-deps.txt`（40 条目）
- `spark/v3.5/spark-runtime/runtime-deps.txt`（40 条目）
- `spark/v4.0/spark-runtime/runtime-deps.txt`（40 条目）

## 修改详情

### `aws-bundle/runtime-deps.txt` (+70/-0 lines, new file)

**修改目的**：AWS 捆绑包的运行时依赖基线。

**工作逻辑**：
列出 AWS 捆绑包的所有运行时依赖，包括 AWS SDK（`software.amazon.awssdk:*:2.42.33`）、Netty、HTTP Components、Log4j、SLF4J、Caffeine 等。版本与 `gradle/libs.versions.toml` 中定义一致。

### `azure-bundle/runtime-deps.txt` (+44/-0 lines, new file)

**修改目的**：Azure 捆绑包的运行时依赖基线。

**工作逻辑**：
列出 Azure 捆绑包的运行时依赖，包括 Azure SDK（`com.azure:*`）、Netty、Reactor、Jackson、JNA 等。

### `flink/v1.20/flink-runtime/runtime-deps.txt` (+33/-0 lines, new file)

**修改目的**：Flink 1.20 运行时的依赖基线。

**工作逻辑**：
列出 Flink 1.20 运行时分发包的依赖，包括 Jackson、Caffeine、Avro、Aircompressor 等。

### `flink/v2.0/flink-runtime/runtime-deps.txt` (+33/-0 lines, new file)

**修改目的**：Flink 2.0 运行时的依赖基线。内容与 v1.20 类似。

### `flink/v2.1/flink-runtime/runtime-deps.txt` (+33/-0 lines, new file)

**修改目的**：Flink 2.1 运行时的依赖基线。内容与 v1.20 类似。

### `gcp-bundle/runtime-deps.txt` (+114/-0 lines, new file)

**修改目的**：GCP 捆绑包的运行时依赖基线。

**工作逻辑**：
列出 GCP 捆绑包的运行时依赖，包括 Google Cloud Storage SDK、Google Auth、gRPC、Protobuf、Guava 等。

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+233/-0 lines, new file)

**修改目的**：Kafka Connect 运行时的依赖基线。

**工作逻辑**：
列出 Kafka Connect 运行时分发包的所有依赖（233 条目），是所有模块中依赖最多的。

### `spark/v3.4/spark-runtime/runtime-deps.txt` (+40/-0 lines, new file)

**修改目的**：Spark 3.4 运行时的依赖基线。

### `spark/v3.5/spark-runtime/runtime-deps.txt` (+40/-0 lines, new file)

**修改目的**：Spark 3.5 运行时的依赖基线。

### `spark/v4.0/spark-runtime/runtime-deps.txt` (+40/-0 lines, new file)

**修改目的**：Spark 4.0 运行时的依赖基线。

## 总结

该提交为 Iceberg 的所有运行时/捆绑包模块添加了 `runtime-deps.txt` 依赖基线文件，使 CI 的 `checkAllRuntimeDeps` 任务能够检测所有分发包的意外依赖变更。这是一个重要的构建基础设施改进，与 3585 提交（CI 中启用所有模块检查）配合使用，确保所有引擎版本的运行时依赖一致性。
