# 提交 3789：Spark: Trim row-level test parameter rows from 6 to 3 (#16549)

## 提交信息

- **序号**：3789 / 4088
- **哈希**：176c6e30f61280c41cc93ea98a73aa3b9403fad5
- **短哈希**：176c6e30f
- **日期**：2026-05-27 11:34:19 -0700
- **作者**：Steven Zhen Wu
- **提交说明**：Spark: Trim row-level test parameter rows from 6 to 3 (#16549)
- **PR/Issue**：#16549

## 总体目的

这个提交大幅精简了 Spark 行级操作测试（`SparkRowLevelOperationsTestBase`）的参数化行数，将 v4.0/v4.1 从 6 行减到 3 行，v3.5 从 7 行减到 3 行，削减约 50%-57% 的测试调用。同时修复了 REST catalog 测试设施中的路径 scheme 不一致问题。

**测试行精简策略**：从"测试每个 catalog 后端"转变为"测试对生产重要的 catalog"：
1. **testhive（Hive）**：保留作为 Hive metastore 基线，携带 HASH/null/DISTRIBUTED 测试轴。
2. **spark_catalog（REST 后端）**：从 Hive 改为 REST 后端，使 SessionCatalog 行测试 REST 提交路径，覆盖 formatVersion 3 的 DV（删除向量）路径。

移除了 testhadoop 行（不推荐用于生产）和冗余的 testhive/testrest 行。9 个具体子类（TestCopyOnWriteMerge/Update/Delete、TestMergeOnReadMerge/Update/Delete 等）的测试调用量各减少 50%。

**REST catalog 路径 scheme 修复**：发现 `RESTCatalogServer` 使用 `getAbsolutePath()` 返回不带 URI scheme 的路径，而 HiveCatalog 和 HadoopCatalog 返回带 `file://` scheme 的路径。这种不一致导致消费 catalog 路径的测试代码（如 `TestBase.move()` 使用 `Paths.get(URI.create(...))`）在 REST catalog 上失败。修复方式是将 RESTCatalogServer 改用 `toURI().toString()` 统一返回带 scheme 的路径。

## 如何达成设计目的

1. 精简 `SparkRowLevelOperationsTestBase` 的参数行。
2. 修改 `RESTCatalogServer` 使用 `toURI().toString()` 返回带 scheme 的 warehouse 路径。
3. 移除 `TestRewriteTablePathProcedure` 中针对 REST catalog 的路径特殊处理。

## 修改详情

### `open-api/src/testFixtures/java/org/apache/iceberg/rest/RESTCatalogServer.java` (+1/-1 lines)

**修改目的**：修复 REST catalog 测试设施的 warehouse 路径 scheme。

**工作逻辑**：
```java
// 修改前
warehouseLocation = new File(tmp, "iceberg_data").getAbsolutePath();
// 修改后
warehouseLocation = new File(tmp, "iceberg_data").toURI().toString();
```
`getAbsolutePath()` 返回 `/tmp/.../iceberg_data`（无 scheme），`toURI().toString()` 返回 `file:/tmp/.../iceberg_data`（带 scheme），与 Hive/Hadoop catalog 的路径格式一致。

### `spark/v3.5/spark-extensions/src/test/java/.../SparkRowLevelOperationsTestBase.java` (+7/-51 lines)

**修改目的**：精简 v3.5 的测试参数行从 7 行到 3 行。

**工作逻辑**：
- 移除 `RANDOM` 和 `ThreadLocalRandom` 相关导入（不再需要随机化）。
- 移除 testhadoop 行、多个 testhive 重复行和旧的 spark_catalog(Hive) 行。
- 将 spark_catalog 行从 Hive 后端改为 REST 后端，配置 `type=rest` 和 `URI` 属性。
- 保留 3 行：testhive(HASH)、testhive(另一组参数)、spark_catalog(REST)。

### `spark/v4.0/spark-extensions/src/test/java/.../SparkRowLevelOperationsTestBase.java` (+7/-51 lines)
### `spark/v4.1/spark-extensions/src/test/java/.../SparkRowLevelOperationsTestBase.java` (+7/-51 lines)

**修改目的**：精简 v4.0 和 v4.1 的测试参数行从 6 行到 3 行。

**工作逻辑**：与 v3.5 相同的精简策略，保留 3 行测试配置。

### `spark/v3.5/spark-extensions/src/test/java/.../TestRewriteTablePathProcedure.java` (+1/-6 lines)
### `spark/v4.0/spark-extensions/src/test/java/.../TestRewriteTablePathProcedure.java` (+1/-6 lines)
### `spark/v4.1/spark-extensions/src/test/java/.../TestRewriteTablePathProcedure.java` (+1/-6 lines)

**修改目的**：移除 REST catalog 的路径特殊处理。

**工作逻辑**：
移除以下特殊处理代码：
```java
if (SparkCatalogConfig.REST.catalogName().equals(catalogName)) {
  filePath = file.getAbsolutePath().toString();
}
```
由于 RESTCatalogServer 现在返回带 scheme 的路径，这个针对 REST catalog 的 `getAbsolutePath()` 特殊处理不再需要，统一使用 `toURI().toString()` 即可。保留这个特殊处理反而会导致路径不匹配（因为 `RewriteTablePathUtil.newPositionDeleteEntry` 验证删除文件路径以表位置前缀开头，而表位置现在包含 `file://`）。

## 总结

这个提交通过精简 Spark 行级操作测试的参数化行数，大幅减少了 CI 运行时间（约 50%），同时保持了对生产重要 catalog（Hive 和 REST）的覆盖。修复了 REST catalog 测试设施的路径 scheme 不一致问题，消除了各处需要的特殊路径处理代码。这是一个测试基础设施优化提交，由 Steven Zhen Wu 使用 Claude Code AI 辅助编写。
