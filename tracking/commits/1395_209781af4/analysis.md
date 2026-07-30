# 提交 1395：Spark 3.4: Iceberg parser should passthrough unsupported procedure to delegate (#11579)

## 提交信息

- **序号**：1395 / 4088
- **哈希**：209781af4f48cbde23a864ae6da98022eccb4708
- **短哈希**：209781af4
- **日期**：2024-11-19（Tue Nov 19 04:11:17 2024 +0800）
- **作者**：Cheng Pan <chengpan@apache.org>
- **提交说明**：Spark 3.4: Iceberg parser should passthrough unsupported procedure to delegate (#11579)
- **PR/Issue**：#11579

## 总体目的

Iceberg 通过 Spark 的扩展解析器（`IcebergSparkSqlExtensionsParser`）拦截 SQL 语句。当 SQL 以 `call` 开头时，解析器会尝试把它当作 Iceberg 的 Call 语句（`CallStatement`）来解析。这种"激进拦截"导致一个问题：用户调用任何非 Iceberg 内置的 procedure（例如 Spark 内置 procedure、第三方 catalog 注册的 procedure、或拼写错误的 procedure 名）时，Iceberg 解析器会先尝试用自己的语法解析，要么报出令人困惑的 `IcebergParseException`（如 "missing '(' at 'radish'"），要么把调用吞掉后无法找到 procedure 抛出 `NoSuchProcedureException`。用户期望的体验是：Iceberg 解析器只拦截它自己支持的 procedure，对不支持的 procedure 应该"透传"给 Spark 原生解析器（delegate），由 Spark 自己处理，从而给出准确的错误信息或正确执行。

本提交针对 Spark 3.4 修复该问题：解析器在判断是否需要拦截 `call` 语句时，不再无条件拦截所有 `call`，而是只拦截那些调用 Iceberg 内置 procedure（即名字位于 `system.<procedure_name>` 命名空间下、且 `procedure_name` 在 `SparkProcedures` 注册表中）的语句。其它 `call` 语句透传给 delegate。同时清理 SQL 中的反引号（backtick），因为用户常写 `` CALL cat.`system`.`rollback_to_snapshot`(...) `` 这种带反引号的写法，反引号会干扰名字匹配。

## 如何达成设计目的

1. **新增 `SparkProcedures.names()` 静态方法**：返回所有已注册的 Iceberg procedure 名字集合（`BUILDERS.keySet()`），供解析器查询"哪些 procedure 是 Iceberg 内置的"。

2. **新增 `isIcebergProcedure(normalized)` 私有方法**：判断规范化后的 SQL 是否是调用 Iceberg 内置 procedure。判断条件：以 `call` 开头，且 SQL 中包含 `system.<某个内置 procedure 名>` 子串。

3. **修改 `isIcebergCommand` 判断**：把 `normalized.startsWith("call")` 替换为 `isIcebergProcedure(normalized)`，从而只拦截 Iceberg 内置 procedure 调用，其它 `call` 透传给 delegate。

4. **清理反引号**：在规范化阶段增加 `.replaceAll("`", "")`，把 `` `system`.`rollback_to_snapshot` `` 规范化为 `system.rollback_to_snapshot`，使后续名字匹配能命中。注释中举的例子 `` `system`.`ancestors_of` `` 说明反引号包裹的多段名字在去除反引号后才能被 `isIcebergProcedure` 正确识别。

5. **更新测试**：原有 `TestCallStatementParser` 中多个测试用例使用 `cat.system.func` 这种虚构的 procedure 名（`func` 不在 Iceberg 注册表中），在新逻辑下会被透传给 delegate 而非被 Iceberg 解析，导致测试失败。因此把测试中的 `func` 替换为真实的 Iceberg procedure 名 `rollback_to_snapshot`，使 Iceberg 解析器仍然能拦截并解析这些 Call 语句。同时新增两个测试：
   - `testDelegateUnsupportedProcedure`：验证 `CALL cat.d.t()`（非 Iceberg procedure）会抛出 Spark 原生 `ParseException`（`PARSE_SYNTAX_ERROR`，`'CALL'`），而不是 Iceberg 自己的异常。
   - `testCallWithBackticks`：验证带反引号的 `` CALL cat.`system`.`rollback_to_snapshot`() `` 能被正确解析为 `CallStatement`，name 为 `["cat", "system", "rollback_to_snapshot"]`。

6. **更新各 procedure 测试**：`TestCherrypickSnapshotProcedure`、`TestExpireSnapshotsProcedure` 等中，原有断言 `CALL %s.custom.expire_snapshots(...)` 抛出 `NoSuchProcedureException`（"Procedure custom.expire_snapshots not found"）。新逻辑下，由于 `custom.expire_snapshots` 不是 `system.<内置名>`，会被透传给 delegate，delegate 把 `CALL` 当作语法错误抛出 `ParseException`（`PARSE_SYNTAX_ERROR`，`'CALL'`）。因此测试断言从 `NoSuchProcedureException` 改为 `ParseException` 并校验错误类与参数。

## 修改详情

### `spark/v3.4/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSparkSqlExtensionsParser.scala`

**修改目的**：让 Iceberg 解析器只拦截内置 procedure 调用，透传其它 call 语句；并兼容反引号写法。

**工作逻辑**：

新增 import：
```scala
import org.apache.iceberg.spark.procedures.SparkProcedures
```

在 `isIcebergCommand` 中规范化 SQL 时追加反引号清理：
```scala
.replaceAll("/\\*.*?\\*/", " ")
// Strip backtick then `system`.`ancestors_of` changes to system.ancestors_of
.replaceAll("`", "")
.trim
```

把拦截条件从：
```scala
normalized.startsWith("call") || (...)
```
改为：
```scala
isIcebergProcedure(normalized) || (...)
```

新增方法：
```scala
// All builtin Iceberg procedures are under the 'system' namespace
private def isIcebergProcedure(normalized: String): Boolean = {
  normalized.startsWith("call") &&
    SparkProcedures.names().asScala.map("system." + _).exists(normalized.contains)
}
```

逻辑：先确认是 `call` 语句，再把所有内置 procedure 名加上 `system.` 前缀，检查规范化后的 SQL 是否包含其中任意一个。例如 `system.rollback_to_snapshot`、`system.expire_snapshots` 等。包含则拦截，否则透传。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java`

**修改目的**：暴露已注册 procedure 名集合，供解析器查询。

**工作逻辑**：
```java
import java.util.Set;
...
public static Set<String> names() {
  return BUILDERS.keySet();
}
```

`BUILDERS` 是 `SparkProcedures` 内部维护的 `Map<String, Supplier<ProcedureBuilder>>`，key 即 procedure 名（如 `rollback_to_snapshot`、`expire_snapshots`、`rewrite_data_files` 等）。`names()` 返回其 keySet，是只读视图。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCallStatementParser.java`

**修改目的**：适配新拦截逻辑，并新增覆盖透传与反引号的测试。

**工作逻辑**：

新增 `testDelegateUnsupportedProcedure`：调用 `parser.parsePlan("CALL cat.d.t()")`，期望抛出 `ParseException`，错误类 `PARSE_SYNTAX_ERROR`，错误参数 `error` = `'CALL'`。这验证非 Iceberg procedure 透传给 delegate 后，delegate 不认识 `CALL` 关键字（Spark 原生解析器在 Spark 3.4 默认不支持 `CALL` 语法，所以报语法错误）。

新增 `testCallWithBackticks`：解析 `` CALL cat.`system`.`rollback_to_snapshot`() ``，期望得到 `CallStatement`，name 为 `["cat", "system", "rollback_to_snapshot"]`，参数 0 个。验证反引号清理生效。

修改现有测试：把 `CALL c.n.func(...)`、`CALL cat.system.func(...)` 等中的 `func` 替换为 `rollback_to_snapshot`，因为 `func` 不在 Iceberg 注册表中，新逻辑下不会被 Iceberg 拦截。同时把 `testCallParseError` 中 `CALL cat.system radish kebab` 改为 `CALL cat.system.rollback_to_snapshot kebab`，期望错误信息从 `missing '(' at 'radish'` 改为 `missing '(' at 'kebab'`（因为 `rollback_to_snapshot` 是单个 token，`kebab` 才是第一个非法 token）。

`testCallStripsComments` 中所有 `cat.system.func` 替换为 `cat.system.rollback_to_snapshot`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCherrypickSnapshotProcedure.java`

**修改目的**：更新 `custom.cherrypick_snapshot` 调用的期望异常。

**工作逻辑**：把 import 从 `NoSuchProcedureException` 改为 `ParseException`，新增 `Assert` import。断言：
```java
assertThatThrownBy(() -> sql("CALL %s.custom.cherrypick_snapshot('n', 't', 1L)", catalogName))
    .isInstanceOf(ParseException.class)
    .satisfies(exception -> {
      ParseException parseException = (ParseException) exception;
      Assert.assertEquals("PARSE_SYNTAX_ERROR", parseException.getErrorClass());
      Assert.assertEquals("'CALL'", parseException.getMessageParameters().get("error"));
    });
```

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestExpireSnapshotsProcedure.java`、`TestFastForwardBranchProcedure.java`、`TestPublishChangesProcedure.java`、`TestRemoveOrphanFilesProcedure.java`、`TestRewriteDataFilesProcedure.java`、`TestRewriteManifestsProcedure.java`、`TestRollbackToSnapshotProcedure.java`、`TestRollbackToTimestampProcedure.java`、`TestSetCurrentSnapshotProcedure.java`

**修改目的**：与 `TestCherrypickSnapshotProcedure` 同理，把这些 procedure 测试中对 `custom.<procedure>` 调用的期望异常从 `NoSuchProcedureException` 改为 `ParseException`（`PARSE_SYNTAX_ERROR` / `'CALL'`）。

**工作逻辑**：每个文件统一改动：
- import 替换：`NoSuchProcedureException` → `ParseException`，新增 `Assert`。
- 断言替换：`isInstanceOf(NoSuchProcedureException.class).hasMessage("Procedure ... not found")` → `isInstanceOf(ParseException.class).satisfies(...)` 校验 `PARSE_SYNTAX_ERROR` 与 `'CALL'`。

## 小结

- **成效**：Iceberg Spark 3.4 扩展解析器不再"贪心"拦截所有 `call` 语句，只拦截调用 `system.<内置 procedure>` 的语句；其余 `call` 透传给 Spark 原生解析器，给出准确的语法错误而非误导性的 `NoSuchProcedureException`。同时支持反引号包裹的 procedure 名，提升用户体验。
- **影响范围**：13 个文件、157 处新增、52 处删除；核心改动在解析器 Scala 文件与 `SparkProcedures.java`，其余为测试适配。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个用户体验与互操作性修复，建议回迁到 1.4.x（前提是 1.4.x 仍维护 Spark 3.4 模块）。
  - 回迁时需同步回迁 `SparkProcedures.names()` 新增方法与解析器 Scala 改动，以及全部相关测试。
  - 注意：1.4.x 中如果某些 procedure 名与 main 不同（例如 1.4.x 还没有 main 上新增的 procedure），`names()` 返回的集合会反映 1.4.x 实际注册的 procedure，这是正确行为，无需特殊处理。
  - 该改动改变了 `call` 语句的拦截行为，对依赖旧"贪心拦截"行为的下游测试或集成有破坏性，需谨慎评估。例如有用户依赖 `CALL custom.my_proc()` 被 Iceberg 报 `NoSuchProcedureException` 的旧行为，回迁后会变成 Spark 原生 `PARSE_SYNTAX_ERROR`，错误消息变化需告知用户。
  - 与 1396（Spark 3.3 同款修复）应一起回迁，保持 Spark 各版本行为一致。
