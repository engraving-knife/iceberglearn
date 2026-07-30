# 提交 3492：Spark 4.1: Control merge schema evolution by table property (#15825)

## 提交信息

- **序号**：3492 / 4088
- **哈希**：05d7ece42acccc041f711dcac0dbb03a3c127396
- **短哈希**：05d7ece42a
- **日期**：2026-04-01 10:19:04 -0600
- **作者**：Szehon Ho
- **提交说明**：Spark 4.1: Control merge schema evolution by table property (#15825)
- **PR/Issue**：#15825

## 总体目的

新增表属性 `write.spark.auto-schema-evolution.enabled`（默认 true），控制是否向 Spark 报告 `AUTOMATIC_SCHEMA_EVOLUTION` 能力。当设为 false 时，Spark 的 `MERGE WITH SCHEMA EVOLUTION` 不再自动演进目标表 schema。这为用户提供了对 schema 演进行为的细粒度控制。

## 如何达成设计目的

1. 在 `TableProperties` 中新增 `SPARK_WRITE_AUTO_SCHEMA_EVOLUTION` 属性，默认值为 true。
2. 重构 `SparkTable` 的 capabilities 计算逻辑：将 `AUTOMATIC_SCHEMA_EVOLUTION` 从基础能力集中移出，改为根据表属性条件添加。
3. 新增 `computeCapabilities` 方法，统一计算能力集（基础 + 条件性 AUTO_SCHEMA_EVOLUTION + 条件性 ACCEPT_ANY_SCHEMA）。
4. 添加测试验证禁用属性后 MERGE WITH SCHEMA EVOLUTION 不演进 schema。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+4 lines)

**修改目的**：新增表属性定义。

**工作逻辑**：
```java
public static final String SPARK_WRITE_AUTO_SCHEMA_EVOLUTION =
    "write.spark.auto-schema-evolution.enabled";
public static final boolean SPARK_WRITE_AUTO_SCHEMA_EVOLUTION_DEFAULT = true;
```

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+24/-8 lines)

**修改目的**：重构 capabilities 计算，支持条件性 schema 演进能力。

**工作逻辑**：
- 将 `CAPABILITIES` 重命名为 `BASE_CAPABILITIES`，移除 `AUTOMATIC_SCHEMA_EVOLUTION`。
- 移除 `CAPABILITIES_WITH_ACCEPT_ANY_SCHEMA` 静态集合。
- 构造函数中 `this.capabilities = computeCapabilities(table)`。
- 新增 `computeCapabilities(Table)` 方法：以 BASE_CAPABILITIES 为基础，根据 `autoSchemaEvolution(table)` 添加 AUTOMATIC_SCHEMA_EVOLUTION，根据 `acceptAnySchema(table)` 添加 ACCEPT_ANY_SCHEMA。
- 新增 `autoSchemaEvolution(Table)` 方法读取表属性。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMergeSchemaEvolution.java` (+45 lines)

**修改目的**：测试禁用 auto-schema-evolution 后的行为。

**工作逻辑**：
- `testMergeWithSchemaEvolutionDisabledByTableProperty`：
  1. 创建表 (id INT, dep STRING)。
  2. ALTER TABLE SET TBLPROPERTIES 设 `write.spark.auto-schema-evolution.enabled = false`。
  3. 创建源视图 (id INT, dep STRING, salary INT)。
  4. 执行 MERGE WITH SCHEMA EVOLUTION。
  5. 验证表仍只有 2 列（salary 未添加），数据正确（源数据的 salary 被忽略）。

## 总结

功能增强提交，新增 `write.spark.auto-schema-evolution.enabled` 表属性控制 Spark MERGE 的 schema 演进能力。通过重构 SparkTable 的 capabilities 计算，将 AUTOMATIC_SCHEMA_EVOLUTION 从无条件基础能力改为条件性能力。默认保持 true 向后兼容，设为 false 可禁用 schema 演进。
