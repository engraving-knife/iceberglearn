# 提交 2047：Docs: Release notes for 1.9.0

## 提交信息

- **序号**：2047 / 4088
- **哈希**：dc26b72ad016840b79d62bf8a84b7f2109e9b71b
- **短哈希**：dc26b72ad
- **日期**：2025-04-28 08:38:25 +0200
- **作者**：Ajantha Bhat
- **提交说明**：Docs: Release notes for 1.9.0 (#12911)
- **PR/Issue**：#12911

## 总体目的

本提交为 Apache Iceberg 1.9.0 版本添加正式的发布说明。1.9.0 于 2025 年 4 月 28 日发布，包含大量新功能和 bug 修复。发布说明是项目文档站点的重要组成部分，帮助用户了解每个版本包含的变更内容，便于决定是否升级以及了解新功能。

该 PR 经过多位维护者（Eduard Tudenhoefner、Fokko Driesprong）协作review 和修改，确保发布说明的完整性和准确性。

## 如何达成设计目的

在文档站点的 `site/docs/releases.md` 文件中，新增 1.9.0 版本的发布说明段落，按模块分类列出所有重要的 PR 和变更。同时将此前标记为"latest release"的 1.8.1 移除该标记（在提交 2048 中处理 issue 模板的对应更新）。

## 修改详情

### `site/docs/releases.md` (修改, +75/-2 lines)

**修改目的**：添加 Iceberg 1.9.0 的发布说明。

**工作逻辑**：
在 releases.md 中新增 `### 1.9.0 release` 段落，包含以下分类的变更列表：

- **Deprecation / End of Support**：移除 Spark 3.3 支持、移除 Hadoop 2
- **Spec**：支持 geo 类型、允许带行谱系的 Equality Deletes、`current-snapshot-id` 实现说明、行谱系要求更新、Variant 上下界说明、V3 中 source-id 使用等
- **API**：UpdateSchema 支持默认值、Variant 移至 API、Variant#toString 实现
- **Core**：分区统计读写器、Auth Manager API、InternalData 读写构建器、V3 表行谱系、FileRewritePlanner、Variant 类型支持、纳秒时间戳和 unknown 类型支持、JdbcCatalog FileIO 关闭、批量删除等大量改进
- **Parquet**：Variant 读写器实现、Variant metrics、unknown 和纳秒时间戳支持
- **ORC**：纳秒时间戳、variant 和 unknown 支持
- **AWS**：S3 分析加速库集成
- **Spark**：V2 deletes 重写为 V3 DVs、悬空 DV 检测、`_row_id` 和 `_last_updated_sequence_number` 读取器
- **Kafka Connect**：Debezium 和 AWS DMS 的 SMT、事务 ID 前缀配置、ICR 模式数据处理
- **Flink**：Flink catalog 中 create table like 支持、Avro 和 Parquet timestamp(9)/unknown/defaults 支持、Flink SQL 窗口 source watermark
- **Dependencies**：Netty 4.2.0.Final、Nessie 0.103.3、Parquet 1.15.1（修复 CVE-2025-30065）、SQLite JDBC 3.49.1.0、Jackson 2.18.3

同时修改了原有 1.8.x 版本说明的位置（从"latest release"标记移除）。

## 总结

本提交为 Apache Iceberg 1.9.0 版本添加完整的发布说明文档，按 Spec、API、Core、Parquet、ORC、AWS、Spark、Kafka Connect、Flink、Dependencies 等模块分类列出了该版本包含的所有重要变更和新功能。属于纯文档修改，共 75 行新增。
