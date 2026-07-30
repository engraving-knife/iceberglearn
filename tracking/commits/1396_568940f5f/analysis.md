# 提交 1396：Spark 3.3: Iceberg parser should passthrough unsupported procedure to delegate (#11580)

## 提交信息

- **序号**：1396 / 4088
- **哈希**：568940f5f85568868f3d75855fa72d17378271f3
- **短哈希**：568940f5f
- **日期**：2024-11-19（Tue Nov 19 04:11:41 2024 +0800）
- **作者**：Cheng Pan <chengpan@apache.org>
- **提交说明**：Spark 3.3: Iceberg parser should passthrough unsupported procedure to delegate (#11580)
- **PR/Issue**：#11580

## 总体目的

本提交是提交 1395（PR #11579）在 Spark 3.3 模块上的同款修复，目的与设计完全相同：让 Iceberg Spark 3.3 扩展解析器不再无条件拦截所有 `call` 语句，而是只拦截调用 Iceberg 内置 procedure（`system.<内置名>`）的语句，其它 `call` 透传给 Spark 原生解析器（delegate），从而给出准确的错误信息或正确执行第三方 procedure。同时清理 SQL 中的反引号，使 `` CALL cat.`system`.`rollback_to_snapshot`() `` 这类写法能被正确识别。

之所以拆成两个 PR（#11579 给 3.4、#11580 给 3.3），是因为 Iceberg 对每个 Spark 版本（3.3、3.4、3.5）维护独立的源码分支（`spark/v3.3/`、`spark/v3.4/`、`spark/v3.5/`），代码不能跨版本共享，需要分别提交。

## 如何达成设计目的

与 1395 完全相同，区别仅在于作用路径为 `spark/v3.3/` 而非 `spark/v3.4/`：

1. 在 `SparkProcedures.java`（3.3 模块）新增 `names()` 静态方法，返回 `BUILDERS.keySet()`。
2. 在 `IcebergSparkSqlExtensionsParser.scala`（3.3 模块）新增 `isIcebergProcedure(normalized)` 方法，判断 SQL 是否调用 `system.<内置 procedure>`。
3. 把 `isIcebergCommand` 中的 `normalized.startsWith("call")` 替换为 `isIcebergProcedure(normalized)`。
4. 在 SQL 规范化阶段增加 `.replaceAll("`", "")` 清理反引号。
5. 更新 `TestCallStatementParser` 与各 procedure 测试，适配新拦截行为。

## 修改详情

### `spark/v3.3/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSparkSqlExtensionsParser.scala`

**修改目的**：与 1395 中 3.4 版本文件相同。

**工作逻辑**：

```scala
import org.apache.iceberg.spark.procedures.SparkProcedures
...
// Strip backtick then `system`.`ancestors_of` changes to system.ancestors_of
.replaceAll("`", "")
.trim
isIcebergProcedure(normalized) || (...)
...
// All builtin Iceberg procedures are under the 'system' namespace
private def isIcebergProcedure(normalized: String): Boolean = {
  normalized.startsWith("call") &&
    SparkProcedures.names().asScala.map("system." + _).exists(normalized.contains)
}
```

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java`

**修改目的**：暴露 procedure 名集合。

**工作逻辑**：
```java
import java.util.Set;
...
public static Set<String> names() {
  return BUILDERS.keySet();
}
```

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCallStatementParser.java`

**修改目的**：与 1395 同款测试适配。

**工作逻辑**：
- 新增 `testDelegateUnsupportedProcedure`：`CALL cat.d.t()` → `ParseException`（`PARSE_SYNTAX_ERROR` / `'CALL'`）。
- 新增 `testCallWithBackticks`：`` CALL cat.`system`.`rollback_to_snapshot`() `` 解析为 `["cat", "system", "rollback_to_snapshot"]`。
- 现有测试中 `func` 替换为 `rollback_to_snapshot`。
- `testCallParseError` 改用 `cat.system.rollback_to_snapshot kebab`，期望 `missing '(' at 'kebab'`。
- `testCallStripsComments` 全部 `func` 替换为 `rollback_to_snapshot`。

### `spark/v3.3/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCherrypickSnapshotProcedure.java`、`TestExpireSnapshotsProcedure.java`、`TestFastForwardBranchProcedure.java`、`TestPublishChangesProcedure.java`、`TestRemoveOrphanFilesProcedure.java`、`TestRewriteDataFilesProcedure.java`、`TestRewriteManifestsProcedure.java`、`TestRollbackToSnapshotProcedure.java`、`TestRollbackToTimestampProcedure.java`、`TestSetCurrentSnapshotProcedure.java`

**修改目的**：把对 `custom.<procedure>` 调用的期望异常从 `NoSuchProcedureException` 改为 `ParseException`（`PARSE_SYNTAX_ERROR` / `'CALL'`）。

**工作逻辑**：每个文件统一改动：import 替换 `NoSuchProcedureException` → `ParseException` + 新增 `Assert`；断言替换为校验 `PARSE_SYNTAX_ERROR` 与 `'CALL'`。

## 小结

- **成效**：Spark 3.3 模块获得与 3.4 相同的 procedure 拦截修复：只拦截内置 procedure、透传其它 call、支持反引号。
- **影响范围**：13 个文件、149 处新增、52 处删除；与 1395 对称。
- **回迁到 1.4.x 的注意事项**：
  - 与 1395 同理，建议回迁到 1.4.x（前提是 1.4.x 维护 Spark 3.3 模块）。
  - 必须与 1395 一起回迁，保持 Spark 3.3 与 3.4 行为一致；如果只回迁其中一个，会导致两个版本行为不一致，给用户带来困惑。
  - 注意 1.4.x 中 Spark 3.3 与 3.4 的 `SparkProcedures` 注册表内容可能略有差异（取决于 1.4.x 上各自新增了哪些 procedure），`names()` 反映各自实际注册情况，无需特殊对齐。
  - 同样需评估对依赖旧"贪心拦截"行为的下游测试或集成的破坏性。
