# 提交 2804：Spark 4.0: Add schema conversion support for default values (#14407)

## 提交信息

- **序号**：2804 / 4088
- **哈希**：a99dc4f2faa3b52140f3f1432187f5e575bb32bd
- **短哈希**：a99dc4f2f
- **日期**：2025-10-29 13:02:52 -0600
- **作者**：Drew Gallardo
- **提交说明**：Spark 4.0: Add schema conversion support for default values (#14407)
- **PR/Issue**：#14407

## 总体目的

本提交为 Spark 4.0 的 Iceberg 集成添加 schema 转换时默认值（default values）的支持，将 Iceberg 的 `writeDefault` 和 `initialDefault` 转换为 Spark 的列默认值元数据。

Iceberg 规范支持两种默认值：
- `writeDefault`（写入默认值）：在 INSERT 操作中，如果未提供该列的值，使用此默认值。
- `initialDefault`（初始默认值）：用于已有行中新添加列的回填值。

Spark 4.0 引入了列默认值支持，通过 `StructField` 的 `CURRENT_DEFAULT` 和 `EXISTS_DEFAULT` 元数据来表示。`CURRENT_DEFAULT` 对应 Iceberg 的 `writeDefault`（INSERT 时使用），`EXISTS_DEFAULT` 对应 `initialDefault`（已有行的回填值）。

之前 Iceberg 到 Spark 的 schema 转换（`TypeToSparkType`）不处理默认值，导致 Spark 端无法感知 Iceberg 表的默认值定义。本提交填补了这一空白。

## 如何达成设计目的

在 `TypeToSparkType` 的 schema 访问器中，当访问结构体字段时，检查 Iceberg 字段是否有 `writeDefault` 和 `initialDefault`，如果有则通过 Spark 的 `Literal$.create()` 方法将其转换为 Spark SQL 字面量表达式（SQL 字符串形式），并设置到 `StructField` 的默认值元数据中。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/TypeToSparkType.java` (+17/-0 lines)

**修改目的**：在 Iceberg 到 Spark 的类型转换中添加默认值支持。

**工作逻辑**：在 `struct` 方法中，为每个 `StructField` 检查并设置默认值：
- 如果 `field.writeDefault()` 不为 null，通过 `SparkUtil.internalToSpark()` 将 Iceberg 内部值转换为 Spark 值，再用 `Literal$.MODULE$.create(value, type).sql()` 生成 SQL 字面量字符串，通过 `withCurrentDefaultValue()` 设置到 Spark 字段。
- 如果 `field.initialDefault()` 不为 null，同样转换后通过 `withExistenceDefaultValue()` 设置。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/TestSparkSchemaUtil.java` (+189/-0 lines)

**修改目的**：测试 schema 转换时默认值的正确性。

**工作逻辑**：
- `testSchemaConversionWithOnlyWriteDefault`：验证只有 writeDefault 时，Spark 元数据中只有 `CURRENT_DEFAULT`。
- `testSchemaConversionWithOnlyInitialDefault`：验证只有 initialDefault 时，Spark 元数据中只有 `EXISTS_DEFAULT`。
- `testSchemaConversionWithDefaultsForPrimitiveTypes`：参数化测试，覆盖 15 种基本类型（Integer、String、UUID、Boolean、Long、Float、Double、Decimal、Date、Timestamp/TimestampNTZ、Binary、Fixed）的默认值转换，验证生成的 SQL 字面量格式正确（如字符串用单引号、浮点用 CAST、日期用 DATE '...'、二进制用 X'...' 等）。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkDefaultValues.java` (+234/-0 lines, new file)

**修改目的**：测试 Spark SQL 中 Iceberg 默认值的端到端行为。

**工作逻辑**：
- `testWriteDefaultWithSparkDefaultKeyword`：创建带默认值的表，使用 `INSERT INTO ... VALUES (1, DEFAULT, DEFAULT, DEFAULT)` 验证 DEFAULT 关键字触发写入默认值。
- `testWriteDefaultWithDefaultKeywordAndReorderedSchema`：验证列顺序不同时默认值仍正确应用。
- `testBulkInsertWithDefaults`：验证批量插入时多行使用 DEFAULT。
- `testCreateTableWithDefaultsUnsupported`：验证 Spark DDL 中使用 DEFAULT 子句创建表暂不支持（抛出 AnalysisException）。
- `testAlterTableAddColumnWithDefaultUnsupported`：验证 ALTER TABLE ADD COLUMN 带 DEFAULT 暂不支持。
- `testPartialInsertUnsupported`：验证部分列 INSERT（省略有默认值的列）在 DSV2 中不支持。
- `testSchemaEvolutionWithDefaultValueChanges`：验证 schema 演进时新添加带默认值的列，已有行使用 initialDefault 回填，新行使用 writeDefault。

## 总结

本提交为 Spark 4.0 的 Iceberg 集成添加了默认值 schema 转换支持。通过在 `TypeToSparkType` 中将 Iceberg 的 `writeDefault`/`initialDefault` 转换为 Spark 的 `CURRENT_DEFAULT`/`EXISTS_DEFAULT` 元数据，使 Spark 能感知 Iceberg 表的默认值定义。测试覆盖了 15 种基本类型的转换正确性以及端到端的 INSERT DEFAULT 行为，同时也明确了当前的限制（DDL 不支持 DEFAULT 子句、部分列 INSERT 不支持）。
