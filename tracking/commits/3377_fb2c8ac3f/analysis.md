# 提交 3377：chore: several fixes on the LICENSE/NOTICE (#15449)

## 提交信息

- **序号**：3377 / 4088
- **哈希**：fb2c8ac3faf342ec859075e5822e8477d1d26170
- **短哈希**：fb2c8ac3f
- **日期**：2026-03-12
- **作者**：JB Onofré
- **提交说明**：chore: several fixes on the LICENSE/NOTICE (#15449)
- **PR/Issue**：#15449

## 总体目的

本提交对 Iceberg 仓库中所有模块的 `LICENSE` 与 `NOTICE` 文件进行系统性清理与规范化，以满足 Apache 软件基金会（ASF）对发布物合规性的严格要求。Apache 项目在发布时必须随附准确、完整的 LICENSE 与 NOTICE 文件，清晰声明所含代码的版权与许可证信息。此次改动覆盖根目录、各 bundle 模块（aws-bundle、azure-bundle、gcp-bundle、bundled-guava）、各 Flink 运行时模块（v1.20、v2.0、v2.1）、各 Spark 运行时模块（v3.4、v3.5、v4.0、v4.1）、kafka-connect 运行时模块（hive、main）以及 open-api 模块，共 30 个文件。

具体问题包括三方面：一是根 `LICENSE` 文件中各依赖项的许可证表述不统一，仅写了许可证 URL 而缺少许可证名称（如将 `License: https://www.apache.org/licenses/LICENSE-2.0` 规范为 `License: Apache License, Version 2.0 - https://www.apache.org/licenses/LICENSE-2.0`）；二是根 `NOTICE` 的版权年份过期（从 `2017-2025` 更新为 `2017-2026`）；三是各 bundle 模块的 `LICENSE`/`NOTICE` 采用了过于冗长且格式不一致的逐依赖罗列方式（每个依赖列出 Group/Name/Version/Project URL/License 多行），改为更简洁、合规的统一格式（按 "This product bundles X" 的标准 Apache 式表述）。部分 bundle 的 NOTICE 也补充了第三方 NOTICE 的合并内容。

## 如何达成设计目的

整体思路是对每个模块的 LICENSE/NOTICE 做格式统一与内容校准：根目录统一许可证表述并更新年份；各 bundle 与运行时模块从"逐依赖元数据罗列"改为"按产品声明合并"的精简格式，确保每个被引入的第三方组件都有正确的许可证与版权声明，同时避免冗余的版本号等信息（版本号会随依赖升级而过时，不适合写在 LICENSE 中）。

## 修改详情

### `LICENSE` (+13/-11 lines)

**修改目的**：规范根 LICENSE 中各依赖项的许可证表述。

**工作逻辑**：
将所有依赖项的 `License:` 行从仅含 URL 的形式统一改为 `License: Apache License, Version 2.0 - https://www.apache.org/licenses/LICENSE-2.0` 的"名称 + URL"形式。涉及的依赖包括 Gradle Wrapper、Apache Avro、Apache Parquet、Cloudera Kite、Presto、Apache iBATIS、Apache Hive、Apache Spark、Delta Lake、Apache Commons、Apache HttpComponents Client、Apache Flink 等，确保许可证声明完整规范。

### `NOTICE` (+1/-1 lines)

**修改目的**：更新根 NOTICE 版权年份。

**工作逻辑**：
将版权声明年份从 `Copyright 2017-2025` 更新为 `Copyright 2017-2026`，反映当前年份。

### `aws-bundle/LICENSE`、`aws-bundle/NOTICE` (+大量改动)

**修改目的**：将 AWS bundle 的 LICENSE/NOTICE 从逐依赖元数据罗列改为标准合并格式。

**工作逻辑**：
原 LICENSE 以 "Group: ... Name: ... Version: ..." 的方式逐个列出 commons-codec、commons-logging、netty 各模块、httpcomponents、reactive-streams、AWS SDK 各模块等，每个都占多行。改为精简的 `This product bundles X.` 段落格式，每段附 Project URL 与 License。NOTICE 同步调整为合并第三方 NOTICE 内容的标准格式。这种格式更符合 Apache 发布规范，且不依赖易过时的版本号。

### `azure-bundle/LICENSE`、`azure-bundle/NOTICE`

**修改目的**：与 aws-bundle 同理，规范化 Azure bundle 的许可证声明格式。

**工作逻辑**：采用与 aws-bundle 一致的精简合并格式，替换原有的逐依赖元数据罗列。

### `gcp-bundle/LICENSE`、`gcp-bundle/NOTICE`

**修改目的**：规范化 GCP bundle 的许可证声明。

**工作逻辑**：该模块改动量最大（LICENSE 约 1331 行变动），同样从冗长的逐依赖罗列改为精简的按产品声明合并格式，NOTICE 同步调整。

### `bundled-guava/LICENSE`、`bundled-guava/NOTICE`

**修改目的**：规范 bundled-guava 模块的许可证表述。

**工作逻辑**：统一许可证声明格式。

### `flink/v1.20/flink-runtime/LICENSE`、`flink/v1.20/flink-runtime/NOTICE`

**修改目的**：规范 Flink 1.20 运行时模块的许可证声明。

**工作逻辑**：LICENSE 增加 497 行，NOTICE 增加 250 行，采用统一的合并格式并补充完整的第三方组件声明。

### `flink/v2.0/flink-runtime/LICENSE`、`flink/v2.0/flink-runtime/NOTICE` 及 `flink/v2.1/flink-runtime/LICENSE`、`flink/v2.1/flink-runtime/NOTICE`

**修改目的**：规范 Flink 2.0、2.1 运行时模块的许可证声明。

**工作逻辑**：与 v1.20 一致地采用统一合并格式，补充第三方组件声明。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE`、`kafka-connect/kafka-connect-runtime/hive/NOTICE`

**修改目的**：规范 Kafka Connect Hive 运行时模块的许可证声明。

**工作逻辑**：该模块 LICENSE 改动量大（约 2183 行变动），从逐依赖罗列改为精简合并格式。

### `kafka-connect/kafka-connect-runtime/main/LICENSE`、`kafka-connect/kafka-connect-runtime/main/NOTICE`

**修改目的**：规范 Kafka Connect 主运行时模块的许可证声明。

**工作逻辑**：同样从逐依赖罗列改为精简合并格式，NOTICE 同步调整。

### `spark/v3.4/spark-runtime/LICENSE`、`spark/v3.4/spark-runtime/NOTICE`（及 v3.5、v4.0、v4.1 同名文件）

**修改目的**：规范所有 Spark 版本运行时模块的许可证声明。

**工作逻辑**：
四个 Spark 版本（v3.4、v3.5、v4.0、v4.1）的 spark-runtime LICENSE 各增加 436 行、NOTICE 各增加 92 行，统一采用合并格式并补充完整的第三方组件许可证与版权声明。

### `open-api/LICENSE`、`open-api/NOTICE`

**修改目的**：规范 open-api 模块的许可证表述。

**工作逻辑**：统一许可证声明格式与根目录保持一致。

## 总结

本提交是对全仓 LICENSE/NOTICE 合规性的一次系统治理：统一许可证表述格式、更新版权年份、将各 bundle 与运行时模块从冗长易过时的逐依赖元数据罗列改为简洁规范的合并声明格式。改动虽不涉及功能逻辑，但对 Iceberg 作为 Apache 顶级项目的发布合规性至关重要，确保所有第三方依赖的版权与许可证信息准确、完整且符合 ASF 发布要求。
