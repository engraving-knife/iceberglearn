# 提交 1463：Spark 3.5: Align RewritePositionDeleteFilesSparkAction filter with Spark case sensitivity (#11700)

## 提交信息

- **序号**：1463 / 4088
- **哈希**：38c8daa4eae8a75ab46571f1efce1609100f53dd
- **短哈希**：38c8daa4e
- **日期**：2024-12-05（Thu Dec 5 18:18:10 2024 -0800）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark 3.5: Align RewritePositionDeleteFilesSparkAction filter with Spark case sensitivity (#11700)
- **PR/Issue**：#11700

## 总体目的

`RewritePositionDeleteFilesSparkAction` 是 Iceberg Spark 3.5 模块中用于重写（压缩）位置删除文件（position delete files）的 Spark Action。用户可通过 `.filter(Expression)` 方法传入一个 Iceberg 表达式，限定只重写满足条件的删除文件所在的数据文件。该过滤器最终通过 `PositionDeletesBatchScan.baseTableFilter(filter)` 传递给表扫描。

问题在于：Spark 有自己的大小写敏感配置（`spark.sql.caseSensitive`，默认 `false`，即大小写不敏感）。当用户在 Spark SQL 中使用大小写不敏感的列名时，Iceberg 的其他 Spark 操作（如普通表扫描）会遵循 Spark 的 `caseSensitive` 设置。但 `RewritePositionDeleteFilesSparkAction` 在构建扫描时**没有**把 Spark 的大小写敏感设置传递给 `PositionDeletesBatchScan`，导致扫描默认以大小写敏感方式解析 filter 中的列名。这与 Spark 的整体行为不一致：用户在 Spark 中用大写列名（如 `C1`）过滤时，普通查询能正常工作（因 Spark 默认大小写不敏感），但 `rewritePositionDeletes` 会因找不到字段而报错。

本提交修复这一不一致：在 `RewritePositionDeleteFilesSparkAction` 中读取 Spark 的 `caseSensitive` 配置，并将其传递给 `PositionDeletesBatchScan.caseSensitive()`，使重写位置删除文件操作的 filter 解析行为与 Spark 大小写敏感设置保持一致。

## 如何达成设计目的

修改分两步：

1. **在 Action 构造函数中读取 Spark 大小写敏感配置**：通过 `SparkUtil.caseSensitive(spark)` 获取 Spark Session 的 `spark.sql.caseSensitive` 配置值，存入新字段 `caseSensitive`。
2. **在扫描构建链路中传递该配置**：在 `planFiles` 方法中，对 `PositionDeletesBatchScan` 调用 `.caseSensitive(caseSensitive)`，使扫描在解析 filter 表达式中的列名时遵循该设置。

同时在测试中新增用例：(a) 修改现有测试用例中的 filter 列名从 `c1` 改为 `C1`，验证在 Spark 默认大小写不敏感时仍能正常工作；(b) 新增一个测试块，将 `spark.sql.caseSensitive` 设为 `true` 后，验证传入大写列名 `C1` 的 filter 会抛出 `ValidationException`（"Cannot find field 'C1' in struct"）。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewritePositionDeleteFilesSparkAction.java`

**修改目的**：让位置删除文件重写操作的 filter 解析遵循 Spark 大小写敏感配置。

**工作逻辑**：
- 新增 import：`org.apache.iceberg.spark.SparkUtil`。
- 新增字段：`private boolean caseSensitive;`，用于缓存 Spark 大小写敏感配置。
- 在构造函数 `RewritePositionDeleteFilesSparkAction(SparkSession spark, Table table)` 中新增一行 `this.caseSensitive = SparkUtil.caseSensitive(spark);`，从 Spark Session 读取配置（底层读取 `spark.sql.caseSensitive`，默认 false）。
- 在 `planFiles(Table deletesTable)` 方法中，修改扫描构建链路：
  ```java
  // 修改前
  scan.baseTableFilter(filter).ignoreResiduals().planFiles();
  // 修改后
  scan.baseTableFilter(filter).caseSensitive(caseSensitive).ignoreResiduals().planFiles();
  ```
  即在 `baseTableFilter` 与 `ignoreResiduals` 之间插入 `.caseSensitive(caseSensitive)`，使 `PositionDeletesBatchScan` 在解析 filter 中的列名时按 Spark 大小写敏感设置处理。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewritePositionDeleteFilesAction.java`

**修改目的**：验证 filter 列名大小写敏感行为与 Spark 配置一致。

**工作逻辑**：
- 新增 import：`assertThatThrownBy`（AssertJ）、`SQLConf`（Spark）。
- 修改现有测试方法中的 filter：将 `Expressions.equal("c1", 1)` 和 `Expressions.equal("c1", 2)` 改为 `Expressions.equal("C1", 1)` 和 `Expressions.equal("C1", 2)`（列名改为大写 `C1`）。因 Spark 默认 `caseSensitive=false`，大写 `C1` 仍能匹配实际列名 `c1`，测试应通过。注释说明 "C1" should work because Spark defaults case sensitivity to false。
- 在该测试方法末尾新增一个测试块：通过 `withSQLConf` 将 `SQLConf.CASE_SENSITIVE().key()` 设为 `"true"`，然后执行 `rewritePositionDeletes(table).filter(filter).execute()`，断言抛出 `ValidationException`，且消息包含 "Cannot find field 'C1' in struct"。这验证了当 Spark 开启大小写敏感时，大写列名 `C1` 无法匹配实际列名 `c1`，会报错。

## 小结

- **成效**：`RewritePositionDeleteFilesSparkAction` 的 filter 列名解析现遵循 Spark 的 `spark.sql.caseSensitive` 配置，与 Spark 其他操作行为一致。默认（大小写不敏感）时大写列名可正常匹配；开启大小写敏感后，列名必须精确匹配。
- **影响范围**：仅 Spark 3.5 模块的 1 个主代码文件和 1 个测试文件。主代码改动 3 处（import、字段、构造函数赋值、扫描链路），测试改动 3 处（import、filter 列名改大写、新增大小写敏感断言块）。不影响表数据或元数据格式，仅影响重写位置删除文件操作的 filter 解析行为。
- **回迁到 1.4.x 的注意事项**：这是一个一致性 bug 修复，建议回迁到 1.4.x（若 1.4.x 支持 Spark 3.5）。需注意：(1) 1.4.x 分支的 Spark 3.5 模块结构应与 main 一致，cherry-pick 一般无冲突；(2) 该修复不改变表格式或元数据，仅影响 Action 运行时行为，回迁风险低；(3) 若 1.4.x 的 Spark 3.5 模块中 `RewritePositionDeleteFilesSparkAction` 代码与 main 差异较大（如被其他补丁修改过），需手动确认 `planFiles` 方法和构造函数的对应位置；(4) 同样的修复在 Spark 3.3/3.4 模块由提交 1467（#11710）单独处理，回迁时两个提交需配对考虑。
