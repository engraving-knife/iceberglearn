# 提交 1871：Kafka: Suppress warnings around java.util.Date usage / fix var names (#12561)

## 提交信息

- **序号**：1871 / 4088
- **哈希**：731ff733265dc8f3036e7b6cd66fd64a8f32170f
- **短哈希**：731ff7332
- **日期**：2025-03-18 05:33:37 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Kafka: Suppress warnings around java.util.Date usage / fix var names (#12561)
- **PR/Issue**：#12561

## 总体目的

本提交对 Kafka Connect transforms 模块做代码清理，主要解决两个问题：一是抑制静态分析工具对 `java.util.Date` 使用的告警，二是修正不符合 Java 命名规范的局部变量名。

Iceberg 项目引入了 errorprone / checkstyle 等静态检查，会标记 `java.util.Date` 的使用（因为它是可变且过时的 API，推荐使用 `java.time` 系列）。但 Debezium 和 MongoDB 的转换逻辑中确实需要使用 `java.util.Date`（与 Kafka Connect 的 Struct/Schema API 兼容），因此合理做法是抑制这些告警而非改用新 API。

同时，`MongoDataConverter` 中存在多个不符合驼峰命名规范的变量名（如 `keyvalueforStruct`、`keyValuesforSchema`、`jswithscope`），本提交统一修正为规范命名（`keyValueForStruct`、`keyValuesForSchema`、`jsWithScope`）。

## 如何达成设计目的

1. **抑制 Date 告警**：在三个使用 `java.util.Date` 的方法上添加 `@SuppressWarnings("JavaUtilDate")` 注解：
   - `DebeziumTransform.applyWithSchema` 方法
   - `MongoDataConverter.convertFieldValue(Entry, Struct, Schema)` 方法
   - `MongoDataConverter.convertFieldValue(Schema, BsonType, BsonValue, List)` 重载方法（合并到已有的 `checkstyle:CyclomaticComplexity` 抑制注解中）

2. **修正变量名**：在 `MongoDataConverter` 中将以下变量重命名：
   - 方法参数 `keyvalueforStruct` → `keyValueForStruct`（两个方法）
   - 方法参数 `keyValuesforSchema` → `keyValuesForSchema`
   - 局部变量 `jswithscope` → `jsWithScope`
   - 所有引用这些变量的位置同步更新。

## 修改详情

### `kafka-connect/kafka-connect-transforms/src/main/java/org/apache/iceberg/connect/transforms/DebeziumTransform.java` (修改, +1/-0 lines)

**修改目的**：抑制 `applyWithSchema` 方法中 `java.util.Date` 使用告警。

**工作逻辑**：在方法上添加 `@SuppressWarnings("JavaUtilDate")` 注解。该方法处理 Debezium 记录时需要操作 Struct，间接涉及 Date 类型。

### `kafka-connect/kafka-connect-transforms/src/main/java/org/debezium/connector/mongodb/transforms/MongoDataConverter.java` (修改, +42/-41 lines)

**修改目的**：抑制 Date 告警并修正变量命名。

**工作逻辑**：
- 在 `convertFieldValue(Entry, Struct, Schema)` 方法上添加 `@SuppressWarnings("JavaUtilDate")`，因为该方法在 `DATE_TIME` 和 `TIMESTAMP` 分支中构造 `new Date(...)`。
- 在 `convertFieldValue(Schema, BsonType, BsonValue, List)` 重载方法上将原 `@SuppressWarnings("checkstyle:CyclomaticComplexity")` 扩展为 `@SuppressWarnings({"checkstyle:CyclomaticComplexity", "JavaUtilDate"})`，因为该方法也使用 Date。
- 将 `convertRecord` 和 `convertFieldValue(Entry, Struct, Schema)` 的参数 `keyvalueforStruct` 重命名为 `keyValueForStruct`，并更新方法体内所有引用（约 20 处，覆盖 STRING、OBJECT_ID、DOUBLE、BINARY、INT32、INT64、BOOLEAN、DATE_TIME、JAVASCRIPT、JAVASCRIPT_WITH_SCOPE、REGULAR_EXPRESSION、TIMESTAMP、DECIMAL128、DOCUMENT、ARRAY 等分支）。
- 将 `addFieldSchema` 的参数 `keyValuesforSchema` 重命名为 `keyValuesForSchema`，更新所有引用。
- 将 `addFieldSchema` 中 JAVASCRIPT_WITH_SCOPE 分支的局部变量 `jswithscope` 重命名为 `jsWithScope`，更新引用。

## 总结

本提交是 Kafka Connect transforms 模块的纯代码清理：通过 `@SuppressWarnings("JavaUtilDate")` 抑制无法避免的 `java.util.Date` 使用告警（因 Kafka Connect API 约束），并将多个不符合驼峰规范的变量名统一修正。无功能变更。
