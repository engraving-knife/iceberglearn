# 提交 1232：API, Spark: Make StrictMetricsEvaluator not fail on nested column predicates (#11261)

## 提交信息

- **序号**：1232 / 4088
- **哈希**：ca8a3a4d29799b7e0635b0feb1126b0d457618dd
- **短哈希**：ca8a3a4d2
- **日期**：2024-10-14（Mon Oct 14 14:47:29 2024 +0800）
- **作者**：Yujiang Zhong <42907416+zhongyujiang@users.noreply.github.com>
- **提交说明**：API, Spark: Make StrictMetricsEvaluator not fail on nested column predicates (#11261)
- **PR/Issue**：#11261

## 总体目的

`StrictMetricsEvaluator` 用于判断数据文件中**所有**行是否一定满足某个谓词（"严格匹配"），从而在 Spark DELETE 等 row-level 操作中决定能否安全跳过该文件。原先该评估器在遇到嵌套列（nested column，例如 `struct.field`）的谓词时会直接抛 `Preconditions.checkNotNull` 异常（消息 `Cannot filter by nested column: ...`），原因是嵌套列在 `struct` 顶层 `field(id)` 查找中拿不到（只有顶层字段在 struct 上），导致 DELETE 等操作对包含嵌套列的过滤条件直接报错。

本提交将"对嵌套列直接抛异常"改为"对嵌套列保守返回 `ROWS_MIGHT_NOT_MATCH`"（即不能保证所有行都匹配，因此不裁剪文件），从而让 DELETE 等操作在嵌套列条件下的过滤能够正常执行而不是失败。这是一个正确性与可用性修复：嵌套列统计信息 Iceberg 当前并未在 manifest 中存储，因此无法做严格匹配判定，但应当返回保守结果，而不是抛异常中断整个操作。

## 如何达成设计目的

1. 在 `StrictMetricsEvaluator` 中移除 `schema` 字段以及 `Preconditions.checkNotNull(struct.field(id), ...)` 调用，新增私有方法 `isNestedColumn(int id)`：当 `struct.field(id) == null` 时认为该 id 是嵌套列（因为顶层 struct 仅包含顶层字段）。
2. 在所有比较类谓词方法（`isNull`、`notNull`、`lt`、`ltEq`、`gt`、`gtEq`、`eq`、`notEq`、`in`、`notIn`）的开头，把原先的 `checkNotNull` 改写为：

   ```java
   if (isNestedColumn(id)) {
       return ROWS_MIGHT_NOT_MATCH;
   }
   ```

   即对嵌套列直接返回"可能不匹配"，等价于不裁剪此文件，保证安全。
3. 由于不再需要 `Types.NestedField field` 中间变量，原先 `Conversions.fromByteBuffer(field.type(), ...)` 改为使用 `ref.type()`（`BoundReference` 本身就携带类型），逻辑等价且更直接，同时移除了对 `Preconditions` 与 `Types` 的部分 import。
4. 测试方面，在 `TestStrictMetricsEvaluator` 中为 SCHEMA 添加一个 `struct` 字段（含 `nested_col_no_stats`、`nested_col_with_stats` 两个子字段），并新增两个测试方法 `testEvaluateOnNestedColumnWithoutStats` 与 `testEvaluateOnNestedColumnWithStats`，验证在嵌套列上做 `>=`、`<=`、`isNull`、`notNull` 时均返回 `false`（即 ROWS_MIGHT_NOT_MATCH，不严格匹配，因此 shouldRead 为 false）。
5. 在 Spark 端 `TestDelete` 中新增 `testDeleteWithFilterOnNestedColumn`，端到端验证基于嵌套列 `complex.c1` 的 DELETE 语句能正确执行并得到预期结果。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/StrictMetricsEvaluator.java`

**修改目的**：使评估器对嵌套列谓词不再抛异常，而是返回保守的 `ROWS_MIGHT_NOT_MATCH`。

**工作逻辑**：

- 删除 `private final Schema schema;` 字段及构造函数中的赋值，仅保留 `struct`（`schema.asStruct()`），用于顶层字段查找。同时移除对 `Preconditions` 与 `Types` 的相关 import。
- 新增私有方法：

  ```java
  private boolean isNestedColumn(int id) {
      return struct.field(id) == null;
  }
  ```

- 在 `isNull`、`notNull`、`lt`、`ltEq`、`gt`、`gtEq`、`eq`、`notEq`、`in`、`notIn` 共 10 个谓词方法中，将原先的：

  ```java
  Preconditions.checkNotNull(struct.field(id), "Cannot filter by nested column: %s", schema.findField(id));
  ```

  统一替换为：

  ```java
  if (isNestedColumn(id)) {
      return ROWS_MIGHT_NOT_MATCH;
  }
  ```

- 对所有比较类谓词（`lt`/`ltEq`/`gt`/`gtEq`/`eq`/`notEq`/`in`/`notIn`），原先用 `Types.NestedField field = struct.field(id);` 取类型并 `Conversions.fromByteBuffer(field.type(), ...)` 解码 upper/lower bounds，现统一改为 `Conversions.fromByteBuffer(ref.type(), ...)`，其中 `ref` 是 `BoundReference<T>`，其 `type()` 返回的就是该列类型。该改动纯等价，但因移除了 `field` 变量而必须如此调整。
- 部分错误提示中 `file.path()` 改为 `file.location()`，与"路径"语义更贴合（location 是完整 URI 字符串）。

### `api/src/test/java/org/apache/iceberg/expressions/TestStrictMetricsEvaluator.java`

**修改目的**：覆盖嵌套列场景下的评估行为。

**工作逻辑**：

- 在 SCHEMA 中追加 id=15 的 `struct` 字段，包含 id=16 `nested_col_no_stats`（无统计）和 id=17 `nested_col_with_stats`（带 value counts/null counts/lower/upper bounds，复用 INT_MIN_VALUE/INT_MAX_VALUE）。
- 新增 `testEvaluateOnNestedColumnWithoutStats` 与 `testEvaluateOnNestedColumnWithStats`，对 `greaterThanOrEqual`、`lessThanOrEqual`、`isNull`、`notNull` 在嵌套列上调用 `StrictMetricsEvaluator`，断言结果均为 `false`（不严格匹配）。无论子列是否带统计，因 id 在顶层 struct 查不到，都走 `ROWS_MIGHT_NOT_MATCH` 分支。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`

**修改目的**：端到端验证 DELETE 在嵌套列过滤条件下能正常执行。

**工作逻辑**：新增 `testDeleteWithFilterOnNestedColumn`：

- 调用 `createAndInitNestedColumnsTable()` 建表，包含 `complex` struct 列。
- 插入两条数据 `(1, named_struct("c1", 3, "c2", "v1"))` 与 `(2, named_struct("c1", 2, "c2", "v2"))`。
- 依次执行三条 DELETE：`complex.c1 > 3`（无命中）、`complex.c1 = 3`（删第一条）、`t.complex.c1 = 2`（删第二条，带别名），每次断言剩余行符合预期。验证不再因嵌套列谓词报错。

## 小结

- **成效**：`StrictMetricsEvaluator` 在面对嵌套列谓词时不再抛 `NullPointerException`/`IllegalStateException`，而是保守返回"可能不匹配"，使基于嵌套列的 DELETE 等操作能够正确执行；同时清理了无用的 `schema` 字段、`Preconditions`/`Types` import，统一以 `BoundReference.type()` 取列类型。
- **影响范围**：仅 API 与 Spark 测试代码。API 公共类 `StrictMetricsEvaluator` 行为对嵌套列从"抛异常"改为"返回保守值"，对上层 row-level 操作（DELETE/MERGE/UPDATE）的兼容性是正向修复；对非嵌套列逻辑无变化。
- **回迁到 1.4.x 的注意事项**：本提交是 bug 修复，建议回迁。回迁时注意 1.4.x 中 `StrictMetricsEvaluator` 是否还保留原 `schema` 字段；如有外部代码依赖旧抛异常行为（极少见），需同步调整。同时回迁需带上测试（包括 Spark 端 `TestDelete` 与 API 端 `TestStrictMetricsEvaluator`），且 Spark 3.3/3.4 在 1.4.x 中的 `TestDelete` 也需同步添加用例（本提交只在 v3.5 添加了用例，但实际修复在 API 层对三个版本均生效）。
