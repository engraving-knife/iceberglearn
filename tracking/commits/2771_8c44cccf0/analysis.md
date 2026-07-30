# 提交 2771：Spark 3.5,4.0: Fix test parameters (#14376)

## 提交信息

- **序号**：2771 / 4088
- **哈希**：8c44cccf0dc3e7dcd9df53b5c1e663aaf82f46d6
- **短哈希**：8c44cccf0
- **日期**：2025-10-20 08:29:51 +0200
- **作者**：Tom Tanaka
- **提交说明**：Spark 3.5,4.0: Fix test parameters (#14376)
- **PR/Issue**：#14376

## 总体目的

本提交修复 Spark 3.5 和 4.0 中 `TestRewritePositionDeleteFiles` 测试类的参数化配置问题。

在此提交之前，该测试类的 `@Parameters` 注解声明的参数名为 `formatVersion = {0}, catalogName = {1}, implementation = {2}, config = {3}`，暗示 formatVersion 是第一个参数（index 0）。但实际的 `parameters()` 方法返回的数组中并没有提供 formatVersion 值，只有 catalogName、implementation 和 config 三个值。同时建表 SQL 中硬编码了 `'format-version'='2'`。

这导致参数名与实际传入的参数不匹配：`@Parameters` 声明 4 个参数但只传了 3 个值，且 formatVersion 没有作为参数注入。本提交将参数顺序调整为 `catalogName = {0}, implementation = {1}, config = {2}, formatVersion = {3}`，与实际传入的值顺序一致，并通过 `@Parameter(index = 3)` 注入 formatVersion 字段，将建表 SQL 从硬编码改为使用参数化的 `%d`。

## 如何达成设计目的

1. **调整 `@Parameters` 的 name 模板**：将 `formatVersion = {0}, catalogName = {1}, implementation = {2}, config = {3}` 改为 `catalogName = {0}, implementation = {1}, config = {2}, formatVersion = {3}`，使参数名与实际数组值的顺序对应。

2. **在 `parameters()` 返回数组中补充 formatVersion 值**：在原有的 catalogName、implementation、CATALOG_PROPS 后面加上 `2` 作为 formatVersion 的值。

3. **新增 `@Parameter(index = 3)` 字段**：声明 `private int formatVersion;` 字段并标注 `@Parameter(index = 3)`，让 JUnit 注入第 4 个参数。

4. **建表 SQL 参数化**：将 `TBLPROPERTIES('format-version'='2')` 改为 `TBLPROPERTIES('format-version'='%d')`，并在 `String.format` 参数列表末尾加上 `formatVersion`。

5. **引入 `Parameter` import**：新增 `import org.apache.iceberg.Parameter;`。

## 修改详情

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFiles.java` (+13/-4 lines)

**修改目的**：修复参数化测试配置，使 formatVersion 成为可注入参数。

**工作逻辑**：将 `@Parameters` 的 name 模板重排为 `catalogName = {0}, implementation = {1}, config = {2}, formatVersion = {3}`；在 `parameters()` 的返回数组中追加 `2` 作为 formatVersion 值；新增 `@Parameter(index = 3) private int formatVersion;` 字段；建表 SQL 中将硬编码的 `'format-version'='2'` 改为 `'format-version'='%d'` 并传入 `formatVersion` 变量。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewritePositionDeleteFiles.java` (+13/-4 lines)

**修改目的**：与 Spark 3.5 相同的修复，应用于 Spark 4.0 版本。

**工作逻辑**：修改内容与 Spark 3.5 版本完全一致。

## 总结

本提交是一个测试基础设施修复，解决了 `TestRewritePositionDeleteFiles` 中 `@Parameters` 名称模板与实际参数值不匹配、formatVersion 未参数化的问题。修复后 formatVersion 成为正式的可注入参数，为将来测试不同 format 版本铺平道路。Spark 3.5 和 4.0 两个版本同步修复。
