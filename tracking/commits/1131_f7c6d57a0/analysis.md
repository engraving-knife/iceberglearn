# 提交 1131：Spark 3.5: Mandate identifier fields when create_changelog_view for table contain unsortable columns (#11045)

## 提交信息

- **序号**：1131 / 4088
- **哈希**：f7c6d57a03282516c6f988fa23a809416cd765b1
- **短哈希**：f7c6d57a0
- **日期**：2024-09-05（Thu Sep 5 11:12:24 2024 -0700）
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Spark 3.5: Mandate identifier fields when create_changelog_view for table contain unsortable columns (#11045)
- **PR/Issue**：#11045

## 总体目的

`create_changelog_view` 存储过程把 Iceberg 表的变更日志构造成一个可查询的 Spark 视图。其内部需要对变更行进行排序（按变更操作类型分桶、去重 carry-over 行、计算 update images 等），排序时若表中存在**不可排序的列**（如 `MAP`、未实现 `Comparable` 的复杂类型），Spark 在 `orderBy`/`sort` 时会抛出运行时异常，但错误信息与 changelog 视图本身无关，用户难以定位。

本提交在 `CreateChangelogViewProcedure` 调用入口处增加前置校验：当表的数据列中存在 Spark `OrderUtils.isOrderable` 判定为不可排序的列时，**必须**显式提供 `identifier_columns`（标识列）——因为标识列是用于排序和分组的主键，通常是可排序的简单类型；若同时既没有标识列又有不可排序列，则直接抛出带清晰提示的 `IllegalArgumentException`，提前失败并指明是哪些列不可排序，避免用户在后续 Spark 排序阶段才遇到晦涩的错误。

## 如何达成设计目的

在 `CreateChangelogViewProcedure` 构造 changelog Dataset 之后、计算 update images / 移除 carry-over 之前，插入校验逻辑：

1. 提前调用 `identifierColumns(input, tableIdent)` 拿到用户指定的标识列数组（避免后续重复取）。
2. 遍历 `df.schema().fields()`，用 `OrderUtils.isOrderable(field.dataType())` 过滤出所有不可排序的列名，收集到 `Set<String> unorderableColumnNames`。
3. 校验：`identifierColumns.length > 0 || unorderableColumnNames.isEmpty()`，否则抛 `IllegalArgumentException("Identifier field is required as table contains unorderable columns: <set>")`。
4. 把提前取好的 `identifierColumns` 复用给后续 `computeUpdateImages` 调用，避免重复计算。

同时新增测试用例：建一张含 `MAP<STRING,STRING>` 列（不可排序）且未指定标识列的表，断言调用 `create_changelog_view` 会抛出预期的 `IllegalArgumentException`。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/CreateChangelogViewProcedure.java`

**修改目的**：在 changelog 视图生成前校验不可排序列，要求显式标识列。

**工作逻辑**：

- 新增 import：`java.util.stream.Collectors` 和 `org.apache.spark.sql.catalyst.expressions.OrderUtils`。
- 在 `buildChangelogView`（或等价方法）中，原先生成 `Dataset<Row> df` 并读 `netChanges` 后直接进入 `shouldComputeUpdateImages` 分支。现插入：

  ```java
  String[] identifierColumns = identifierColumns(input, tableIdent);
  Set<String> unorderableColumnNames =
      Arrays.stream(df.schema().fields())
          .filter(field -> !OrderUtils.isOrderable(field.dataType()))
          .map(StructField::name)
          .collect(Collectors.toSet());

  Preconditions.checkArgument(
      identifierColumns.length > 0 || unorderableColumnNames.isEmpty(),
      "Identifier field is required as table contains unorderable columns: %s",
      unorderableColumnNames);
  ```

- 后续 `if (shouldComputeUpdateImages(input))` 分支里，原本 `computeUpdateImages(identifierColumns(input, tableIdent), df)` 改为复用 `computeUpdateImages(identifierColumns, df)`，消除重复取标识列的开销。

`OrderUtils.isOrderable` 是 Spark 3.5 提供的工具，用于判断一个 `DataType` 是否可用于 `ORDER BY`/`SORT BY`。`MAP`、`STRUCT` 中含不可排序子字段、某些 UDT 等会被判定为不可排序。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCreateChangelogViewProcedure.java`

**修改目的**：覆盖"含不可排序列且未指定标识列"场景的前置校验。

**工作逻辑**：新增测试 `testUpdateWithInComparableType`：

```java
@TestTemplate
public void testUpdateWithInComparableType() {
  sql(
      "CREATE TABLE %s (id INT NOT NULL, data MAP<STRING,STRING>, age INT) USING iceberg",
      tableName);

  assertThatThrownBy(
          () ->
              sql("CALL %s.system.create_changelog_view(table => '%s')", catalogName, tableName))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining(
          "Identifier field is required as table contains unorderable columns: [data]");
}
```

该测试建一张含 `MAP<STRING,STRING>` 列 `data` 的表，不指定标识列直接调用 `create_changelog_view`，断言抛出 `IllegalArgumentException` 且消息包含 `[data]`。注意 `id INT NOT NULL` 虽可作为标识列候选，但用户未在 `create_changelog_view` 调用中传入 `identifier_columns` 参数，故 `identifierColumns` 长度为 0，触发校验。

## 小结

- **成效**：`create_changelog_view` 现在会在入口处检测表中的不可排序列，并要求显式提供标识列，否则给出明确的 `IllegalArgumentException`（列出具体不可排序列名），避免用户在后续 Spark 排序阶段遇到与 changelog 无关的晦涩错误。同时消除了 `identifierColumns` 的重复取值。
- **影响范围**：仅 Spark 3.5 的 `CreateChangelogViewProcedure.java`（+15 行，-1 行）和对应测试（+14 行）。对调用方是行为收紧：此前会延迟到排序阶段失败，现在提前失败并给出可操作提示。已显式提供标识列的调用不受影响。
- **回迁到 1.4.x 的注意事项**：这是用户体验改进（提前报错+清晰提示），不改变数据正确性。1.4.x 若已包含 `create_changelog_view` 且 Spark 版本为 3.5，可考虑 cherry-pick 以改善错误提示——但需确认 1.4.x 的 Spark 3.5 模块中 `OrderUtils.isOrderable` API 可用（该 API 在 Spark 3.5+ 存在）。若 1.4.x 不含 Spark 3.5 或该 API 不可用，则**不应回迁**。cherry-pick 时注意 import 与测试基类（`ExtensionsTestBase`/`@TestTemplate`）的兼容性。
