# 提交 1460：Spark 3.3, 3.4: Make where clause case sensitive in rewrite data files (#11696)

## 提交信息

- **序号**：1460 / 4088
- **哈希**：3278b6990c08e277b397d675f4610da395debc77
- **短哈希**：3278b6990
- **日期**：2024-12-04（Wed Dec 4 22:39:10 2024 +0800）
- **作者**：AGW <ludlows@users.noreply.github.com>
- **提交说明**：Spark 3.3, 3.4: Make where clause case sensitive in rewrite data files (#11696)
- **PR/Issue**：#11696

## 总体目的

本提交是提交 1455（#11439，Spark 3.5 修复）的姊妹提交，把相同的修复应用到 Spark 3.3 和 Spark 3.4 模块。由于 Iceberg 为每个 Spark 版本维护独立的源码副本（`spark/v3.3/`、`spark/v3.4/`、`spark/v3.5/`），同一个 bug 需要在每个版本下分别修复。

问题与 1455 完全相同：`rewrite_data_files` 过程的 `where` 参数经 Spark 解析后转换为 Iceberg `Expression`，但 `RewriteDataFilesSparkAction` 构造 `TableScan` 时未把 Spark 的 `spark.sql.caseSensitive` 配置透传给 Iceberg 扫描（Iceberg `TableScan` 默认 `caseSensitive = true`）。当 Spark 处于大小写不敏感模式（默认）且 where 子句中的列名大小写与表 schema 不一致时（如 `where => 'C1 > 0'` 而实际列名为 `c1`），Iceberg 扫描绑定 filter 表达式时按大小写敏感模式找不到列，导致过滤失效或异常。

修复方式与 1455 一致：在 `RewriteDataFilesSparkAction` 构造函数中读取 `SparkUtil.caseSensitive(spark)`，在 `table.newScan()` 上调用 `.caseSensitive(caseSensitive)`。

## 如何达成设计目的

对 Spark 3.3 和 3.4 各自的 `RewriteDataFilesSparkAction` 与 `TestRewriteDataFilesProcedure` 应用与 1455 相同的改动：
1. 新增 `caseSensitive` 字段并在构造函数中初始化；
2. 在 `planFileGroups` 的 `TableScan` 链上增加 `.caseSensitive(caseSensitive)`；
3. 新增 `testFilterCaseSensitivity` 回归测试。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java`（修改，+4 行）

**修改目的**：让 Spark 3.3 的 rewrite data files 扫描遵循 Spark 大小写敏感配置。

**工作逻辑**：与 1455 中 Spark 3.5 的改动完全一致：
- 新增 `import org.apache.iceberg.spark.SparkUtil;`
- 新增字段 `private boolean caseSensitive;`
- 构造函数中 `this.caseSensitive = SparkUtil.caseSensitive(spark);`
- `planFileGroups` 中 `table.newScan().useSnapshot(startingSnapshotId).caseSensitive(caseSensitive).filter(filter)...`

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java`（修改，+24 行）

**修改目的**：新增回归测试。

**工作逻辑**：`testFilterCaseSensitivity` 与 1455 中的测试逻辑相同，但有两点差异：
- 使用 JUnit 4 的 `@Test` 注解（Spark 3.3 测试基类基于 JUnit 4），而非 3.5 的 `@TestTemplate`；
- `assertThat(output.get(0)).hasSize(3)`：Spark 3.3 的 `rewrite_data_files` 返回结果为 3 列（rewritten_data_files_count、added_data_files_count、rewritten_bytes_count），而 3.4/3.5 为 4 列（额外一列，如 added_files_size）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java`（修改，+4 行）

**修改目的**：让 Spark 3.4 的 rewrite data files 扫描遵循 Spark 大小写敏感配置。

**工作逻辑**：与 3.3/3.5 完全一致。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java`（修改，+24 行）

**修改目的**：新增回归测试。

**工作逻辑**：`testFilterCaseSensitivity` 与 3.5 版本一致（`@TestTemplate`、`hasSize(4)`），仅文件路径不同。

## 小结

- **成效**：把 1455 的修复（rewrite_data_files where 子句大小写敏感）补齐到 Spark 3.3 和 3.4 模块，使三个 Spark 版本行为一致。
- **影响范围**：Spark 3.3 和 3.4 模块各自的 `RewriteDataFilesSparkAction`（生产代码）与 `TestRewriteDataFilesProcedure`（测试）。改动结构与 1455 完全镜像。
- **回迁到 1.4.x 的注意事项**：应与 1455 一并回迁，保持三个 Spark 版本一致。需确认 1.4.x 的 `spark/v3.3`、`spark/v3.4` 模块中 `SparkUtil.caseSensitive(SparkSession)` 方法存在（1.4.x 中已具备）。Spark 3.3 测试用 JUnit 4，3.4/3.5 用 JUnit 5，回迁时注意注解差异。返回结果列数差异（3.3 为 3 列、3.4/3.5 为 4 列）在测试断言中已分别处理，回迁时需保持。
