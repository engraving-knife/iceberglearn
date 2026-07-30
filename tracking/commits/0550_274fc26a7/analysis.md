# 提交 0550：Spark 3.4: Improve error msg when function can't be loaded

## 提交信息

- **序号**：0550 / 4088
- **哈希**：274fc26a7f2060ea9b52aec409c66c9655274017
- **短哈希**：274fc26a7
- **日期**：2024-02-28 16:03:12 +0100
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Improve error msg when function can't be loaded (#9821)
- **PR/Issue**：#9821

## 总体目的

本提交是提交 0548（#9814，Spark 3.5 版本）向 Spark 3.4 模块的回port（backport），目的完全一致：改进 Iceberg Spark 自定义 Catalog 在加载函数失败时抛出的错误信息，把 Spark 默认生成的 `[ROUTINE_NOT_FOUND] The function X cannot be found.` 替换为更明确的 `Cannot load function: <catalog>.<namespace>.<function>`，包含 catalog 名称，便于多 catalog 环境下定位问题。

由于 Iceberg 同时维护 Spark 3.4 和 Spark 3.5 两个分支（`spark/v3.4/` 与 `spark/v3.5/` 目录并行），任何产品代码改动都需在两个分支同步应用。本提交完成 3.4 侧的同步。

## 如何达成设计目的

设计思路与 0548 完全相同：在 `SupportsFunctions.loadFunction` 抛出 `NoSuchFunctionException` 时，改用接受自定义 message 的构造器重载 `NoSuchFunctionException(String message, Option<Throwable> cause)`，传入 `String.format("Cannot load function: %s.%s", name(), ident)` 覆盖 Spark 默认消息。同时同步更新两个测试的断言。

与 0548 的唯一差异在于测试代码所处的 JUnit 版本上下文：

| 方面 | 0548（Spark 3.5） | 0550（Spark 3.4） |
|---|---|---|
| `SupportsFunctions.java` 改动 | 完全相同 | 完全相同 |
| `TestViews.java` 基类 | `ExtensionsTestBase`（JUnit5） | `SparkExtensionsTestBase`（JUnit4） |
| `TestViews.java` 测试注解 | `@TestTemplate` | `@Test` |
| `TestFunctionCatalog.java` 基类 | `TestBaseWithCatalog`（JUnit5） | `SparkTestBaseWithCatalog`（JUnit4） |
| `TestFunctionCatalog.java` 断言调用 | 静态导入 `assertThatThrownBy(...)` | 限定调用 `Assertions.assertThatThrownBy(...)` |
| `TestFunctionCatalog.java` 测试注解 | `@TestTemplate` | `@Test` |

产品代码（`SupportsFunctions.java`）的改动在两个 Spark 版本间逐字节一致；测试代码的断言文本改动也一致，仅因 3.4 仍处 JUnit4 栈而保留 `@Test`、`Assertions.assertThatThrownBy` 等 JUnit4 风格。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SupportsFunctions.java`

**修改目的**：核心改动，与 0548 完全一致。让 `loadFunction` 在找不到函数时抛出含 catalog 名称的明确错误信息。

**工作逻辑**：
修改前：
```java
throw new NoSuchFunctionException(ident);
```
修改后：
```java
throw new NoSuchFunctionException(
    String.format("Cannot load function: %s.%s", name(), ident), Option.empty());
```
- 走 `NoSuchFunctionException(String, Option<Throwable>)` 构造器，传入自定义 message。
- `name()` 返回 catalog 名（来自 `FunctionCatalog` 接口），`ident.toString()` 返回 `namespace.name`，组合后消息如 `Cannot load function: spark_catalog.system.iceberg_version`。
- `Option.empty()` 是 Scala 的空 Option，表示无原始 cause 异常，需新增 `import scala.Option;`。
- 该文件与 `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SupportsFunctions.java` 在改动前后均逐字节一致（Iceberg 维护两 Spark 版本同构）。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/TestFunctionCatalog.java`

**修改目的**：同步更新 `testLoadFunctions` 中两个失败场景的断言，匹配新错误信息。

**工作逻辑**：
- 第一个场景（`Identifier.of(DEFAULT_NAMESPACE, "iceberg_version")` 加载失败）：
  - 原断言：`.hasMessageStartingWith("[ROUTINE_NOT_FOUND] The function default.iceberg_version cannot be found.")`
  - 新断言：`.hasMessageStartingWith(String.format("Cannot load function: %s.default.iceberg_version", catalogName))`
- 第二个场景（`Identifier.of(SYSTEM_NAMESPACE, "undefined_function")` 加载失败）：
  - 原断言：`.hasMessageStartingWith("[ROUTINE_NOT_FOUND] The function system.undefined_function cannot be found.")`
  - 新断言：`.hasMessageStartingWith(String.format("Cannot load function: %s.system.undefined_function", catalogName))`
- 与 0548 的差异：本文件仍使用 `Assertions.assertThatThrownBy(...)`（限定调用，非静态导入）和 `@Test`（JUnit4），因 Spark 3.4 此时尚未完成 JUnit5 迁移。断言文本改动本身与 0548 一致。
- `sql("SELECT undefined_function(1, 2)")` 的断言未改，仍为 `[UNRESOLVED_ROUTINE]...`，因该路径走 Spark Analyzer，不经过 Iceberg `loadFunction`。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`

**修改目的**：更新 `readFromViewReferencingTempFunction` 测试中视图读取失败的断言。

**工作逻辑**：
- 原断言（三个 `hasMessageContaining` 组合）：
  ```java
  .hasMessageContaining("The function")
  .hasMessageContaining(functionName)
  .hasMessageContaining("cannot be found");
  ```
- 新断言（一个 `hasMessageStartingWith`）：
  ```java
  .hasMessageStartingWith(
      String.format("Cannot load function: %s.%s.%s", catalogName, NAMESPACE, functionName));
  ```
- 与 0548 的差异：本文件 `readFromViewReferencingTempFunction` 仍用 `@Test`（JUnit4）而非 `@TestTemplate`，类仍 `extends SparkExtensionsTestBase`（JUnit4 基类）而非 `ExtensionsTestBase`。断言文本改动本身与 0548 一致。
- 该测试场景：通过 ViewCatalog API 创建引用 TEMP FUNCTION 的视图，读取视图时 Spark 通过 catalog 解析函数引用失败，触发 `loadFunction` 抛出新错误信息。

## 小结

本提交是 0548（#9814）向 Spark 3.4 的同步回port，产品代码改动（`SupportsFunctions.java`）与 0548 逐字节一致，测试断言文本改动也一致，仅因 Spark 3.4 此时尚未完成 JUnit5 迁移而保留 JUnit4 测试风格（`@Test`、`Assertions.assertThatThrownBy`、`SparkExtensionsTestBase`/`SparkTestBaseWithCatalog` 基类）。

**与 0548 的关联**：
- 0548 是主提交（Spark 3.5），0550 是其 Spark 3.4 回port。两者由同一作者 Eduard Tudenhoefner 在同一天（2024-02-27/28）相继提交，PR 号相邻（#9814 → #9821）。
- 产品逻辑完全相同，无版本特定差异。Spark 3.4 与 3.5 的 `SupportsFunctions` 接口签名一致，`NoSuchFunctionException` 构造器在两个 Spark 版本中均可用。
- 测试基础设施差异（JUnit4 vs JUnit5）是两个 Spark 版本迁移进度不同导致的，与本提交的功能改动无关。

**回迁到 1.4.x 的注意事项**：
- 若 1.4.x 同时维护 Spark 3.4 和 3.5 适配，0548 和 0550 应一并回迁，保持两个 Spark 版本的错误信息行为一致。
- 本提交不含破坏性变更，错误信息字符串变化可能影响依赖原 `[ROUTINE_NOT_FOUND]` 字符串的下游测试/日志解析，但 Iceberg 自身测试已同步更新。
- `scala.Option` import 需要确保 scala-library 在 classpath（Spark 依赖传递引入，通常无需额外声明）。
- 若 1.4.x 的 Spark 3.4 测试栈已完成 JUnit5 迁移（参考 Spark 3.4 的 #12501、#12552、#12600、#12744 等 PR），则回迁时测试代码的 JUnit4 风格需相应调整为 JUnit5 风格；若未迁移，则可直接应用本提交的 JUnit4 风格测试改动。
