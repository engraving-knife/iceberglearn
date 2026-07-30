# 提交 1613 6eef7809f 分析

## 提交信息
- 哈希：6eef7809f7441bf7e3c8313243ea4e296be0d2a6
- 日期：2025-01-21 10:26:10 -0700
- 作者：Amogh Jahagirdar
- 消息：Update notice files to reference 2025 (#12013)

## 总体目的

本提交将仓库内所有 NOTICE 文件中的版权年份从 2017-2024 更新为 2017-2025。Apache 项目的 NOTICE 文件是法律合规性文档的一部分，用于声明项目的版权归属和第三方组件的引用声明。根据 Apache 软件基金会（ASF）的发布政策，每年开始时需要将版权年份的下限更新到当前年份，以反映项目仍在持续维护。

2025 年新年伊始，Iceberg 项目（作为 Apache 顶级项目）需要按惯例同步更新其 NOTICE 文件中的版权声明范围。这是一项纯文档维护工作，不涉及任何代码逻辑变更，但属于发布流程中的合规要求。

这次更新覆盖了仓库内所有包含版权年份声明的 NOTICE 文件，确保各模块（核心、各计算引擎集成、各云厂商 bundle、OpenAPI 等）的版权声明保持一致。

## 如何达成设计目的

设计思路是机械式地批量替换：将所有 NOTICE 文件中的字符串 `Copyright 2017-2024 The Apache Software Foundation` 替换为 `Copyright 2017-2025 The Apache Software Foundation`。仓库中存在多个 NOTICE 文件，每个发布模块（核心 JAR、各 Flink/Spark 运行时 JAR、各云 bundle、OpenAPI 等）都有自己的 NOTICE 文件副本，因此需要逐个修改。

### 修改详情

共修改 13 个 NOTICE 文件，每个文件都执行了相同的替换：将 `Copyright 2017-2024` 改为 `Copyright 2017-2025`。下面按模块分组说明。

#### 根目录 NOTICE

仓库主 NOTICE 文件，是核心 Apache Iceberg 项目的版权声明。所有源码分发都会包含此文件。

#### aws-bundle/NOTICE、azure-bundle/NOTICE、gcp-bundle/NOTICE

三个云厂商 bundle 模块的 NOTICE 文件。这些 bundle 是为方便用户使用而打包的"胖 JAR"，包含 Iceberg 与对应云 SDK 的依赖，发布时需要附带各自的 NOTICE。

#### bundled-guava/NOTICE

Iceberg 在多个模块中依赖 Guava，bundled-guava 模块将 Guava 重新打包（relocation）以避免与用户classpath 中的 Guava 版本冲突。该 NOTICE 文件声明此 bundle 的版权。

#### flink/v1.18/flink-runtime/NOTICE、flink/v1.19/flink-runtime/NOTICE、flink/v1.20/flink-runtime/NOTICE

三个 Flink 版本对应的 flink-runtime 模块 NOTICE 文件。每个 Flink 版本集成都会构建一个 runtime JAR，需要各自的版权声明。

#### kafka-connect/kafka-connect-runtime/NOTICE

Kafka Connect 集成的 runtime 模块 NOTICE 文件。

#### open-api/NOTICE

OpenAPI（REST Catalog 规范）模块的 NOTICE 文件。

#### spark/v3.3/spark-runtime/NOTICE、spark/v3.4/spark-runtime/NOTICE、spark/v3.5/spark-runtime/NOTICE

三个 Spark 版本对应的 spark-runtime 模块 NOTICE 文件。与 Flink 类似，每个 Spark 版本集成都有自己的 runtime JAR 与 NOTICE。

## 小结

本次更新属于例行的年度版权年份维护，效果是让所有发布物的 NOTICE 文件符合 ASF 在 2025 年的合规要求。

影响范围：仅文档类文件，对编译、运行时行为、API 兼容性均无影响。所有变更都是同一字符串的批量替换。

回迁到 1.4.x 分支的注意事项：1.4.x 是较早的维护分支，其 NOTICE 文件可能仍停留在更早的年份（如 2017-2023 或 2017-2024）。如果 1.4.x 仍在维护并计划发布新版本，则需要类似地更新版权年份；但由于 1.4.x 的模块结构与当前 main 分支可能不完全一致（例如某些 bundle 或集成模块在 1.4.x 中尚未引入），回迁时应仅更新 1.4.x 中实际存在的 NOTICE 文件。本提交本身不引入功能变化，回迁风险极低，主要是合规性维护。
