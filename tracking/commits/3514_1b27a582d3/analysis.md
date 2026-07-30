# 提交 3514：Docs: Add Hive Metastore schema validation warnings for schema evolution with Hive catalog (#15814)

## 提交信息

- **序号**：3514 / 4088
- **哈希**：1b27a582d385d4b3341e8246f5327af237c41052
- **短哈希**：1b27a582d3
- **日期**：2026-04-10 13:52:39 -0700
- **作者**：jackylee
- **提交说明**：Docs: Add Hive Metastore schema validation warnings for schema evolution with Hive catalog (#15814)
- **PR/Issue**：#15814

## 总体目的

在使用 Hive catalog 时，Hive Metastore（HMS）会按位置比较列类型来验证 schema 变更（`hive.metastore.disallow.incompatible.col.type.changes` 默认为 true）。这导致某些会改变列位置的 schema 演进操作失败，包括：
- `ADD COLUMN` with `FIRST` 或 `AFTER` 子句
- `ALTER COLUMN` with `FIRST` 或 `AFTER` 子句（重排序）
- `DROP COLUMN` 非最后一列

删除中间列会导致后续列移位，HMS 将其视为不兼容的类型变更并拒绝。需要在文档中明确说明这一限制、变通方案和权衡。

## 如何达成设计目的

在 `spark-ddl.md` 和 `flink-ddl.md` 中添加警告提示框（admonition），说明：
1. HMS 位置性 schema 验证的机制。
2. 受影响的操作列表。
3. 变通方案：设置 `hive.metastore.disallow.incompatible.col.type.changes=false`（区分 Remote HMS 和 Embedded HMS 的配置方式）。
4. 权衡：禁用后 Hive 引擎可能无法正确读取表，但 Iceberg 感知引擎（Spark、Flink、Trino 等）仍可正常工作。

## 修改详情

### `docs/docs/spark-ddl.md` (+35 lines)

**修改目的**：在 Spark DDL 文档中添加 Hive Catalog 限制警告。

**工作逻辑**：
- 在 ALTER TABLE 总述部分添加主要警告框，列出受影响操作和变通方案。
- 在 `ADD COLUMN ... FIRST/AFTER` 部分添加引用警告。
- 在 `ALTER COLUMN ... FIRST/AFTER`（重排序）部分添加引用警告。
- 在 `DROP COLUMN` 部分添加引用警告。

### `docs/docs/flink-ddl.md` (+17 lines)

**修改目的**：在 Flink DDL 文档的 Hive catalog 部分添加限制警告。

**工作逻辑**：在 Hive catalog 配置属性列表后添加警告框，说明 HMS 位置性验证对所有引擎的影响，以及 Remote/Embedded HMS 的变通方案配置方式。

## 总结

文档增强提交，在 Spark 和 Flink DDL 文档中添加 Hive Metastore schema 验证限制的警告说明。文档解释了 HMS 按位置验证列类型导致某些 schema 演进操作失败的机制，提供了通过 `hive.metastore.disallow.incompatible.col.type.changes=false` 的变通方案，并明确了禁用后 Hive 引擎无法读取表的权衡。
