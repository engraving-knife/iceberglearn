# 提交 3771：Spark: Backport Add _row_id and _last_updated_sequence_number reader in Orc to support lineage (#16534)

## 提交信息

- **序号**：3771 / 4088
- **哈希**：fca74a08c6e0a08646647dcbda9a63fe90ee88d5
- **短哈希**：fca74a08c
- **日期**：2026-05-22 11:43:23 -0700
- **作者**：GuoYu
- **提交说明**：Spark: Backport Add _row_id and _last_updated_sequence_number raeder in Orc to support lineage (#16534)
- **PR/Issue**：#16534

## 总体目的

这个提交是将 ORC 格式读取器中行级血缘（lineage）支持的功能回移植到 Spark v3.5 和 v4.0 版本。该功能通过在 ORC 读取器中传递 `TypeDescription`（ORC 的 schema 描述）给 `StructReader`，使其能够读取 `_row_id` 和 `_last_updated_sequence_number` 等血缘相关元数据列，从而支持行级操作（如 UPDATE、DELETE、MERGE）中的血缘追踪。

原始修复已经合入主分支，但 Spark v3.5 和 v4.0 两个维护版本也需要这个修改。

## 如何达成设计目的

修改 `SparkOrcValueReaders.struct()` 方法和 `StructReader` 构造函数，新增 `TypeDescription record` 参数，将其传递给父类构造函数。父类（Iceberg core 中的 ORC `StructReader`）利用 `TypeDescription` 来识别和读取 ORC 文件中的额外列（如 `_row_id` 和 `_last_updated_sequence_number`）。同时修改 `SparkOrcReader` 中的调用，将 `record` 参数传入。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkOrcReader.java` (+1/-1 lines)

**修改目的**：将 ORC `TypeDescription` 传递给 struct reader 创建方法。

**工作逻辑**：
```java
// 修改前
return SparkOrcValueReaders.struct(fields, expected, idToConstant);
// 修改后
return SparkOrcValueReaders.struct(record, fields, expected, idToConstant);
```
将 `record`（ORC 的 `TypeDescription`）作为第一个参数传入，使 struct reader 能够访问 ORC 文件的完整 schema 信息。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/SparkOrcValueReaders.java` (+11/-4 lines)

**修改目的**：在 struct reader 中接收并传递 `TypeDescription`。

**工作逻辑**：
1. 新增 `import org.apache.orc.TypeDescription;` 导入。
2. `struct()` 方法新增 `TypeDescription record` 参数，传入 `StructReader` 构造函数。
3. `StructReader` 构造函数新增 `TypeDescription record` 参数，通过 `super(record, readers, struct, idToConstant)` 传递给父类。父类利用这个 `TypeDescription` 来定位和读取 ORC 文件中的血缘元数据列。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkOrcReader.java` (+1/-1 lines)
### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkOrcValueReaders.java` (+11/-4 lines)

**修改目的**：对 Spark v4.0 应用相同的修改。

**工作逻辑**：与 v3.5 完全相同的修改。

### `spark/v3.5/spark-extensions/src/test/java/.../TestRowLevelOperationsWithLineage.java` (+12/-0 lines)
### `spark/v4.0/spark-extensions/src/test/java/.../TestRowLevelOperationsWithLineage.java` (+12/-0 lines)

**修改目的**：为 v3.5 和 v4.0 添加血缘功能的测试用例。

**工作逻辑**：新增测试用例验证行级操作中的血缘追踪功能。

## 总结

这个提交是 ORC 行级血缘读取支持的 backport，通过在 Spark ORC 读取器中传递 `TypeDescription` 给 `StructReader`，使其能够读取 `_row_id` 和 `_last_updated_sequence_number` 等血缘元数据列。修改覆盖 Spark v3.5 和 v4.0 两个版本，确保这两个维护版本也支持 ORC 格式的行级血缘功能。
