# 提交 2359：Core: Add Schema evolution test with initial defaults (#13537)

## 提交信息

- **序号**：2359 / 4088
- **哈希**：31daaedd13daf0e7ee4781fa5e768f9fe5ba5dc9
- **短哈希**：31daaedd1
- **日期**：2025-07-16 09:42:02 +0200
- **作者**：Anoop Johnson
- **提交说明**：Core: Add Schema evolution test with initial defaults (#13537)
- **PR/Issue**：#13537

## 总体目的

这个提交为 Iceberg Core 添加了一个关于带初始默认值（initial defaults）的 schema 演进的测试用例，验证在添加带有默认值的新列后，扫描规划和投影能正确工作。新增约 69 行测试代码。

背景：Iceberg v3 格式引入了列默认值（column default values）特性，允许在 schema 演进时为新添加的列指定初始默认值（`initialDefault`）和写入默认值（`writeDefault`）。当表已有数据文件后新增一个带默认值的列时，旧数据文件中不包含该列，读取时应使用默认值填充。

此前的测试覆盖可能未充分验证这种"先有数据、后加带默认值列"的场景下的扫描行为。本提交补充了该测试，确保扫描规划、投影扫描、过滤扫描在新列带默认值时都能正确工作，覆盖了 schema 演进与默认值交互的关键路径。

## 如何达成设计目的

在 `TestScansAndSchemaEvolution` 测试类中新增 `testAddColumnWithDefaultValueAndQuery` 方法，通过参数化测试（`@Parameter` + `formatVersion`）在 v3 及以上版本运行，覆盖以下验证点：
1. 添加带默认值的新列后，schema 正确包含该列及其默认值。
2. 基础扫描规划在新列存在时正常工作。
3. 投影扫描包含新列且默认值正确。
4. 基于默认值列的过滤扫描正常工作。
5. schema 演进后写入新数据，所有任务的 schema 均包含带默认值的列。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestScansAndSchemaEvolution.java` (+69/-0 lines)

**修改目的**：新增带初始默认值列的 schema 演进测试。

**工作逻辑**：`testAddColumnWithDefaultValueAndQuery()` 方法：
- 使用 `assumeThat(V3_AND_ABOVE)` 确保仅在 v3+ 格式版本运行（默认值需要 v3+）。
- 创建表并写入两个初始数据文件（不含 `category` 列）。
- 通过 `updateSchema().addColumn("category", Types.StringType.get(), "Product category", Literal.of(defaultValue))` 添加带初始默认值 `"default_category"` 的新列。
- 验证 schema 中 `category` 字段的 `initialDefault` 和 `writeDefault` 均为预期值。
- 验证基础扫描 `planFiles()` 返回 2 个任务。
- 验证投影扫描（`select("id", "data", "category")`）中每个任务的 schema 包含 `category` 字段且默认值正确。
- 验证基于 `category` 默认值的过滤扫描返回所有文件（默认值适用于所有行）。
- 验证基于非默认值的过滤扫描仍返回所有文件（过滤在读取阶段进行）。
- 写入第三个数据文件后，验证所有 3 个任务的 schema 均包含带正确默认值的 `category` 字段。

新增导入：`TestHelpers.V3_AND_ABOVE`、`Expressions.Literal`、`Assumptions.assumeThat`。

## 总结

该提交为 Iceberg Core 补充了带初始默认值列的 schema 演进测试，覆盖了添加带默认值新列后的扫描规划、投影扫描、过滤扫描以及后续数据写入等关键路径，验证了 v3 默认值特性在 schema 演进场景下的正确性。这是一个纯测试新增提交，无生产代码改动。
