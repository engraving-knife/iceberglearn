# 提交分析：Docs: Add Spark SQL Configurations

## 提交信息

| 字段 | 值 |
|------|-----|
| 提交号 | 2110 |
| 短哈希 | `fa1d86060` |
| 完整哈希 | `fa1d860603235c610c78a83b7f4cb5ef986f2b3a` |
| 作者 | Guy Khazma |
| 邮箱 | 33684427+guykhazma@users.noreply.github.com |
| 日期 | 2025-05-12 15:43:36 2025 -0400 |
| 提交信息 | Docs: Add Spark SQL Configurations (#12931) |

## 总体目的

本提交为 Iceberg 的 Spark 配置文档添加了 Spark SQL 配置选项的完整说明。此前文档中缺少对 Spark SQL 级别配置项的系统性描述，用户难以了解所有可用的 `spark.sql.iceberg.*` 配置选项及其默认值。本提交通过添加配置优先级说明和完整的配置选项表格来填补这一文档空白。

## 设计目的的实现方式

1. **添加配置优先级说明**：在"Runtime configuration"章节下新增"Precedence of Configuration Settings"小节，明确说明配置的优先级顺序：DataSource API Read/Write Options > Spark Session Configuration > Table Properties > Default Value。

2. **添加 Spark SQL Options 表格**：新增"Spark SQL Options"小节，包含一个完整的配置选项表格，列出所有 `spark.sql.iceberg.*` 配置项、默认值和描述。同时提供了一个 Scala 代码示例展示如何在 SparkSession 中设置这些配置。

## 修改详情

### 修改 `spark-configuration.md`

**文件**：`docs/docs/spark-configuration.md`

**修改内容**：在"Runtime configuration"章节下新增两个小节，共 55 行。

#### 新增"Precedence of Configuration Settings"小节

说明了 Iceberg 配置的四级优先级：
1. **DataSource API Read/Write Options**：通过 `.option(...)` 显式传递的读写选项
2. **Spark Session Configuration**：通过 `spark.conf.set(...)`、`spark-defaults.conf` 或 `--conf` 设置的全局配置
3. **Table Properties**：通过 `ALTER TABLE SET TBLPROPERTIES` 在表级别定义的属性
4. **Default Value**：默认值

如果某一层级未定义配置，则使用下一层级作为回退。

#### 新增"Spark SQL Options"小节

包含以下内容：

1. **说明文字**：介绍 Iceberg 支持通过 Spark SQL 配置选项设置全局行为，可通过 `spark.conf`、`SparkSession` 设置或 Spark submit 参数指定。

2. **Scala 代码示例**：展示如何在 SparkSession.builder() 中设置 `spark.sql.iceberg.vectorization.enabled` 为 `false` 来禁用向量化读取。

3. **配置选项表格**：列出 23 个配置项，包括：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `spark.sql.iceberg.vectorization.enabled` | Table default | 启用数据文件的向量化读取 |
| `spark.sql.iceberg.parquet.reader-type` | ICEBERG | 设置 Parquet 读取器实现（ICEBERG, COMET） |
| `spark.sql.iceberg.check-nullability` | true | 验证写入 schema 的可空性是否匹配表的可空性 |
| `spark.sql.iceberg.check-ordering` | true | 验证写入 schema 列顺序是否匹配表 schema 顺序 |
| `spark.sql.iceberg.planning.preserve-data-grouping` | false | 为存储分区连接保留分区分组 |
| `spark.sql.iceberg.aggregate-push-down.enabled` | true | 启用聚合函数下推（MAX, MIN, COUNT） |
| `spark.sql.iceberg.distribution-mode` | 参见 Spark Writes | 控制写入时的分布策略 |
| `spark.wap.id` | null | Write-Audit-Publish 快照暂存 ID |
| `spark.wap.branch` | null | WAP 分支名称 |
| `spark.sql.iceberg.compression-codec` | Table default | 写入压缩编解码器 |
| `spark.sql.iceberg.compression-level` | Table default | Parquet/Avro 压缩级别 |
| `spark.sql.iceberg.compression-strategy` | Table default | ORC 压缩策略 |
| `spark.sql.iceberg.data-planning-mode` | AUTO | 数据文件扫描计划模式 |
| `spark.sql.iceberg.delete-planning-mode` | AUTO | 删除文件扫描计划模式 |
| `spark.sql.iceberg.advisory-partition-size` | Table default | 写入建议分区大小 |
| `spark.sql.iceberg.locality.enabled` | false | 报告本地性信息 |
| `spark.sql.iceberg.executor-cache.enabled` | true | 启用执行器端缓存 |
| `spark.sql.iceberg.executor-cache.timeout` | 10 | 执行器缓存超时（分钟） |
| `spark.sql.iceberg.executor-cache.max-entry-size` | 67108864 (64MB) | 单个缓存条目最大大小 |
| `spark.sql.iceberg.executor-cache.max-total-size` | 134217728 (128MB) | 执行器缓存总大小上限 |
| `spark.sql.iceberg.executor-cache.locality.enabled` | false | 启用本地性感知的执行器缓存 |
| `spark.sql.iceberg.merge-schema` | false | 启用 schema 合并 |
| `spark.sql.iceberg.report-column-stats` | true | 向 Spark CBO 报告 Puffin 表统计信息 |

**目的**：为用户提供完整的 Spark SQL 配置选项参考文档，使用户能够方便地查找和理解所有可用的配置项、默认值和用途。配置优先级说明帮助用户理解不同配置层级之间的覆盖关系。

## 总结

本提交是一个纯文档提交，为 Iceberg 的 Spark 配置文档新增了 55 行内容，包括：
1. **配置优先级说明**：明确了四级配置优先级（DataSource Options > Spark Session > Table Properties > Default）
2. **Spark SQL Options 表格**：列出了 23 个 `spark.sql.iceberg.*` 配置项及其默认值和描述
3. **代码示例**：提供了在 SparkSession 中设置配置的 Scala 示例

这些文档填补了 Spark SQL 配置选项文档的空白，使用户能够更容易地理解和配置 Iceberg 在 Spark 中的行为。
