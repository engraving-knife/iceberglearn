# 提交 2841：Spark: Improve the table, view, and function existence verification logic (#14457)

## 提交信息

- **序号**：2841 / 4088
- **哈希**：91565517130b2879ee033c5ff9917bb1ff70da7e
- **短哈希**：915655171
- **日期**：2025-11-06 15:15:35 -0800
- **作者**：roryqi
- **提交说明**：Spark: Improve the table, view, and function existence verification logic (#14457)
- **PR/Issue**：#14457

## 总体目的

Spark 的 `TableCatalog`、`ViewCatalog`、`FunctionCatalog` 接口都提供了 `xxxExists` 默认方法，默认实现通常通过尝试 `load`/`loadView`/`loadFunction` 并捕获对应 `NoSuchXxxException` 来判断存在性。这种"试错"方式既低效（要抛/捕异常栈），也可能在某些 catalog 实现下产生副作用或额外日志，且对 `SparkSessionCatalog`（Iceberg 包装 session catalog 的双层结构）语义不准确。

Iceberg 的 catalog 层本身已经有原生的 `tableExists`、`viewExists`、`functionExists` 方法，可以直接返回布尔结果。该提交在 Spark 3.4/3.5/4.0 三个版本分支的 `SparkCatalog` 与 `SparkSessionCatalog` 中覆盖 `tableExists`、`viewExists`、（仅 `SparkSessionCatalog`）`functionExists`，让它们直接委托给底层 Iceberg catalog 的原生 `exists` 方法，避免异常驱动的存在性检查，提升性能与语义准确性。

这与 2831（namespace existence verification）属同一系列的存在性验证改进，本提交补全 table/view/function 三个维度。

## 如何达成设计目的

1. **`SparkCatalog.tableExists`**：对 `PathIdentifier`（路径表）调用 `tables.exists(location)`；否则调用 `icebergCatalog.tableExists(buildIdentifier(ident))`。
2. **`SparkCatalog.viewExists`**：先判断 `asViewCatalog != null`（当前 catalog 是否支持 view），再调用 `asViewCatalog.viewExists(buildIdentifier(ident))`。
3. **`SparkSessionCatalog.tableExists`**：Iceberg catalog 与 session catalog 任一存在即返回 true（`icebergCatalog.tableExists(ident) || getSessionCatalog().tableExists(ident)`），因为 session catalog 模式下两类表共存。
4. **`SparkSessionCatalog.viewExists`**：先查 Iceberg view catalog（`asViewCatalog.viewExists`），再查 session catalog（若它是 `ViewCatalog`，`isViewCatalog()`）。
5. **`SparkSessionCatalog.functionExists`**：`super.functionExists(ident) || getSessionCatalog().functionExists(ident)`，让 Iceberg 函数与 session catalog 函数都能被发现。
6. 修改同步应用到 Spark 3.4、3.5、4.0 三个版本分支。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+14/-0 lines)

**修改目的**：覆盖 `tableExists` 与 `viewExists`，直接委托底层 catalog。

**工作逻辑**：
- `tableExists(Identifier ident)`：`isPathIdentifier(ident)` 时 `tables.exists(((PathIdentifier) ident).location())`；否则 `icebergCatalog.tableExists(buildIdentifier(ident))`。`tables` 是 `HadoopTables`/`PathTables` 之类，对路径表有原生 `exists`。
- `viewExists(Identifier ident)`：`return asViewCatalog != null && asViewCatalog.viewExists(buildIdentifier(ident));`，短路确保不支持 view 时不调用。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkSessionCatalog.java` (+16/-0 lines)

**修改目的**：在 session catalog 双层结构下正确判断 table/view/function 存在性。

**工作逻辑**：
- `tableExists`：`icebergCatalog.tableExists(ident) || getSessionCatalog().tableExists(ident)`。
- `viewExists`：`(asViewCatalog != null && asViewCatalog.viewExists(ident)) || (isViewCatalog() && getSessionCatalog().viewExists(ident))`，先查 Iceberg view catalog 再查 session catalog（若 session catalog 是 ViewCatalog）。
- `functionExists`：`super.functionExists(ident) || getSessionCatalog().functionExists(ident)`，`super` 即 Iceberg `SparkCatalog` 的 function 存在性逻辑，再叠加 session catalog 的函数。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+14/-0 lines)

**修改目的**：与 4.0 相同的 `tableExists`/`viewExists` 覆盖应用到 Spark 3.5。逻辑一致。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkSessionCatalog.java` (+16/-0 lines)

**修改目的**：与 4.0 相同的 `tableExists`/`viewExists`/`functionExists` 覆盖应用到 Spark 3.5。逻辑一致。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java` (+14/-0 lines)

**修改目的**：与 4.0 相同的覆盖应用到 Spark 3.4。逻辑一致。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkSessionCatalog.java` (+16/-0 lines)

**修改目的**：与 4.0 相同的覆盖应用到 Spark 3.4。逻辑一致。

## 总结

该提交在 Spark 3.4/3.5/4.0 三个版本的 `SparkCatalog` 与 `SparkSessionCatalog` 中覆盖 `tableExists`、`viewExists`、（`SparkSessionCatalog`）`functionExists`，直接委托底层 Iceberg catalog 的原生 `exists` 方法，替代 Spark 默认的"抛异常试错"实现。对 `SparkSessionCatalog` 还正确处理了 Iceberg catalog 与 session catalog 双层共存的存在性合并语义。与 2831（namespaceExists）一起，完成了 Iceberg Spark 集成对 namespace/table/view/function 四类存在性检查的全面优化。
