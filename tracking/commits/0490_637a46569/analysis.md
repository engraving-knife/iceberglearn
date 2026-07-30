# 提交 0490：Spark 3.4: Handle concurrently dropped view during CREATE OR REPLACE (#9677)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0490 |
| 完整哈希 | 637a46569da624631719365ec64caf0eba4a505f |
| 短哈希 | 637a46569 |
| 日期 | 2024-02-07（Wed Feb 7 15:18:06 2024 +0100） |
| 作者 | Eduard Tudenhoefner <etudenhoefner@gmail.com> |
| 说明 | Spark 3.4: Handle concurrently dropped view during CREATE OR REPLACE (#9677) |
| PR | #9677 |

提交统计：2 个文件修改，46 行新增，31 行删除。

涉及文件：
- `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/CreateV2ViewExec.scala`
- `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

## 总体目的

本提交修复 Spark 3.4 模块中 `CREATE OR REPLACE VIEW` 在并发场景下的一个失败路径：当一条 `CREATE OR REPLACE VIEW` 语句正在执行时，若目标视图被另一条并发语句（如 `DROP VIEW`）先行删除，则替换操作会因视图已不存在而抛出 `NoSuchViewException`，导致原本语义上"不存在就创建"的 `CREATE OR REPLACE` 错误地失败。修复的目标是让该并发竞态被妥善处理——捕获该异常并重试，使 `CREATE OR REPLACE VIEW` 在视图被并发删除后仍能成功创建视图，符合 `OR REPLACE` 语义（视图最终应当存在且内容为新定义）。

问题根因在于 `SparkCatalog.replaceView` 调用底层 Iceberg 视图 catalog 的 `buildView(...).createOrReplace()`。该操作在某些实现路径下会先尝试替换已存在的视图，而当视图在替换过程中被并发删除时，底层抛出 Iceberg 的 `org.apache.iceberg.exceptions.NoSuchViewException`。然而旧版 `SparkCatalog.replaceView` 只在 `throws` 中声明了 `NoSuchNamespaceException`，catch 块也只处理 `NoSuchNamespaceException`，因此 Iceberg 的 `NoSuchViewException` 会以未转换的异常向上传播，无法被 Spark 执行层（`CreateV2ViewExec`）按 Spark 自身的 `NoSuchViewException` 类型捕获和处理。

修复分两处协同完成。第一处在 `SparkCatalog.replaceView`：方法签名新增 `throws NoSuchViewException`，并在 catch 块中把 Iceberg 的 `NoSuchViewException` 转换为 Spark 的 `NoSuchViewException(ident)`，使异常类型对齐 Spark catalog 接口契约，便于上层按 Spark 异常类型捕获。第二处在 `CreateV2ViewExec`：在 `SupportsReplaceView` 分支用 try-catch 包裹 `replaceView` 调用，捕获 Spark 的 `NoSuchViewException` 后重试 `replaceView`。重试之所以能成功，是因为底层 `createOrReplace()` 具备"不存在则创建"的语义——第一次因视图被并发删除而走替换路径失败，重试时视图已不存在，`createOrReplace` 转为创建路径，从而成功建立视图。这本质上是把"并发删除导致的瞬时失败"通过一次重试转化为可成功完成的操作。

## 如何达成设计目的

实现路径分三步：首先在 `SparkCatalog.replaceView` 的签名与 catch 块补上 `NoSuchViewException` 的声明与转换，打通异常从 Iceberg 到 Spark 的类型桥梁；其次在 `CreateV2ViewExec` 的 `SupportsReplaceView` 分支用 try-catch 捕获 `NoSuchViewException` 并重试 `replaceView`；最后为避免重复冗长的参数列表（`replaceView` 现在要在两处调用、`createView` 要在三处调用），把这两段调用抽取为 `replaceView(...)` 与 `createView(...)` 私有辅助方法，让 `run()` 主流程更清晰。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/CreateV2ViewExec.scala`

**修改目的**：在 `CREATE OR REPLACE VIEW` 执行路径中处理视图被并发删除的情况，捕获 `NoSuchViewException` 后重试替换，使操作最终成功；同时抽取辅助方法消除重复。

**工作逻辑**：

1. **新增导入**：引入 `org.apache.spark.sql.catalyst.analysis.NoSuchViewException`，用于 catch 子句的类型匹配。

2. **`SupportsReplaceView` 分支增加重试**（CREATE OR REPLACE 路径）：原代码直接内联调用 `c.replaceView(ident, queryText, ...)`，改造为：
   ```scala
   case c: SupportsReplaceView =>
     try {
       replaceView(c, currentCatalog, currentNamespace, newProperties)
     } catch {
       // view might have been concurrently dropped during replace
       case _: NoSuchViewException =>
         replaceView(c, currentCatalog, currentNamespace, newProperties)
     }
   ```
   即先尝试替换；若捕获到 `NoSuchViewException`（视图在替换过程中被并发删除），则再次调用 `replaceView`。重试时底层 `createOrReplace()` 发现视图已不存在，转为创建路径，从而成功建立视图，契合 `CREATE OR REPLACE` 的最终语义（视图应当存在）。

3. **`case _`（不支持 ReplaceView 的回退分支）**：原内联的 `catalog.createView(...)` 改为调用新抽取的 `createView(currentCatalog, currentNamespace, newProperties)` 辅助方法；该分支逻辑不变（视图存在则先 drop 再 create）。

4. **`else` 分支（CREATE VIEW [IF NOT EXISTS]）**：原内联的 `catalog.createView(...)` 同样改为调用 `createView(...)` 辅助方法；`ViewAlreadyExistsException` 的 catch 处理不变。

5. **新增私有辅助方法 `replaceView`**：封装对 `supportsReplaceView.replaceView(ident, queryText, currentCatalog, currentNamespace, viewSchema, queryColumnNames.toArray, columnAliases.toArray, columnComments.map(c => c.orNull).toArray, newProperties.asJava)` 的调用，参数列表冗长，抽取后被 try 与 catch 两处复用，消除重复。

6. **新增私有辅助方法 `createView`**：封装对 `catalog.createView(...)` 的调用，被 `case _` 回退分支与 `else`（CREATE VIEW）分支复用，消除重复。

通过抽取两个辅助方法，`run()` 方法体从原先三处冗长的 9 参数内联调用收敛为对辅助方法的简洁调用，可读性显著提升，同时为重试逻辑提供了干净的复用点。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

**修改目的**：让 `replaceView` 正确声明并转换 `NoSuchViewException`，使并发删除视图时的异常能以 Spark 标准异常类型向上传播，供 `CreateV2ViewExec` 捕获重试。

**工作逻辑**：

- `SparkCatalog.replaceView`（实现 `SupportsReplaceView` 接口）内部调用 `asViewCatalog.buildView(buildIdentifier(ident))...createOrReplace()`。`createOrReplace()` 在视图被并发删除时会抛出 Iceberg 的 `org.apache.iceberg.exceptions.NoSuchViewException`。
- **签名变更**：`throws NoSuchNamespaceException` → `throws NoSuchNamespaceException, NoSuchViewException`，在接口契约层面声明该受检异常，使调用方（`CreateV2ViewExec`）能按类型处理。
- **新增 catch 块**：在已有的 `catch (org.apache.iceberg.exceptions.NoSuchNamespaceException e)` 之后新增：
  ```java
  } catch (org.apache.iceberg.exceptions.NoSuchViewException e) {
    throw new NoSuchViewException(ident);
  }
  ```
  把 Iceberg 内部异常转换为 Spark 的 `org.apache.spark.sql.catalyst.analysis.NoSuchViewException(ident)`，与 `createView` 中转换 `AlreadyExistsException` → `ViewAlreadyExistsException` 的模式一致。这一转换是 `CreateV2ViewExec` 中 `case _: NoSuchViewException` 能正确匹配的前提——若不转换，传播上来的是 Iceberg 异常，Scala 端 catch 不到 Spark 类型，重试逻辑无法生效。
- 配合 `CreateV2ViewExec` 的重试，整个 `CREATE OR REPLACE VIEW` 在视图被并发删除时由"直接失败"变为"捕获后重试一次并成功创建"，提升了并发场景下的鲁棒性。

## 小结

本提交修复 Spark 3.4 模块 `CREATE OR REPLACE VIEW` 在目标视图被并发删除时错误失败的问题。`SparkCatalog.replaceView` 新增 `NoSuchViewException` 声明与 catch 转换，把 Iceberg 异常转为 Spark 异常；`CreateV2ViewExec` 在 `SupportsReplaceView` 分支用 try-catch 捕获该异常后重试 `replaceView`，利用底层 `createOrReplace()` 的"不存在则创建"语义使重试成功。同时抽取 `replaceView`/`createView` 两个私有辅助方法消除冗长参数列表的重复。该修复提升了视图操作的并发鲁棒性，回迁 1.4.x 需注意两文件协同改动、缺一不可，并建议以并发 DROP/REPLACE 场景测试验证重试路径。
