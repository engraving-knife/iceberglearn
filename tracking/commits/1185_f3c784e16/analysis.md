# 提交 1185：Spark: Added merge schema as spark configuration (#9640)

## 提交信息

- **序号**：1185 / 4088
- **哈希**：f3c784e166566b494f988b732bf854a9bfae4e64
- **短哈希**：f3c784e16
- **日期**：2024-09-26（Thu Sep 26 03:36:21 2024 +0530）
- **作者**：aleenamg21-1 <155471412+aleenamg21-1@users.noreply.github.com>
- **提交说明**：Spark: Added merge schema as spark configuration (#9640)
- **PR/Issue**：#9640

## 总体目的

Iceberg Spark 集成支持在写入时"合并 schema"（merge schema）：当 DataFrame 的 schema 与目标 Iceberg 表的 schema 不完全一致但兼容（例如新增列）时，写入操作可以自动把新列合并进表 schema，而不是抛出 schema 不匹配错误。该能力此前只能通过**每次写入时的 DataFrameWriter 选项**（`mergeSchema` / `merge-schema`）来开启，例如 `df.writeTo("t").option("merge-schema", "true").append()`。

这种"逐次写入指定 option"的方式对用户而言不够便利——在大批量 ETL 任务中，用户更希望通过 Spark 的 `spark.sql.*` 配置项在会话级别统一切换默认行为，避免每个写入调用都要重复传 option。本提交的目的是新增 SparkSQL 配置项 `spark.sql.iceberg.merge-schema`，让用户可在会话级（`spark.conf.set`）控制 merge schema 默认值，与 Iceberg 已有的 `spark.sql.iceberg.*` 系列配置风格保持一致。

## 如何达成设计目的

1. 在 `SparkSQLProperties` 中新增常量 `MERGE_SCHEMA = "spark.sql.iceberg.merge-schema"` 与默认值 `MERGE_SCHEMA_DEFAULT = false`，与同文件中其它 `spark.sql.iceberg.*` 配置项风格一致。
2. 修改 `SparkWriteConf.mergeSchema()` 方法的解析链：除了原有的两个 option 来源（`merge-schema` 与 `mergeSchema`）外，新增 `.sessionConf(SparkSQLProperties.MERGE_SCHEMA)`，使其能从 SparkSession 配置中读取；同时把默认值来源从 `SparkWriteOptions.MERGE_SCHEMA_DEFAULT` 改为 `SparkSQLProperties.MERGE_SCHEMA_DEFAULT`。
3. 删除 `SparkWriteOptions.MERGE_SCHEMA_DEFAULT` 常量，避免默认值在两处定义造成漂移（单一真源原则）。
4. 新增测试 `testMergeSchemaSparkConfiguration` 验证：通过 `spark.conf().set("spark.sql.iceberg.merge-schema", "true")` 设置后，写入一个含额外列的 DataFrame 时 schema 会自动合并。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSQLProperties.java`

**修改目的**：声明新的 SparkSQL 配置项。

**工作逻辑**：新增两行常量：
```java
// Controls whether to merge schema during write operation
public static final String MERGE_SCHEMA = "spark.sql.iceberg.merge-schema";
public static final boolean MERGE_SCHEMA_DEFAULT = false;
```
默认值为 `false`，与原 `SparkWriteOptions.MERGE_SCHEMA_DEFAULT` 保持一致，保证旧行为不变。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkWriteConf.java`

**修改目的**：让 `mergeSchema()` 解析时优先级包含 session 配置。

**工作逻辑**：原解析链为：
```java
.option(SparkWriteOptions.MERGE_SCHEMA)
.option(SparkWriteOptions.SPARK_MERGE_SCHEMA)
.defaultValue(SparkWriteOptions.MERGE_SCHEMA_DEFAULT)
```
改为：
```java
.option(SparkWriteOptions.MERGE_SCHEMA)
.option(SparkWriteOptions.SPARK_MERGE_SCHEMA)
.sessionConf(SparkSQLProperties.MERGE_SCHEMA)
.defaultValue(SparkSQLProperties.MERGE_SCHEMA_DEFAULT)
```
按 Iceberg 的 `ConfigConf` 解析约定，调用链中前面的来源优先级更高，因此 DataFrameWriter option 仍优先于 session 配置；只有当 option 未指定时，才会回落到 `spark.sql.iceberg.merge-schema` 的会话配置，最终再回落到默认值 `false`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkWriteOptions.java`

**修改目的**：移除冗余的默认值常量。

**工作逻辑**：删除 `public static final boolean MERGE_SCHEMA_DEFAULT = false;` 一行。该默认值现由 `SparkSQLProperties.MERGE_SCHEMA_DEFAULT` 单一持有，避免两处定义漂移。`MERGE_SCHEMA` 与 `SPARK_MERGE_SCHEMA` 两个 option 名常量保留不变。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataFrameWriterV2.java`

**修改目的**：验证 session 配置生效。

**工作逻辑**：新增 `testMergeSchemaSparkConfiguration` 测试：
1. 先把表的 `write.spark.accept-any-schema` 设为 `true`（Iceberg 要求 merge schema 时表属性开启 accept-any-schema）。
2. 写入 2 列 DataFrame（`id`、`data`），断言表中有 2 列数据。
3. 通过 `spark.conf().set("spark.sql.iceberg.merge-schema", "true")` 在会话级别开启 merge schema（**注意：本次写入调用没有传 option**，验证的就是会话配置生效）。
4. 写入 3 列 DataFrame（`id`、`data`、`salary`），断言表中已有 4 行，旧数据 `salary` 为 null、新数据 `salary` 为实际值。这证明 schema 已自动合并新增 `salary` 列。

## 小结

- **成效**：用户现可通过 `spark.sql.iceberg.merge-schema` 在 SparkSession 级别统一控制 merge schema 默认值，无需逐次写入传 option。优先级链保持 DataFrameWriter option > session 配置 > 默认 false，旧行为完全不变。
- **影响范围**：Spark v3.5 集成的 3 个 Java 源文件 + 1 个测试文件，约 41 行新增/2 行删除。属于配置项扩展，对运行时行为影响小且向后兼容。
- **回迁到 1.4.x 的注意事项**：这是一个面向用户体验的便利性改进，**回迁价值中等**。如果 1.4.x 仍维护 Spark v3.5 集成且用户有类似诉求（希望会话级控制 merge schema），可以回迁。回迁时需要注意：(1) 1.4.x 中 `SparkWriteConf.mergeSchema()` 的现有解析链是否与本提交前状态一致，若已有本地改动需谨慎合并；(2) 删除 `SparkWriteOptions.MERGE_SCHEMA_DEFAULT` 后，需检查 1.4.x 中是否有其他位置引用该常量（例如文档、其他模块），若有需同步调整；(3) 该改动仅影响 Spark v3.5 模块，1.4.x 若同时维护 v3.3/v3.4 模块，可考虑一致性同步回迁，但非强制。从语义上看，回迁是安全的，不破坏任何已有 API 与行为。
