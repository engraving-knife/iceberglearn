# 提交 0484：Spark: Handle concurrently dropped view during CREATE OR REPLACE (#9623)

## 提交信息

- **序号**：0484 / 4088
- **哈希**：2a39af894f4f00aa37922ef765cc2583517fa1d1
- **短哈希**：2a39af894
- **日期**：2024-02-07 08:32:43 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Handle concurrently dropped view during CREATE OR REPLACE (#9623)
- **PR/Issue**：#9623

## 总体目的

这个提交修复了一个并发竞态条件下的失败场景：当用户执行 `CREATE OR REPLACE VIEW` 时，如果在执行过程中视图被另一个会话/进程并发地删除（drop），原代码会把 Iceberg 内部的 `NoSuchViewException` 当作未声明的检查型异常向上抛出，导致命令直接失败、用户看到与"视图不存在"无关的异常堆栈。本提交的目标是把这种"并发删除"的情况显式识别并自动重试一次：把 Iceberg 的 `NoSuchViewException` 翻译为 Spark 的 `NoSuchViewException`，并在 `CreateV2ViewExec` 里捕获它后再次调用 `replaceView`，由于第二次调用时视图已经真的不存在，`ViewBuilder.createOrReplace()` 会走 create 分支重新建出视图，最终使命令在并发删除的窗口下仍能成功。

具体来说，`SparkCatalog.replaceView` 内部调用的是 Iceberg 的 `ViewBuilder.createOrReplace()`。`createOrReplace` 的典型实现是：先加载现有 view metadata（如果存在），然后追加一个新的 view version 来"替换"。如果在这两步之间，另一个会话执行了 `DROP VIEW`，那么"替换"分支就会在 commit 阶段抛出 Iceberg 的 `org.apache.iceberg.exceptions.NoSuchViewException`。原代码在 `SparkCatalog.replaceView` 的 `throws` 子句里只声明了 `NoSuchNamespaceException`，没有声明 `NoSuchViewException`，更没有把 Iceberg 版的异常翻译成 Spark 版的异常。结果异常就以"未声明受检异常"的形式泄漏到 `CreateV2ViewExec`，而 Spark 的 `CreateV2ViewExec` 也没有任何捕获逻辑，于是直接冒泡到用户层面。

修复方案的两条腿：Java 侧在 `SparkCatalog.replaceView` 的 `throws` 子句增加 `NoSuchViewException`，并新增一个 `catch (org.apache.iceberg.exceptions.NoSuchViewException e) { throw new NoSuchViewException(ident); }` 把 Iceberg 异常翻译为 Spark 的 `org.apache.spark.sql.catalyst.analysis.NoSuchViewException`；Scala 侧在 `CreateV2ViewExec.run()` 中对 `replaceView(...)` 包一层 `try { ... } catch { case _: NoSuchViewException => replaceView(...) }`，"并发删除后重试一次"的语义就清晰落在这一行上。配套地，把原本重复 3 次的 9 参 `c.replaceView(...)`/`catalog.createView(...)` 调用抽取为两个私有辅助方法 `replaceView(...)` 与 `createView(...)`，让重试代码只重复一行而不是九行参数。

## 如何达成设计目的

整体设计是"翻译异常 + 重试"两层：先在 Java/Spark 桥接层把 Iceberg 内部的 `NoSuchViewException` 翻译成 Spark 标准 `NoSuchViewException`，让 Spark 调用方有可捕获的类型化异常；再在 `CreateV2ViewExec` 中显式捕获这个异常并重试一次 `replaceView`，利用 `ViewBuilder.createOrReplace()` 在视图不存在时自动切到 create 分支的行为来恢复。代码层面通过抽取两个私有辅助方法（`replaceView`、`createView`）来让重复的 9 参调用收敛为一处定义，重试代码读起来只剩两行 `replaceView(...)`。

## 修改详情

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/execution/datasources/v2/CreateV2ViewExec.scala`

**修改目的**：在 `CREATE OR REPLACE VIEW` 路径上为并发删除的视图增加"捕获 `NoSuchViewException` 后重试一次"的语义；同时把重复的 9 参调用抽取为 `replaceView`/`createView` 两个辅助方法以提高可读性。

**工作逻辑**：

1. **新增 import**：`import org.apache.spark.sql.catalyst.analysis.NoSuchViewException`，用于在 `try/catch` 中匹配该类型异常（依赖 Java 侧 `SparkCatalog.replaceView` 把 Iceberg 异常翻译过来）。

2. **重写 `run()` 方法的 `CREATE OR REPLACE VIEW` 分支**：
   - 当 catalog 实现 `SupportsReplaceView` 时，原代码直接调用 `c.replaceView(...)` 9 个参数。新版用 `try`/`catch` 包裹：
     ```scala
     try {
       replaceView(c, currentCatalog, currentNamespace, newProperties)
     } catch {
       // view might have been concurrently dropped during replace
       case _: NoSuchViewException =>
         replaceView(c, currentCatalog, currentNamespace, newProperties)
     }
     ```
     第一次 `replaceView` 若因视图被并发 drop 而抛 `NoSuchViewException`，则再调一次。第二次进入 `ViewBuilder.createOrReplace()` 时视图已不存在，会走 create 分支重建视图，从而把命令从失败中救回。
   - `case _ =>` 分支（catalog 不支持 `SupportsReplaceView`，回退到 drop + create）原本也内联了 9 参 `catalog.createView(...)`，新版替换为单行 `createView(currentCatalog, currentNamespace, newProperties)`。
   - `else` 分支（`!replace`，即 `CREATE VIEW [IF NOT EXISTS]`）原本也内联了 9 参 `catalog.createView(...)`，新版同样替换为 `createView(currentCatalog, currentNamespace, newProperties)`。`ViewAlreadyExistsException if allowExisting` 的捕获逻辑保持不变。

3. **新增私有辅助方法 `replaceView(...)`**：
   ```scala
   private def replaceView(
     supportsReplaceView: SupportsReplaceView,
     currentCatalog: String,
     currentNamespace: Array[String],
     newProperties: Map[String, String]) = {
     supportsReplaceView.replaceView(
       ident, queryText, currentCatalog, currentNamespace, viewSchema,
       queryColumnNames.toArray, columnAliases.toArray,
       columnComments.map(c => c.orNull).toArray, newProperties.asJava)
   }
   ```
   封装了 9 个参数的 `replaceView` 调用，被 `try` 块与 `catch` 重试块两处共用。

4. **新增私有辅助方法 `createView(...)`**：
   ```scala
   private def createView(
     currentCatalog: String,
     currentNamespace: Array[String],
     newProperties: Map[String, String]) = {
     catalog.createView(
       ident, queryText, currentCatalog, currentNamespace, viewSchema,
       queryColumnNames.toArray, columnAliases.toArray,
       columnComments.map(c => c.orNull).toArray, newProperties.asJava)
   }
   ```
   封装了 9 个参数的 `createView` 调用，被两处 `else` 分支共用。

整体上，原本三处重复的 9 参调用被收编到两个辅助方法，重试代码也只占两行，可读性显著提升，同时引入了"并发删除后重试"的核心语义。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkCatalog.java`

**修改目的**：把 Iceberg 内部的 `NoSuchViewException` 翻译为 Spark 标准 `NoSuchViewException`，让 `CreateV2ViewExec` 有可捕获的类型化异常；同时在 `replaceView` 的 `throws` 子句中显式声明该异常，与接口契约对齐。

**工作逻辑**：

1. **`replaceView` 方法签名的 `throws` 子句**：
   ```java
   @Override
   public View replaceView(
       Identifier ident, String sql, String currentCatalog,
       String[] currentNamespace, StructType schema,
       String[] queryColumnNames, String[] columnAliases,
       String[] columnComments, Map<String, String> properties)
       throws NoSuchNamespaceException, NoSuchViewException {
   ```
   原签名只有 `throws NoSuchNamespaceException`，新增 `NoSuchViewException`，与 `ViewCatalog.replaceView` 接口契约一致（接口允许声明 `NoSuchViewException`，因为 replace 语义要求视图存在；不存在时抛此异常）。

2. **`try/catch` 块内新增一个分支**：原 `try` 只捕获 `org.apache.iceberg.exceptions.NoSuchNamespaceException` 并翻译为 `NoSuchNamespaceException(currentNamespace)`。新增：
   ```java
   } catch (org.apache.iceberg.exceptions.NoSuchViewException e) {
     throw new NoSuchViewException(ident);
   }
   ```
   即：当 `asViewCatalog.buildView(...).createOrReplace()` 在 replace 阶段因视图被并发删除而抛 Iceberg 版 `NoSuchViewException` 时，把它翻译为 `new NoSuchViewException(ident)`（Spark 标准）向上抛。这是 Scala 侧 `catch { case _: NoSuchViewException => ... }` 能捕获到该异常的前提——否则异常会是 Iceberg 内部类型，Scala 的 `case _: NoSuchViewException` 不会匹配。

   下方 `alterView` 方法本就已有 `catch (org.apache.iceberg.exceptions.NoSuchViewException e) { throw new NoSuchViewException(ident); }`，本次 `replaceView` 的改动让两者行为一致，消除桥接层的不一致。

## 小结

本提交通过两层修复消除了 `CREATE OR REPLACE VIEW` 在并发删除竞态下的失败：Java 桥接层把 Iceberg 内部 `NoSuchViewException` 翻译为 Spark 标准 `NoSuchViewException`，并在 `replaceView` 签名 `throws` 中显式声明；Scala 层 `CreateV2ViewExec` 在 `SupportsReplaceView` 分支捕获该异常后重试一次 `replaceView`，依赖 `ViewBuilder.createOrReplace()` 在视图不存在时走 create 分支自然重建视图。附带把三处重复的 9 参 `replaceView`/`createView` 调用抽取为两个私有辅助方法，让重试代码与各分支调用都收敛到单行。整体行为对外保持兼容（成功场景不变），仅在原本失败的竞态窗口下额外把命令救回。
