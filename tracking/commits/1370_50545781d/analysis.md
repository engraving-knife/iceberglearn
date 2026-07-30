# 提交 1370：Spark 3.5: Iceberg parser should passthrough unsupported procedure to delegate (#11480)

## 提交信息

- **序号**：1370 / 4088
- **哈希**：50545781d54adedb6e6f1e753323ccf37ba9030f
- **短哈希**：50545781d
- **日期**：2024-11-12（Wed Nov 13 00:29:08 2024 +0800）
- **作者**：Cheng Pan <chengpan@apache.org>
- **提交说明**：Spark 3.5: Iceberg parser should passthrough unsupported procedure to delegate (#11480)
- **PR/Issue**：#11480

## 总体目的

Iceberg 为 Spark 3.5 提供了 SQL 扩展解析器 `IcebergSparkSqlExtensionsParser`（通过 `spark.sql.extensions=org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions` 注册），它会"拦截"特定 SQL 语句用 Iceberg 自定义的 ANTLR 文法解析，而非 Spark 默认解析器。拦截判定逻辑在 `canNormalize`/`normalize` 相关方法中：原逻辑是只要 SQL（normalize 后）以 `call` 开头就由 Iceberg 解析器接管。

这带来一个问题：Spark 3.5+ 允许用户在任意 catalog 中注册自定义存储过程（通过 `Procedure` 接口与 `SupportsProcedure` catalog），用户可能写出 `CALL cat.custom.my_proc(...)` 这样的语句调用非 Iceberg 内置过程。由于 Iceberg 解析器无差别拦截所有 `CALL`，它在解析阶段能识别 `CALL` 语法（生成 `CallStatement`），但后续在 catalog 层查找过程时只在 Iceberg 内置的 `system` 命名空间下查找，导致非 Iceberg 过程调用抛出 `NoSuchProcedureException`，而非交给 Spark 默认解析器与 catalog 流程处理。

更隐蔽的是：在 Spark 3.5 上，如果未启用 Iceberg SQL 扩展，`CALL` 语句本应由 Spark 原生 `SparkSqlParser` 解析并路由到对应 catalog 的 `Procedure`；启用 Iceberg 扩展后，所有 `CALL` 被 Iceberg 截走，破坏了 Spark 原生的过程调用链路。

本提交修正拦截判定：**只有当 `CALL` 语句的目标过程名属于 Iceberg 内置过程集合（即 `system.<iceberg_procedure_name>`）时，才由 Iceberg 解析器接管；否则透传给 Spark 默认解析器（delegate）**，让非 Iceberg 过程走原生 Spark 流程。同时修复反引号处理：原 normalize 不剥离反引号，导致 ``CALL cat.`system`.`rollback_to_snapshot`()`` 无法被识别为 Iceberg 过程而错误透传。

## 如何达成设计目的

1. **暴露 Iceberg 过程名集合**：在 `SparkProcedures` 中新增 `public static Set<String> names()` 返回所有已注册 Iceberg 过程名（如 `rollback_to_snapshot`、`cherrypick_snapshot`、`expire_snapshots` 等）。
2. **重写 `CALL` 拦截判定**：在 `IcebergSparkSqlExtensionsParser` 中新增私有方法 `isIcebergProcedure(normalized)`，仅当 SQL 以 `call` 开头**且**包含 `system.<known_iceberg_procedure_name>` 时返回 true。把原先的 `normalized.startsWith("call")` 替换为 `isIcebergProcedure(normalized)`。
3. **剥离反引号**：在 normalize 流程中新增 `.replaceAll("`", "")`，让 `` `system`.`rollback_to_snapshot` `` 规整为 `system.rollback_to_snapshot`，确保 `isIcebergProcedure` 能正确匹配。
4. **测试适配**：
   - `TestCallStatementParser` 中原本用虚构的 `cat.system.func` 名字的测试改为用真实 Iceberg 过程名 `cat.system.rollback_to_snapshot`，确保仍被 Iceberg 解析器拦截；新增 `testDelegateUnsupportedProcedure` 验证非 Iceberg 过程透传后由 Spark 默认解析器抛 `PARSE_SYNTAX_ERROR`；新增 `testCallWithBackticks` 验证反引号场景。
   - 各具体过程测试（`TestCherrypickSnapshotProcedure` 等）中，原来断言"调用非 system 命名空间过程抛 `NoSuchProcedureException`"的用例，改为断言"抛 `ParseException`（PARSE_SYNTAX_ERROR at 'CALL'）"，因为现在这类语句会被透传给 Spark 默认解析器（默认解析器在未启用 Iceberg 扩展的上下文不识别 `CALL`）。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java`（修改，+5 行）

**修改目的**：暴露 Iceberg 内置过程名集合，供 parser 判定拦截使用。

**工作逻辑**：

```java
public static Set<String> names() {
  return BUILDERS.keySet();
}
```

`BUILDERS` 是 `SparkProcedures` 内部的 `Map<String, Supplier<ProcedureBuilder>>`，键即过程名（如 `rollback_to_snapshot`）。返回其 `keySet()`（不可变视图，因为 `ImmutableMap` 的 keySet 不可变）。同时新增 `import java.util.Set;`。

### `spark/v3.5/spark-extensions/src/main/scala/org/apache/spark/sql/catalyst/parser/extensions/IcebergSparkSqlExtensionsParser.scala`（修改，+12 -2 行）

**修改目的**：把 `CALL` 拦截条件从"任意 call"收紧为"call 已知 Iceberg 过程"，并剥离反引号。

**工作逻辑**：

1. **normalize 新增反引号剥离**：
   ```scala
   // Strip backtick then `system`.`ancestors_of` changes to system.ancestors_of
   .replaceAll("`", "")
   ```
   在原有 strip 注释（`/* ... */`）之后、`.trim()` 之前插入。这样 `CALL cat.`system`.`rollback_to_snapshot`()` 规整为 `CALL cat.system.rollback_to_snapshot()`，下游匹配才能命中。

2. **拦截判定替换**：
   ```scala
   // 旧
   normalized.startsWith("call") || (...)
   // 新
   isIcebergProcedure(normalized) || (...)
   ```

3. **新增 `isIcebergProcedure` 方法**：
   ```scala
   // All builtin Iceberg procedures are under the 'system' namespace
   private def isIcebergProcedure(normalized: String): Boolean = {
     normalized.startsWith("call") &&
     SparkProcedures.names().asScala.map("system." + _).exists(normalized.contains)
   }
   ```
   - 必要条件 1：SQL 以 `call` 开头（保持原行为，避免误拦截 `select` 等）；
   - 必要条件 2：SQL 中包含 `system.<已知过程名>` 之一（如 `system.rollback_to_snapshot`）。`SparkProcedures.names().asScala` 把 Java `Set<String>` 转为 Scala `Iterable[String]`，对每个名字拼接 `system.` 前缀，用 `exists(normalized.contains)` 判断 SQL 是否含该子串。

4. **新增 import**：`import org.apache.iceberg.spark.procedures.SparkProcedures`。

注意：用 `contains` 而非精确前缀匹配会有轻微误判风险（如 SQL 中字符串字面量里恰好出现 `system.rollback_to_snapshot`），但实践中 `CALL` 语句的目标过程名位于 `CALL` 之后、参数列表之前，正常使用不会触发误判，且这是性能与正确性的合理折中。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestCallStatementParser.java`（修改，+72 -53 行）

**修改目的**：适配新的拦截判定，并新增覆盖透传与反引号场景。

**工作逻辑**：

1. **新增 `testDelegateUnsupportedProcedure`**：
   ```java
   assertThatThrownBy(() -> parser.parsePlan("CALL cat.d.t()"))
       .isInstanceOf(ParseException.class)
       .satisfies(exception -> {
         ParseException parseException = (ParseException) exception;
         assertThat(parseException.getErrorClass()).isEqualTo("PARSE_SYNTAX_ERROR");
         assertThat(parseException.getMessageParameters().get("error")).isEqualTo("'CALL'");
       });
   ```
   `CALL cat.d.t()` 中 `d.t` 不是 `system.<iceberg_proc>`，故 Iceberg parser 不拦截，透传给 delegate（Spark 默认 `SparkSqlParser`）。在测试环境 delegate 不识别 `CALL`（未启用 Iceberg 扩展时 Spark 3.5 原生 parser 不支持 `CALL`），抛 `PARSE_SYNTAX_ERROR` at `'CALL'`。

2. **新增 `testCallWithBackticks`**：
   ```java
   CallStatement call = (CallStatement) parser.parsePlan("CALL cat.`system`.`rollback_to_snapshot`()");
   assertThat(seqAsJavaList(call.name())).containsExactly("cat", "system", "rollback_to_snapshot");
   ```
   验证反引号剥离后，Iceberg parser 正确拦截并解析为 `CallStatement`，name 部分为 `[cat, system, rollback_to_snapshot]`。

3. **既有用例替换过程名**：`testCallWithPositionalArgs`/`testCallWithNamedArgs`/`testCallWithMixedArgs`/`testCallWithTimestampArg`/`testCallWithVarSubstitution`/`testCallParseError`/`testCallStripsComments` 等用例中，把虚构的 `c.n.func`/`cat.system.func` 替换为真实 Iceberg 过程名 `c.system.rollback_to_snapshot`/`cat.system.rollback_to_snapshot`，确保这些用例仍被 Iceberg parser 拦截（否则会透传抛 `PARSE_SYNTAX_ERROR` 而非生成 `CallStatement`）。`testCallParseError` 的错误消息从 `missing '(' at 'radish'` 改为 `missing '(' at 'kebab'`（因输入语句相应调整）。

### 其余 9 个过程测试文件（`TestCherrypickSnapshotProcedure`、`TestExpireSnapshotsProcedure`、`TestFastForwardBranchProcedure`、`TestPublishChangesProcedure`、`TestRemoveOrphanFilesProcedure`、`TestRewriteDataFilesProcedure`、`TestRewriteManifestsProcedure`、`TestRollbackToSnapshotProcedure`、`TestRollbackToTimestampProcedure`、`TestSetCurrentSnapshotProcedure`）

**修改目的**：把"调用非 Iceberg 命名空间过程"的预期异常从 `NoSuchProcedureException` 改为 `ParseException`（PARSE_SYNTAX_ERROR）。

**工作逻辑**：每个文件中类似下面的断言：
```java
// 旧
assertThatThrownBy(() -> sql("CALL %s.custom.cherrypick_snapshot('n', 't', 1L)", catalogName))
    .isInstanceOf(NoSuchProcedureException.class)
    .hasMessage("Procedure custom.cherrypick_snapshot not found");

// 新
assertThatThrownBy(() -> sql("CALL %s.custom.cherrypick_snapshot('n', 't', 1L)", catalogName))
    .isInstanceOf(ParseException.class)
    .satisfies(exception -> {
      ParseException parseException = (ParseException) exception;
      assertThat(parseException.getErrorClass()).isEqualTo("PARSE_SYNTAX_ERROR");
      assertThat(parseException.getMessageParameters().get("error")).isEqualTo("'CALL'");
    });
```

原因：`custom.cherrypick_snapshot` 不匹配 `system.<iceberg_proc>` 模式，Iceberg parser 不拦截，透传给 Spark 默认 parser，后者抛 `PARSE_SYNTAX_ERROR`（在测试上下文 delegate 不识别 `CALL`）。各文件的 import 相应调整（移除 `NoSuchProcedureException`，新增 `ParseException`，新增 `assertThat` 静态导入）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/SparkProcedures.java` 的 import 调整

新增 `import java.util.Set;`，用于 `names()` 方法返回类型。

## 小结

- **成效**：修复 Iceberg Spark 3.5 SQL 扩展解析器无差别拦截所有 `CALL` 语句导致非 Iceberg 自定义过程调用失败的问题。修复后，只有目标过程名为 Iceberg 内置过程（`system.<iceberg_proc>`）时才由 Iceberg parser 接管，其它 `CALL` 透传给 Spark 默认解析器，恢复 Spark 原生过程调用链路。同时修复反引号包裹过程名（`` `system`.`rollback_to_snapshot` ``）无法被识别的次级 bug。
- **影响范围**：Spark 3.5 模块的 parser（1 个 scala 文件）+ `SparkProcedures`（1 个 java 文件）+ 11 个测试文件。仅 Spark 3.5 模块受影响，Spark 3.3/3.4 未修改（可能存在相同 bug，需单独评估）。对启用 Iceberg SQL 扩展的 Spark 3.5 用户是行为变化：原先会被 Iceberg 截获并抛 `NoSuchProcedureException` 的非 Iceberg 过程调用，现在会透传给 Spark 原生流程。
- **回迁到 1.4.x 的注意事项**：bug 修复类变更，回迁安全且推荐。需注意：
  1. 1.4.x 上若 Spark 3.5 模块的 `IcebergSparkSqlExtensionsParser` 结构与 main 一致可直接 cherry-pick；若 1.4.x 还支持 Spark 3.3/3.4，相同 bug 可能也存在，应同步修复（本提交未覆盖）；
  2. 回迁测试时需同步修改 11 个测试文件，否则 `TestCallStatementParser` 等用例会因虚构过程名 `func` 不再被拦截而失败；
  3. `SparkProcedures.names()` 是新增 public API，回迁后成为 1.4.x 的公共方法（虽然仅 parser 内部使用），需确认无 ABI 影响；
  4. 行为变化：1.4.x 用户若依赖"Iceberg 扩展拦截所有 CALL"的旧行为（如自定义拦截逻辑），回迁后会改变——这是修复的目的，但需在 release notes 中说明；
  5. `contains` 子串匹配的轻微误判风险在 1.4.x 同样存在，若用户报告误拦截可考虑改为更精确的语法树匹配。
