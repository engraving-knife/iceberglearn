# 提交 1840：Spark 3.5: Add unit test for AddFilesProcedure to check invalid column in partition filter (#12456)

## 提交信息

- **序号**：1840 / 4088
- **哈希**：54a62aeb14155c2c82a9008d9e2679646c2d703a
- **短哈希**：54a62aeb1
- **日期**：2025-03-11 09:59:52 -0500
- **作者**：Bharath Krishna
- **提交说明**：Spark 3.5: Add unit test for AddFilesProcedure to check invalid column in partition filter (#12456)
- **PR/Issue**：#12456

## 总体目的

本提交为 Spark 3.5 的 `AddFilesProcedure` 添加了两个单元测试，验证当分区过滤器（partition filter）中包含无效列或列数不匹配时的错误处理。同时修复了错误消息中的格式问题（引号和冒号格式不一致）。

`AddFilesProcedure` 是 Iceberg Spark 扩展提供的一个存储过程，用于将外部数据文件（如 Hive 表的文件）添加到 Iceberg 表中。当目标 Iceberg 表是分区表时，调用方可以通过 partition filter 指定要添加文件的分区。如果 partition filter 中的列名与表的分区列不匹配，或者列数超过表的分区列数，应该抛出清晰的错误消息。本提交确保这些错误路径被测试覆盖。

## 如何达成设计目的

在 `TestAddFilesProcedure` 测试类中新增两个 `@TestTemplate` 测试方法：`partitionColumnCountMismatchInFilter` 验证 partition filter 列数大于表分区列数时的错误；`invalidPartitionColumnsInFilter` 验证 partition filter 包含非分区列时的错误。同时修复 `SparkTableUtil` 中错误消息的格式：将列名列表的单引号改为方括号，使输出格式与测试断言一致。

## 修改详情

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java` (修改, 51 lines)

**修改目的**：新增两个测试用例验证 partition filter 的错误处理。

**工作逻辑**：

1. `partitionColumnCountMismatchInFilter`：先创建一个分区 Hive 源表，再创建一个仅按 `id` 分区的 Iceberg 表（1 个分区列），然后调用 `add_files` 存储过程并传入包含 2 个列的 partition filter（`map('id', '0', 'dept', '1')`）。断言抛出 `IllegalArgumentException`，消息以 "Cannot add data files to target table" 开头，并包含 "the number of columns in the provided partition filter (2) is greater than the number of partitioned columns in table (1)"。

2. `invalidPartitionColumnsInFilter`：创建按 `id` 分区的 Iceberg 表，调用 `add_files` 传入 partition filter `map('dept', '1')`（`dept` 不是分区列）。断言抛出 `IllegalArgumentException`，消息包含 "specified partition filter refers to columns that are not partitioned: [dept]" 和 "Valid partition columns: [id]"。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java` (修改, 1 line)

**修改目的**：修复错误消息的格式。

**工作逻辑**：将 `Preconditions.checkArgument` 中的错误消息格式从 `"...not partitioned: '%s' . Valid partition columns %s"` 改为 `"...not partitioned: %s . Valid partition columns: [%s]"`。具体变化：列名列表去掉单引号改用方括号包裹（`'%s'` → `%s`），"Valid partition columns" 后加冒号（`%s` → `: [%s]`），使输出更规范。注意此处方法名也从 `Cannot add files to target table` 改为 `Cannot add files to target table`（实际是消息文本统一），且 `tableName` 参数仍传入两次。

## 小结

本提交为 AddFilesProcedure 的分区过滤校验增加了测试覆盖，并修复了错误消息格式。改动涉及测试和主代码各 1 个文件，风险低。回迁到 1.4.x 时需注意：错误消息格式变更可能影响依赖特定消息文本的测试或用户代码；新增测试可直接应用。
