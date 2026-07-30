# 提交 0147：Docs: `DataFrameReader` does not take parameters (#9021)

## 提交信息

- **序号**：0147 / 4088
- **哈希**：774d0e8bc9528b057201d8e2cea31405bd49afbd
- **短哈希**：774d0e8bc
- **日期**：2023-11-10 19:51:28 +0100
- **作者**：zhaoym
- **提交说明**：Docs: `DataFrameReader` does not take parameters (#9021)
- **PR/Issue**：#9021

## 总体目的

本提交修复 Spark 查询文档 `docs/spark-queries.md` 中一处增量读取（incremental read）示例的 Scala 写法不规范问题。在 Scala 中，`SparkSession.read` 是一个返回 `DataFrameReader` 的无副作用访问器（属性），按 Scala 惯例应写成 `spark.read`，而非带空参数列表的 `spark.read()`。原文档在增量读取示例里写成了 `spark.read()`，虽然能编译运行（Scala 允许对无参方法用空括号调用），但既不符合 Scala 风格，也容易让读者误以为 `read` 需要传入参数或具有副作用。

`DataFrameReader` 本身不接收参数，真正的参数通过后续的 `.format(...)`、`.option(...)`、`.load(...)` 链式调用传入。把 `spark.read()` 改为 `spark.read`，使文档示例与 Spark 官方 API 文档及社区惯例一致，避免误导用户。这是一个纯文档规范性修复，影响范围仅限一处代码示例。

## 如何达成设计目的

改动位于“Incremental read”小节的 Scala 示例代码块，将 `spark.read()` 去掉空括号改为 `spark.read`，其余链式调用不变。改动极小但精确指向问题点。

## 修改详情

### `docs/spark-queries.md`

**修改目的**：把增量读取示例中的 `spark.read()` 改为 `spark.read`，消除不必要的空参数列表，符合 Scala 对无副作用访问器不加括号的惯例。

**工作逻辑**：修改前后的示例对比如下。

修改前：

```scala
// get the data added after start-snapshot-id (10963874102873L) until end-snapshot-id (63874143573109L)
spark.read()
  .format("iceberg")
  .option("start-snapshot-id", "10963874102873")
  .option("end-snapshot-id", "63874143573109")
  .load("path/to/table")
```

修改后：

```scala
// get the data added after start-snapshot-id (10963874102873L) until end-snapshot-id (63874143573109L)
spark.read
  .format("iceberg")
  .option("start-snapshot-id", "10963874102873")
  .option("end-snapshot-id", "63874143573109")
  .load("path/to/table")
```

`SparkSession.read()` 在 Spark 中定义为 `def read: DataFrameReader`（无参列表），按 Scala 风格指南，纯访问器、无副作用者应省略括号。该示例展示的是用 `start-snapshot-id` / `end-snapshot-id` 两个选项做增量扫描读取 Iceberg 表追加数据的用法，`read` 仅为获取 `DataFrameReader` 的入口，本身不接收参数，因此去掉括号更准确地反映了 API 形态。

## 小结

这是对 Spark 查询文档增量读取示例的一处规范性修复，使 `spark.read` 的写法符合 Scala 无副作用访问器的惯例，避免误导用户以为 `DataFrameReader` 接收参数。
