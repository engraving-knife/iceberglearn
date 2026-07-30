# 提交 2325：Spark: Throw unsupported for ADD COLUMN with default value (#13464)

## 提交信息

- **序号**：2325 / 4088
- **哈希**：401ab27e1ea7c959119bb24f33ece3182c7391f8
- **短哈希**：401ab27e1
- **日期**：2025-07-07 16:04:57 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark: Throw unsupported for ADD COLUMN with default value (#13464)
- **PR/Issue**：#13464

## 总体目的

这个提交在 Spark 的 Iceberg 集成中，对 `ALTER TABLE ADD COLUMN ... DEFAULT` 语句（添加带默认值的列）抛出明确的 `UnsupportedOperationException`，而非让操作静默通过或产生不一致行为。

Spark SQL 支持 `ALTER TABLE ADD COLUMN` 时指定默认值（`DEFAULT` 子句），但 Iceberg 的 Spark 集成尚未实现此功能。在此提交之前，如果用户执行 `ALTER TABLE ADD COLUMN col INT DEFAULT 123`，操作可能不会报错但默认值不会被正确处理，导致数据不一致。此提交通过主动抛出异常，明确告知用户该功能不支持，避免潜在的数据正确性问题。

该修改覆盖了 Spark 3.4、3.5 和 4.0 三个版本模块。

## 如何达成设计目的

在 `Spark3Util` 的 `applyAddColumnFields` 方法中，检查 `AddFieldSchema.defaultValue()` 是否为 null。如果不为 null（即用户指定了默认值），则抛出 `UnsupportedOperationException`，错误消息明确说明 Spark 中设置默认值当前不支持。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/Spark3Util.java` (+7/-0 lines, 各版本相同)

**修改目的**：在添加列操作中检查默认值并抛出异常。

**工作逻辑**：在已有的 nullability 检查之后，新增 `add.defaultValue() != null` 检查。如果检测到默认值，抛出 `UnsupportedOperationException`，消息格式为 "Cannot add column {col} since setting default values in Spark is currently unsupported"。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestAlterTable.java` (+10/-0 lines, 3.4 和 3.5 版本相同; 4.0 版本 +9/-0)

**修改目的**：新增测试验证默认值不被支持。

**工作逻辑**：新增 `testAddColumnWithDefaultValuesUnsupported` 测试方法，执行 `ALTER TABLE %s ADD COLUMN col_with_default int DEFAULT 123` 并验证抛出 `UnsupportedOperationException`，错误消息以 "Cannot add column col_with_default" 开头。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/CatalogTestBase.java` (+12/-12 lines, 仅 3.4 和 3.5)

**修改目的**：调整测试 catalog 配置顺序。

**工作逻辑**：将 SPARK catalog 配置从数组开头移到末尾（在 REST catalog 之后），调整 catalog 参数化顺序以适应新的测试需求。

## 总结

这个提交是一个防御性编程改进，在 Spark 中添加列时检测默认值并主动抛出不支持异常，避免用户误以为默认值已生效。变更覆盖 Spark 3.4/3.5/4.0 三个版本，并附有针对性测试。
