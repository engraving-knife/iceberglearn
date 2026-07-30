# 提交 0548：Spark: Improve error msg when function can't be loaded

## 提交信息

- **序号**：0548 / 4088
- **哈希**：bd9c615d5d2fedb1a9fda6eb018017d0cc32f04e
- **短哈希**：bd9c615d5
- **日期**：2024-02-27 19:54:04 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Improve error msg when function can't be loaded (#9814)
- **PR/Issue**：#9814

## 总体目的

本提交改进 Iceberg Spark 3.5 自定义 Catalog 在加载函数失败时抛出的错误信息。此前 `SupportsFunctions.loadFunction` 在找不到函数时直接调用 `throw new NoSuchFunctionException(ident)`，触发 Spark 框架自动生成的默认错误信息，形如 `[ROUTINE_NOT_FOUND] The function system.undefined_function cannot be found.`。该默认信息存在两个问题：

1. **不包含 catalog 名称**：当用户在多 catalog 环境下（如同时使用 `spark_catalog`、`my_catalog` 等多个 Iceberg catalog）遇到函数加载失败时，错误信息无法定位是哪个 catalog 抛出的，排查困难。
2. **错误码前缀对终端用户不友好**：`[ROUTINE_NOT_FOUND]` 是 Spark 内部 SQLSTATE 风格的错误码，对普通用户而言含义不直观，且在视图（view）引用临时函数（TEMP FUNCTION）这类场景下，用户看到 "The function X cannot be found" 时难以联想到根本原因是 "Iceberg catalog 无法加载该函数"。

本提交将错误信息改为 `Cannot load function: <catalog_name>.<namespace>.<function_name>`，明确表达"Iceberg catalog 无法加载函数"的语义，并包含完整的 catalog + namespace + function 三段标识，便于用户定位问题。

## 如何达成设计目的

设计思路是：在 `SupportsFunctions.loadFunction` 抛出 `NoSuchFunctionException` 时，改用接受自定义 message 的构造器重载，传入格式化后的明确信息，覆盖 Spark 默认生成的消息。

具体实现：
- `NoSuchFunctionException` 是 Spark 的 Scala 类（位于 `org.apache.spark.sql.catalyst.analysis`），提供两个构造器：
  - `NoSuchFunctionException(Identifier ident)`：根据 Identifier 自动生成默认错误信息（含 `[ROUTINE_NOT_FOUND]` 前缀）。
  - `NoSuchFunctionException(String message, Option<Throwable> cause)`：接受自定义 message 和 Scala `Option<Throwable>` 类型的 cause。
- 本提交改用第二个构造器：`new NoSuchFunctionException(String.format("Cannot load function: %s.%s", name(), ident), Option.empty())`。
  - `name()` 是 `FunctionCatalog` 接口的方法，返回当前 catalog 的名字（如 `spark_catalog`、`my_catalog`）。
  - `ident`（`Identifier` 类型）的 `toString()` 返回 `namespace.name` 形式（如 `system.iceberg_version`、`default.undefined_function`）。
  - 因此最终消息形如 `Cannot load function: spark_catalog.system.iceberg_version`，完整包含 catalog、namespace、function 三段。
  - `Option.empty()` 表示无 cause 异常（Scala `Option` 类型，因 Spark 是 Scala 实现，Java 侧调用需显式包装）。
- 新增 `import scala.Option;` 以引用 Scala 的 Option 类型。

同时同步更新两个测试，把对 Spark 默认错误信息的断言改为对新消息的断言：
- `TestFunctionCatalog`：两个 `loadFunction` 失败场景的断言由 `hasMessageStartingWith("[ROUTINE_NOT_FOUND] The function ...")` 改为 `hasMessageStartingWith(String.format("Cannot load function: %s....", catalogName))`，且新断言动态引用 `catalogName` 字段（不再硬编码 `default`/`system`），更健壮。
- `TestViews.readFromViewReferencingTempFunction`：视图引用 TEMP FUNCTION 后读取视图会触发函数加载失败，错误信息断言由三个 `hasMessageContaining`（"The function"、functionName、"cannot be found"）改为一个 `hasMessageStartingWith(String.format("Cannot load function: %s.%s.%s", catalogName, NAMESPACE, functionName))`，与新消息格式一致。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SupportsFunctions.java`

**修改目的**：核心改动。让 `loadFunction` 在找不到函数时抛出含 catalog 名称的明确错误信息。

**工作逻辑**：
`SupportsFunctions` 是 Iceberg Spark Catalog 的混入（mixin）接口，提供 `loadFunction` 的默认实现：先检查 namespace 是否为函数 namespace（长度为 0），若是则尝试通过 `SparkFunctions.load(name)` 加载内置函数；若加载不到或 namespace 不匹配，则抛 `NoSuchFunctionException`。

修改前：
```java
throw new NoSuchFunctionException(ident);
```
此调用走 `NoSuchFunctionException(Identifier)` 构造器，Spark 内部根据 ident 生成形如 `[ROUTINE_NOT_FOUND] The function system.iceberg_version cannot be found.` 的默认消息。

修改后：
```java
throw new NoSuchFunctionException(
    String.format("Cannot load function: %s.%s", name(), ident), Option.empty());
```
- 走 `NoSuchFunctionException(String, Option<Throwable>)` 构造器，传入自定义 message。
- `name()` 返回 catalog 名（来自 `FunctionCatalog` 接口），`ident.toString()` 返回 `namespace.name`，组合后消息如 `Cannot load function: spark_catalog.system.iceberg_version`。
- `Option.empty()` 是 Scala 的空 Option，表示无原始 cause 异常。需新增 `import scala.Option;`。

这样错误信息明确指出"加载函数失败"，并完整列出 catalog、namespace、function 三段标识，便于多 catalog 场景下定位。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/TestFunctionCatalog.java`

**修改目的**：同步更新 `testLoadFunctions` 中两个失败场景的断言，匹配新错误信息。

**工作逻辑**：
- 第一个场景：尝试用 `Identifier.of(DEFAULT_NAMESPACE, "iceberg_version")` 加载函数，`DEFAULT_NAMESPACE` 通常是 `["default"]`，不属于 Iceberg 函数 namespace（长度非 0），故抛异常。
  - 原断言：`.hasMessageStartingWith("[ROUTINE_NOT_FOUND] The function default.iceberg_version cannot be found.")`
  - 新断言：`.hasMessageStartingWith(String.format("Cannot load function: %s.default.iceberg_version", catalogName))`
- 第二个场景：尝试用 `Identifier.of(SYSTEM_NAMESPACE, "undefined_function")` 加载未定义函数，`SYSTEM_NAMESPACE` 是 `["system"]`，虽 namespace 长度非 0 但 `SparkFunctions.load("undefined_function")` 返回 null。
  - 原断言：`.hasMessageStartingWith("[ROUTINE_NOT_FOUND] The function system.undefined_function cannot be found.")`
  - 新断言：`.hasMessageStartingWith(String.format("Cannot load function: %s.system.undefined_function", catalogName))`
- 新断言使用 `catalogName` 变量动态拼接，比原来硬编码 `default`/`system` 更健壮——若测试参数化切换 catalog，断言仍能通过。

注意：`sql("SELECT undefined_function(1, 2)")` 这个走 SQL 解析路径的断言未改，仍为 `[UNRESOLVED_ROUTINE] Cannot resolve function ...`，因为这条 SQL 在解析阶段就被 Spark 的 Analyzer 拦截，未走到 Iceberg catalog 的 `loadFunction`，错误信息由 Spark 自身生成。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：更新 `readFromViewReferencingTempFunction` 测试中视图读取失败的断言。

**工作逻辑**：
该测试通过 ViewCatalog API 创建一个引用了 TEMP FUNCTION（`test_avg`）的视图。直接执行 `SELECT test_avg(id) FROM table` 可成功，但读取视图 `SELECT * FROM viewName` 时，Spark 需要通过 catalog 重新解析视图 SQL 中的函数引用，TEMP FUNCTION 不在 catalog 中，触发 `loadFunction` 失败。

- 原断言（三个 `hasMessageContaining` 组合）：
  ```java
  .hasMessageContaining("The function")
  .hasMessageContaining(functionName)
  .hasMessageContaining("cannot be found");
  ```
  匹配的是 Spark 默认消息 `[ROUTINE_NOT_FOUND] The function ... cannot be found.`。
- 新断言（一个 `hasMessageStartingWith`）：
  ```java
  .hasMessageStartingWith(
      String.format("Cannot load function: %s.%s.%s", catalogName, NAMESPACE, functionName));
  ```
  匹配新的 `Cannot load function: <catalog>.<namespace>.<function>` 消息。`NAMESPACE` 是 `Namespace.of("default")`，其 `toString()` 返回 `default`，所以最终形如 `Cannot load function: spark_catalog.default.test_avg`。

由 `hasMessageContaining` 改为 `hasMessageStartingWith` 是因为新消息格式固定且可预测，前缀匹配更严格、更稳定（避免误匹配其他含 "function" 字样的消息）。

## 小结

本提交是 Spark 3.5 模块的小幅 UX 改进：把 Iceberg catalog 函数加载失败的错误信息从 Spark 默认的 `[ROUTINE_NOT_FOUND] The function X cannot be found.` 改为更明确的 `Cannot load function: <catalog>.<namespace>.<function>`，包含 catalog 名称，便于多 catalog 环境定位。改动仅 3 文件 7 行（增）6 行（减），核心是 `SupportsFunctions.loadFunction` 一行构造器调用替换，其余为测试断言同步。

**关键设计点**：
- 利用 `NoSuchFunctionException(String, Option<Throwable>)` 构造器重载覆盖默认消息，无需继承或包装异常类。
- `Option.empty()` 体现 Scala/Java 互操作细节——Spark 异常构造器签名要求 Scala `Option`，Java 侧需显式构造。
- 测试断言从 `hasMessageContaining` 升级为 `hasMessageStartingWith`，更严格、更稳定。

**回迁到 1.4.x 的注意事项**：
- 本提交不含破坏性变更，错误信息字符串变化可能影响依赖原 `[ROUTINE_NOT_FOUND]` 字符串的下游测试/日志解析，但 Iceberg 自身测试已同步更新。
- 1.4.x 若维护 Spark 3.5 适配，可直接 cherry-pick `SupportsFunctions.java` 改动；测试改动需视 1.4.x 中 `TestFunctionCatalog`/`TestViews` 的基类与已有断言风格而定。
- 本提交有对应的 Spark 3.4 回port（提交 0550 / #9821），改动几乎一致，仅路径与基类（Spark 3.4 仍为 JUnit4 风格）不同。1.4.x 若同时维护 Spark 3.4 和 3.5，建议两个 PR 一并回迁。
- `scala.Option` import 需要确保项目已依赖 scala-library（Spark 依赖传递引入，通常无需额外声明）。
