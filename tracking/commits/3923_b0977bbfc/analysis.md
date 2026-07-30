# 提交 3923：Build: Bump datamodel-code-generator from 0.60.0 to 0.63.0 (#16907)

## 提交信息

- **序号**：3923 / 4088
- **哈希**：b0977bbfcbbdfec17711acdfe0c14921d50d2f41
- **短哈希**：b0977bbfc
- **日期**：2026-06-21 10:26:23 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump datamodel-code-generator from 0.60.0 to 0.63.0 (#16907)
- **PR/Issue**：#16907

## 总体目的

这次提交包含两部分工作：将 `datamodel-code-generator` 从 0.60.0 升级到 0.63.0，以及随之同步更新各运行时模块的 `runtime-deps.txt` 文件中记录的传递性依赖版本。

`datamodel-code-generator` 是一个 Python 工具，用于根据 OpenAPI 规范自动生成 Pydantic 模型类。Iceberg 项目使用它根据 REST catalog 的 OpenAPI 规范生成 `rest-catalog-open-api.py` 文件。升级到 0.63.0 后，生成的代码模式发生了变化，尤其是对 `Summary` 模型中 `__pydantic_extra__` 字段的处理方式从内联声明改为运行时注解 + `model_rebuild(force=True)`，这反映了新版生成器对 Pydantic v2 extra fields 处理机制的适配。

此外，PR 还同步更新了 Flink（v1.20/v2.0/v2.1）、Spark（v3.5/v4.0/v4.1）、GCP bundle、Kafka Connect runtime 等模块的 runtime-deps.txt 文件，以反映上游依赖的版本变化（如 nessie 0.107→0.108、grpc-netty-shaded 1.81→1.82、Guava 33.5→33.6、OpenTelemetry 1.57→1.63 等）。这些 runtime-deps.txt 文件用于记录最终打包到运行时分发物中的依赖版本，确保可重现构建和运行时依赖一致性。

## 如何达成设计目的

通过修改 `open-api/requirements.txt` 升级 generator 版本，重新运行代码生成以更新 `open-api/rest-catalog-open-api.py`，然后通过构建流程生成各模块的 runtime-deps.txt 以反映新的传递性依赖。这体现了 Iceberg 对依赖升级的端到端处理：不仅升级工具版本，还重新生成代码并同步运行时依赖清单。

## 修改详情

### `open-api/requirements.txt` (+1/-1 lines)

**修改目的**：升级 datamodel-code-generator 版本。

**工作逻辑**：将 `datamodel-code-generator==0.60.0` 改为 `datamodel-code-generator==0.63.0`。

### `open-api/rest-catalog-open-api.py` (+8/-2 lines)

**修改目的**：适配新版生成器输出的代码模式。

**工作逻辑**：
新版生成器将 `Summary` 模型的 `__pydantic_extra__` 字段声明从类体内的 `__pydantic_extra__: dict[str, str]` 改为运行时注解 `Summary.__annotations__['__pydantic_extra__'] = Dict[str, str]`，并调用 `Summary.model_rebuild(force=True)` 强制重新解析模型。同时在 import 中添加 `Dict`。这是因为新版生成器对 Pydantic v2 的 `extra='allow'` 配合 `__pydantic_extra__` 的处理采用了更兼容的运行时声明方式。

### `flink/v1.20/flink-runtime/runtime-deps.txt`、`flink/v2.0/flink-runtime/runtime-deps.txt`、`flink/v2.1/flink-runtime/runtime-deps.txt` (各 +2/-2 lines)

**修改目的**：同步 Flink 运行时的 nessie 依赖版本。

**工作逻辑**：将 `nessie-client` 和 `nessie-model` 从 `0.107` 更新为 `0.108`，对应 #16897 的 nessie 升级。

### `spark/v3.5/spark-runtime/runtime-deps.txt`、`spark/v4.0/spark-runtime/runtime-deps.txt`、`spark/v4.1/spark-runtime/runtime-deps.txt` (各 +2/-2 lines)

**修改目的**：同步 Spark 运行时的 nessie 依赖版本。

**工作逻辑**：同上，将 nessie-client/model 从 `0.107` 更新为 `0.108`。

### `gcp-bundle/runtime-deps.txt` (+22/-22 lines)

**修改目的**：同步 GCP bundle 运行时的传递性依赖版本。

**工作逻辑**：批量更新多个 Google Cloud 相关依赖版本，包括：caffeine 3.1→3.2、gapic-google-cloud-storage-v2 2.68→2.69、grpc-google-cloud-storage-v2 2.68→2.69、proto-google-cloud-storage-v2 2.68→2.69、proto-google-common-protos 2.71→2.72、proto-google-iam-v1 1.66→1.67、api-common 2.63→2.64、gax 系列 2.80→2.81、google-auth-library 1.47→1.48、google-cloud-core 系列 2.70→2.71、google-cloud-storage 2.68→2.69、error_prone_annotations 2.48→2.49、guava 33.5→33.6、opentelemetry 系列 1.57→1.63（exporter-logging 1.52→1.63、sdk-extension-autoconfigure-spi 1.57→1.62）。

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+1/-1 lines)

**修改目的**：同步 Kafka Connect 运行时的 grpc-netty-shaded 版本。

**工作逻辑**：将 `grpc-netty-shaded` 从 `1.81` 更新为 `1.82`，对应 #16902 的 grpc 升级。

## 总结

这次提交是一次涉及面较广的构建依赖升级：不仅升级了 datamodel-code-generator 工具本身并适配其新代码生成模式，还端到端地同步了多个运行时模块的传递性依赖版本清单（nessie、grpc、GCP 系列、OpenTelemetry 等），确保构建产物与版本目录中的依赖声明保持一致。这体现了 Iceberg 项目在依赖管理上的系统性方法——升级源头依赖后，运行时依赖清单也必须同步刷新。
