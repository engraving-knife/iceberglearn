# 提交 1467：Spark 3.3,3.4: Align RewritePositionDeleteFilesSparkAction filter with Spark case sensitivity (#11710)

## 提交信息

- **序号**：1467 / 4088
- **哈希**：deeb04b2c1ceb9bb8ebee79254f2c98ab5b95b15
- **短哈希**：deeb04b2c
- **日期**：2024-12-06（Fri Dec 6 18:15:02 2024 -0800）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark 3.3,3.4: Align RewritePositionDeleteFilesSparkAction filter with Spark case sensitivity (#11710)
- **PR/Issue**：#11710

## 总体目的

本提交是提交 1463（#11700，针对 Spark 3.5）的姊妹提交，针对 Spark 3.3 和 Spark 3.4 两个模块做完全相同的修复。

`RewritePositionDeleteFilesSparkAction` 是 Iceberg Spark 模块中用于重写（压缩）位置删除文件（position delete files）的 Spark Action。用户可通过 `.filter(Expression)` 方法传入 Iceberg 表达式限定重写范围。问题在于：该 Action 在构建 `PositionDeletesBatchScan` 扫描时**没有**传递 Spark 的大小写敏感配置（`spark.sql.caseSensitive`，默认 `false`），导致 filter 中列名的解析方式与 Spark 整体行为不一致——普通查询能匹配大写列名（因默认大小写不敏感），但 `rewritePositionDeletes` 会因找不到字段而报错。

本提交在 Spark 3.3 和 3.4 两个模块中同步修复：在 `RewritePositionDeleteFilesSparkAction` 构造函数中读取 Spark 的 `caseSensitive` 配置，并在 `planFiles` 方法中通过 `.caseSensitive(caseSensitive)` 传递给 `PositionDeletesBatchScan`，使 filter 解析行为与 Spark 大小写敏感设置一致。

由于 Iceberg 仓库为 Spark 3.3、3.4、3.5 分别维护独立的源码目录（`spark/v3.3/`、`spark/v3.4/`、`spark/v3.5/`），同一修复需在每个版本目录中分别应用。本提交覆盖 3.3 和 3.4 两个目录。

## 如何达成设计目的

对 Spark 3.3 和 3.4 两个模块分别做与提交 1463（Spark 3.5）完全相同的修改：

1. **在 Action 构造函数中读取 Spark 大小写敏感配置**：通过 `SparkUtil.caseSensitive(spark)` 获取配置值，存入新字段 `caseSensitive`。
2. **在扫描构建链路中传递该配置**：在 `planFiles` 方法中，对 `PositionDeletesBatchScan` 调用 `.caseSensitive(caseSensitive)`。
3. **测试同步更新**：修改现有测试的 filter 列名为大写 `C1`（验证默认大小写不敏感时正常工作），并新增 `caseSensitive=true` 时抛 `ValidationException` 的断言块。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java`

**修改目的**：让 Spark 3.3 模块的位置删除文件重写操作 filter 解析遵循 Spark 大小写敏感配置。

**工作逻辑**（与 Spark 3.5 完全相同）：
- 新增 import：`org.apache.iceberg.spark.SparkUtil`。
- 新增字段：`private boolean caseSensitive;`。
- 构造函数中新增：`this.caseSensitive = SparkUtil.caseSensitive(spark);`。
- `planFiles` 方法中修改扫描链路：
  ```java
  // 修改前
  scan.baseTableFilter(filter).ignoreResiduals().planFiles();
  // 修改后
  scan.baseTableFilter(filter).caseSensitive(caseSensitive).ignoreResiduals().planFiles();
  ```

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java`

**修改目的**：验证 Spark 3.3 模块 filter 列名大小写敏感行为。

**工作逻辑**：
- 新增 import：`assertThatThrownBy`、`SQLConf`。
- 修改 filter：`Expressions.equal("c1", 1)` → `Expressions.equal("C1", 1)`（两处），注释说明默认大小写不敏感时 `C1` 可工作。
- 新增 `withSQLConf(CASE_SENSITIVE=true)` 测试块，断言抛 `ValidationException`，消息含 "Cannot find field 'C1' in struct"。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java`

**修改目的**：让 Spark 3.4 模块的位置删除文件重写操作 filter 解析遵循 Spark 大小写敏感配置。

**工作逻辑**：与 Spark 3.3 完全相同——新增 import `SparkUtil`、新增字段 `caseSensitive`、构造函数读取配置、`planFiles` 方法插入 `.caseSensitive(caseSensitive)`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java`

**修改目的**：验证 Spark 3.4 模块 filter 列名大小写敏感行为。

**工作逻辑**：与 Spark 3.3 测试完全相同——新增 import、修改 filter 列名为大写、新增大小写敏感断言块。

## 小结

- **成效**：Spark 3.3 和 3.4 模块的 `RewritePositionDeleteFilesSparkAction` filter 列名解析现遵循 Spark 的 `spark.sql.caseSensitive` 配置，与 Spark 3.5 模块（提交 1463）及 Spark 其他操作行为一致。
- **影响范围**：Spark 3.3 和 3.4 两个模块各 1 个主代码文件和 1 个测试文件，共 4 个文件。每个文件的改动与 Spark 3.5 版本完全对应。不影响表数据或元数据格式，仅影响重写位置删除文件操作的 filter 解析行为。
- **回迁到 1.4.x 的注意事项**：这是一致性 bug 修复，建议与提交 1463（Spark 3.5）一起回迁到 1.4.x。需注意：(1) 1.4.x 分支需确认支持哪些 Spark 版本（3.3/3.4/3.5），仅对支持的版本回迁；(2) 四个文件的改动均为机械性同步，cherry-pick 一般无冲突，但需注意 1.4.x 中这些文件是否已被其他补丁修改过；(3) 该修复不改变表格式或元数据，仅影响 Action 运行时行为，回迁风险低；(4) 建议三个 Spark 版本（3.3、3.4、3.5）的修复同时回迁，保持行为一致。
