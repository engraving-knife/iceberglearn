# 提交 3340：Core, Spark: Rename read.identifier-fields.rely to identifier-fields.rely (#15495)

## 提交信息

- **序号**：3340 / 4088
- **哈希**：64e0211e424d2c1c4b3e2153c5a54564d80f95b6
- **短哈希**：64e0211e4
- **日期**：2026-03-02 15:21:33 -0600
- **作者**：Russell Spitzer
- **提交说明**：Core, Spark: Rename read.identifier-fields.rely to identifier-fields.rely (#15495)
- **PR/Issue**：#15495

## 总体目的

本提交将上一提交（#15372，序号 3337）引入的表属性 `read.identifier-fields.rely` 重命名为 `identifier-fields.rely`，去掉 `read.` 前缀，使命名更准确地反映该属性的语义。

背景是：序号 3337 的提交新增了让用户声明"可依赖 identifier fields 作为主键"的表属性，初始命名为 `read.identifier-fields.rely`，沿用了 Iceberg 表属性中以 `read.` 为前缀表示读取相关配置的惯例（如 `read.split.target-size`、`read.delete-planning-mode` 等）。然而该属性本质上是对表 schema 中 identifier fields 唯一性的一个声明，影响的是查询优化器是否可将其作为主键依赖——这并非单纯的"读取行为"配置，而是一个更广义的表级声明属性。`read.` 前缀会误导用户以为它只影响读取路径，因此去掉前缀更恰当。值得注意的是，3337 中对应的 Spark 会话配置 `spark.sql.iceberg.identifier-fields-rely` 本就没有 `read` 字样，此次重命名也让表属性与会话配置的命名风格对齐。

由于该特性刚在 3337 引入、尚未发布，此时重命名不会破坏任何已上线的使用，是安全的。

## 如何达成设计目的

改动是纯机械式重命名：在 `TableProperties` 中把常量名 `READ_IDENTIFIER_FIELDS_RELY` 改为 `IDENTIFIER_FIELDS_RELY`、字符串值从 `"read.identifier-fields.rely"` 改为 `"identifier-fields.rely"`，默认值常量同步改名；在 `SparkReadConf` 与测试类中更新对旧常量名的引用。无任何逻辑变更。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (+2/-2 lines)

**修改目的**：重命名表属性键名与对应常量，去掉 `read.` 前缀。

**工作逻辑**：
将 `public static final String READ_IDENTIFIER_FIELDS_RELY = "read.identifier-fields.rely"` 改为 `public static final String IDENTIFIER_FIELDS_RELY = "identifier-fields.rely"`；将 `READ_IDENTIFIER_FIELDS_RELY_DEFAULT` 改为 `IDENTIFIER_FIELDS_RELY_DEFAULT`（值仍为 `false`）。Javadoc 不变。这是对外暴露的属性键名变更，决定用户在 `ALTER TABLE SET TBLPROPERTIES` 时使用的字符串。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkReadConf.java` (+2/-2 lines)

**修改目的**：更新读取配置中对表属性常量的引用。

**工作逻辑**：
在 `identifierFieldsRely()` 方法中，将 `.tableProperty(TableProperties.READ_IDENTIFIER_FIELDS_RELY)` 改为 `.tableProperty(TableProperties.IDENTIFIER_FIELDS_RELY)`，将 `.defaultValue(TableProperties.READ_IDENTIFIER_FIELDS_RELY_DEFAULT)` 改为 `.defaultValue(TableProperties.IDENTIFIER_FIELDS_RELY_DEFAULT)`。会话配置引用 `SparkSQLProperties.IDENTIFIER_FIELDS_RELY` 不变（本就无 `READ` 前缀）。解析逻辑与优先级不变。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkTable.java` (+4/-4 lines)

**修改目的**：更新测试中对旧常量名的引用。

**工作逻辑**：
将四个 `ALTER TABLE SET TBLPROPERTIES` 语句中引用的 `TableProperties.READ_IDENTIFIER_FIELDS_RELY` 统一替换为 `TableProperties.IDENTIFIER_FIELDS_RELY`。这些出现在 `testNoIdentifierFieldsRelyByDefault`、`testIdentifierFieldsRelyViaTableProperty`（启用与禁用两处）、`testIdentifierFieldsRelyViaSessionConf` 中，覆盖了表属性的所有使用点，确保测试与新属性名一致。

## 总结

本提交是对序号 3337 引入的 identifier fields rely 特性的命名修正，将表属性 `read.identifier-fields.rely` 重命名为 `identifier-fields.rely`（常量名同步从 `READ_IDENTIFIER_FIELDS_RELY` 改为 `IDENTIFIER_FIELDS_RELY`），去掉了暗示"仅读取相关"的 `read.` 前缀，使命名更准确地体现其作为表级主键依赖声明的语义，并与无 `read` 前缀的会话配置风格对齐。由于特性尚未发布，重命名无兼容性风险。
