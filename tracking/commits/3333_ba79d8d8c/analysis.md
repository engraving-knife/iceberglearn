# 提交 3333：Build: Bump kafka from 3.9.1 to 3.9.2 (#15478)

## 提交信息

- **序号**：3333 / 4088
- **哈希**：ba79d8d8c0af352888e3280450ff1e873a14d7cb
- **短哈希**：ba79d8d8c
- **日期**：2026-02-28 22:09:56 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump kafka from 3.9.1 to 3.9.2 (#15478)
- **PR/Issue**：#15478

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 Kafka 相关依赖从 `3.9.1` 升级到 `3.9.2`。

Apache Kafka 在 Iceberg 生态中有两层用途。第一层是作为变更捕获（CDC）与流式入湖的源端：Iceberg 提供了 `iceberg-kafka-connect` 模块，实现为 Kafka Connect 的 Source/Sink Connector，将 Kafka topic 中的数据写入 Iceberg 表或将 Iceberg 表变更推送到 Kafka。第二层是作为流式引擎的集成对象：Flink 与 Spark 在处理 Iceberg 表时可能需要与 Kafka 交互（例如 exactly-once 写入时的 checkpoint 协调、流式 source）。此外，Iceberg 还依赖 Kafka 的客户端库在测试中搭建嵌入式 Kafka 集群以验证相关行为。

本次升级同时覆盖四个 Kafka 模块：
- `kafka-clients`：Kafka 核心客户端库（生产者/消费者/Admin 客户端），是运行时与测试的基础依赖；
- `connect-api`：Kafka Connect 的 API 接口，`iceberg-kafka-connect` 模块基于它实现 Connector；
- `connect-json`：Kafka Connect 的 JSON 序列化支持，用于 Connect 框架内部的数据转换；
- `connect-transforms`：Kafka Connect 的单消息转换（Single Message Transforms）API，允许在 Source/Sink 之间对记录做轻量转换。

其中 `kafka-clients` 同时出现在产品代码与测试中，其余三个主要用于 `iceberg-kafka-connect` 模块。

本次升级属于语义化版本的 **patch（补丁）** 升级（`3.9.1` → `3.9.2`，`update-type: version-update:semver-patch`，四个模块均为 patch 级）。patch 升级仅含缺陷修复与内部改进，不引入 API 破坏。Kafka 的 patch 版本通常修复客户端协议、消费者重平衡、生产者重试、Connect 框架稳定性等方面的问题。预期影响是获得 3.9.2 的缺陷修复，对 Iceberg 的 Kafka Connect 集成与流式测试行为无可见变化。

## 如何达成设计目的

改动仅修改版本目录文件 `gradle/libs.versions.toml` 中 `kafka` 这一项的版本字符串。Iceberg 在版本目录里定义了单一 `kafka` 版本变量，被上述四个模块坐标共同引用，因此一行版本号变更即可联动升级全部 Kafka 依赖，保证客户端与 Connect 各模块版本一致。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Kafka 全套依赖版本从 3.9.1 提升到 3.9.2。

**工作逻辑**：
在版本目录 `[versions]` 段中，将 `kafka = "3.9.1"` 修改为 `kafka = "3.9.2"`。该变量被 `kafka-clients`、`connect-api`、`connect-json`、`connect-transforms` 四个库坐标共同引用，构建时会全部解析到 `3.9.2`。这是一次纯版本号变更，不涉及代码或配置逻辑调整，保证 Kafka 客户端与 Connect 各组件版本一致，避免模块间版本错配。

## 总结

本次提交通过 Dependabot 将 Kafka 全套依赖（kafka-clients + 三个 Connect 模块）从 3.9.1 升级到 3.9.2（patch 级），以获取上游缺陷修复。改动局限于版本目录单行，借助单一版本变量统一约束四个模块，风险低，对 Iceberg 的 Kafka Connect 集成与流式测试行为无破坏性影响，属于日常依赖维护的一部分。
