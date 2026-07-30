# 提交 2434：Doc: Flink: Add doc for the dynamic sink (#13608)

## 提交信息

- **序号**：2434 / 4088
- **哈希**：a73b48bf52e97bde22bb62c1030416edbb4775d7
- **短哈希**：a73b48bf5
- **日期**：2025-07-30 17:48:05 +0200
- **作者**：GuoYu
- **提交说明**：Doc: Flink: Add doc for the dynamic sink (#13608)
- **PR/Issue**：#13608

## 总体目的

本提交为 Flink 的 Dynamic Iceberg Sink 功能新增了完整的用户文档。Dynamic Sink 是 Iceberg Flink 集成中的一项重要能力，允许单个 sink 在运行时动态地将记录路由到多个 Iceberg 表，并支持动态建表、schema 演进和分区演进。在此提交之前，该功能缺乏正式文档，用户难以了解其能力、配置方式和使用约束。

文档涵盖了 Dynamic Sink 的三大核心能力、快速上手示例、动态路由配置、schema 演进机制（含缓存设计）、完整配置项表格以及使用注意事项。这使得用户能够自助地使用该功能，而无需阅读源码。

## 如何达成设计目的

在 `docs/docs/flink-writes.md` 文件末尾追加一个全新的 "Dynamic Iceberg Flink Sink" 章节，包含：
1. 功能概述（多表写入、动态建表/更新、动态 schema/分区演进）
2. 快速上手代码示例
3. 配置示例与动态路由（`DynamicRecordGenerator`）实现示例
4. `DynamicRecord` 所需字段说明表
5. Schema 演进规则（支持/不支持的变更）与缓存机制说明
6. Dynamic Sink 配置方法表
7. 注意事项（RANGE 分布模式回退、属性优先级）

## 修改详情

### `docs/docs/flink-writes.md` (+151/-0 lines)

**修改目的**：新增 Dynamic Iceberg Flink Sink 的完整文档。

**工作逻辑**：
- **功能概述**：说明 Dynamic Sink 支持单 sink 写多表、动态建表与更新、动态 schema 和分区演进，所有配置通过 `DynamicRecord` 类控制，无需重启 Flink 作业。
- **快速上手**：展示使用 `DynamicIcebergSink.forInput(dataStream).generator(...).catalogLoader(...).writeParallelism(...).immediateTableUpdate(true).append()` 的典型用法。
- **配置示例**：展示通过 builder 设置通用属性（如压缩编码）和 sink 专属选项（`writeParallelism`、`uidPrefix`、`cacheMaxSize`、`cacheRefreshMs`）。
- **动态路由**：给出实现 `DynamicRecordGenerator` 接口的示例，根据业务逻辑决定每条记录的目标表、分支、schema、分区等。并列表说明 `DynamicRecord` 需要的属性：`TableIdentifier`、`Branch`、`Schema`、`Spec`、`RowData`、`DistributionMode`、`Parallelism`、`UpsertMode`、`EqualityFields`。
- **Schema 演进**：说明 sink 会尝试匹配输入 schema 与现有表 schema，无法匹配时在约束范围内演进表 schema。支持新增列、加宽类型、required 改 optional；不支持删除列、重命名列（避免数据丢失和基于名称的比较冲突）。说明 LRU 缓存机制和 `immediateTableUpdate` 配置。
- **缓存**：区分表元数据缓存和输入 schema 缓存，分别由 `cacheMaxSize` 和 `inputSchemasPerTableCacheMaxSize` 控制。建议复用相同 schema 实例提高命中率。
- **配置方法表**：列出 `overwrite`、`writeParallelism`、`uidPrefix`、`snapshotProperties`、`toBranch`、`cacheMaxSize`、`cacheRefreshMs`、`inputSchemasPerTableCacheMaxSize`、`immediateTableUpdate`、`set`、`setAll` 等方法及说明。
- **注意事项**：RANGE 分布模式不支持，会回退为 HASH；当表属性与 sink 属性冲突时，sink 属性优先。

## 总结

本提交是纯文档增强，为 Flink Dynamic Iceberg Sink 功能提供了全面、结构化的用户文档。文档详细覆盖了功能概述、使用示例、配置方法、schema 演进规则和注意事项，对该功能的推广和正确使用有重要意义。
