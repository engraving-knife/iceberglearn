# 提交 1864：Spark: Call configureTable in ScanTestBase (#12546)

## 提交信息

- **序号**：1864 / 4088
- **哈希**：57ec405a651b99d5fce3f3b4bec217d24bc98d20
- **短哈希**：57ec405a6
- **日期**：2025-03-17 11:02:18 +0100
- **作者**：drexler-sky
- **提交说明**：Spark: Call configureTable in ScanTestBase (#12546)
- **PR/Issue**：#12546

## 总体目的

Iceberg 的 Spark 扫描测试基类 `ScanTestBase`（位于 `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/`）在 `initData` 方法中通过 `HadoopTables.create(writeSchema, PartitionSpec.unpartitioned(), tableProperties, location.toString())` 创建测试表，但创建后没有调用 `configureTable(table)` 进行后续配置。

`configureTable(table)` 是 `ScanTestBase` 及其父类体系中的钩子方法，用于在建表后对表施加测试所需的额外配置（如设置特定的表属性、调整扫描相关参数等）。子类（如 v3 表的扫描测试）会覆盖此方法以注入特定配置。如果不调用 `configureTable`，子类覆盖的配置逻辑不会被执行，导致测试覆盖范围不完整——尤其影响 v3 spec 相关的扫描测试，因为 v3 表需要额外的表级配置才能完整测试新特性（如列默认值、行级删除等）。

此前在 1853（#12520）中，`ScanTestBase` 已经增加了按 schema 是否含默认值来设置 `format-version=3` 的逻辑，但仍未调用 `configureTable`。本提交补上这一遗漏，确保建表后 `configureTable` 被调用，让子类的配置钩子生效。

## 如何达成设计目的

在 `initData` 方法中，`tables.create(...)` 返回 `Table table` 之后，立即调用 `configureTable(table);`。这是单行新增，不改变其他逻辑。`configureTable` 默认实现（在父类中）可能是空方法或设置通用属性，子类可覆盖以注入特定配置。调用时机在建表后、使用表 schema 之前（注释 "Important: use the table's schema for the rest of the test" 之前）。

## 修改详情

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/ScanTestBase.java` (修改, +1 line)

**修改目的**：建表后调用 `configureTable(table)`，让子类的表配置钩子生效。

**工作逻辑**：在 `initData` 方法中：

```java
Table table =
    tables.create(
        writeSchema, PartitionSpec.unpartitioned(), tableProperties, location.toString());
configureTable(table);   // 新增
```

`configureTable` 接收刚创建的 `Table` 对象，子类可覆盖此方法对表施加额外配置（如 `ALTER TABLE SET TBLPROPERTIES ...` 或直接调用 `table.updateProperties().set(...).commit()`）。调用在表创建完成后立即进行，确保后续测试步骤使用的表已具备完整配置。

## 小结

- **成效**：`ScanTestBase` 建表后正确调用 `configureTable`，子类覆盖的表配置逻辑（尤其 v3 相关）能被执行，测试覆盖更完整。
- **影响范围**：仅 spark 3.5 测试基类 1 个文件、1 行新增。不影响生产代码，仅影响继承 `ScanTestBase` 的扫描测试。
- **回迁到 1.4.x 的注意事项**：纯测试改动，回迁安全。需确认 1.4.x 的 `ScanTestBase` 是否已有 `configureTable` 方法（若父类未定义则需先引入）。若 1.4.x 也存在同样的遗漏（建表后未调 `configureTable`），建议回迁以保持测试行为一致。注意 1853 已为 spark 3.4/3.5 的 `ScanTestBase` 加了 v3 format-version 自适应，本提交是 1853 之后对 3.5 的进一步补全，回迁时建议与 1853 一并考虑。
