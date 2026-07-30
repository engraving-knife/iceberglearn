# 提交 1268：Spark: Randomize view/function names in testing (#11381)

## 提交信息

- **序号**：1268 / 4088
- **哈希**：97946088a8977e47ad996220186d4e137edc3d28
- **短哈希**：97946088a
- **日期**：2024-10-23（Wed Oct 23 17:32:30 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark: Randomize view/function names in testing (#11381)
- **PR/Issue**：#11381

## 总体目的

`TestViews` 是 Spark 3.5 扩展模块中针对 Iceberg View 功能的大型集成测试类，包含数十个测试用例，覆盖视图的创建、读取、删除、属性修改、CTE 引用、临时视图/函数交互等场景。该测试类原本存在两个问题：

1. **视图名/函数名硬编码导致测试间污染**：绝大多数测试用例直接使用硬编码的字符串字面量（如 `"simpleView"`、`"trinoView"`、`"test_avg"` 等）作为视图名和临时函数名。由于这些测试运行在共享的 SparkSession 上，前一个测试创建的视图/函数如果在清理（`@AfterEach`）之前失败或遗漏，会残留到后续测试中，导致 `CREATE VIEW` 报"已存在"错误、`SHOW VIEWS` 返回意外行、或者临时函数名冲突等问题，造成测试间歇性失败（flaky test）。虽然类中已有一个 `viewName(String)` 辅助方法（拼接随机数后缀），但绝大多数测试并未使用它。

2. **`toLowerCase` 缺少 `Locale` 参数**：`showViews` 测试中在比对临时视图名时使用了硬编码的小写字符串（如 `"tempviewforlisting"`），而 Spark 内部以大小写不敏感方式存储临时视图名。原代码直接写死小写形式来匹配，但更健壮的做法是对实际视图名调用 `toLowerCase(Locale.ROOT)` 进行转换，避免在不同 Locale 环境下出现意外的字符转换差异。

本提交的目标是消除上述两类测试不稳定性来源，使 `TestViews` 在并行/反复运行时不再因名称冲突而失败。

## 如何达成设计目的

整体思路是"让每个测试使用唯一的随机化名称"：

1. **统一使用 `viewName()` 辅助方法**：将测试类中所有硬编码的视图名（包括 Iceberg View、Temp View、Global Temp View、V1 View）和临时函数名（如 `test_avg`、`test_avg_func` 等）统一替换为 `viewName(baseName)` 调用。辅助方法 `viewName(String)` 在拼接时附加一个随机整数后缀（`viewName + new Random().nextInt(1000000)`），使得每次测试运行时名称几乎不会重复，从而彻底消除跨测试、跨运行的名称冲突。

2. **`showViews` 测试适配随机化名称**：由于 `showViews` 测试需要断言 `SHOW VIEWS` 返回的行内容，而视图名现在变成了随机值，因此将断言中的硬编码字符串替换为对应的变量引用（如 `prefixV2`、`v1` 等）。对于临时视图，Spark 以大小写不敏感方式存储，因此使用 `tempViewForListing.toLowerCase(Locale.ROOT)` 来匹配 Spark 返回的小写形式。

3. **引入 `Locale` 导入**：新增 `import java.util.Locale`，供上述 `toLowerCase(Locale.ROOT)` 调用使用。

## 修改详情

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`（修改，169 行变动：+87 / -82）

**修改目的**：将所有硬编码的视图名和函数名替换为随机化名称，并修复 `toLowerCase` 的 Locale 问题。

**工作逻辑**：

该文件只修改了一个测试类，改动点可分为三类：

#### 1. 新增 `Locale` 导入（第 26 行）

```java
import java.util.Locale;
```

供 `showViews` 测试中的 `toLowerCase(Locale.ROOT)` 使用，避免 locale 敏感的字符转换问题。

#### 2. 视图名/函数名统一随机化（约 40 处替换）

将所有测试方法中的硬编码名称替换为 `viewName(baseName)` 调用。典型替换模式如下：

```diff
-    String viewName = "simpleView";
+    String viewName = viewName("simpleView");
```

```diff
-    String functionName = "test_avg";
+    String functionName = viewName("test_avg");
```

覆盖的测试方法包括但不限于：`readFromView`、`readFromTrinoView`、`readFromMultipleViews`、`readFromViewUsingNonExistingTable`、`readFromViewUsingInvalidSQL`、`readFromViewWithStaleSchema`、`readFromViewHiddenByTempView`、`readFromViewWithGlobalTempView`、`readFromViewReferencingAnotherView`、`readFromViewReferencingTempView`、`readFromViewReferencingAnotherViewHiddenByTempView`、`readFromViewReferencingGlobalTempView`、`readFromViewReferencingTempFunction`、`readFromViewWithCTE`、`rewriteFunctionIdentifier`、`builtinFunctionIdentifierNotRewritten`、`rewriteFunctionIdentifierWithNamespace`、`fullFunctionIdentifier`、`fullFunctionIdentifierNotRewrittenLoadFailure`、`dropView`、`dropViewIfExists`、`dropGlobalTempView`、`dropTempView`、`dropV1View`、`createViewIfNotExists`、`createViewReferencingTempView`、`createViewReferencingGlobalTempView`、`createViewReferencingTempFunction`、`createViewReferencingQualifiedTempFunction`、`createViewWithMismatchedColumnCounts`、`createViewWithColumnAliases`、`createViewWithDuplicateQueryColumnNames`、`createViewWithCTE`、`createViewWithConflictingNamesForCTEAndTempView`、`createViewWithCTEReferencingTempView`、`createViewWithCTEReferencingTempFunction`、`createViewWithSubqueryExpressionUsingTempView`、`createViewWithSubqueryExpressionUsingGlobalTempView`、`createViewWithSubqueryExpressionUsingTempFunction`、`showCreateSimpleView`、`showCreateComplexView`、`alterViewSetProperties`、`alterViewSetReservedProperties`、`alterViewUnsetProperties`、`alterViewUnsetUnknownProperty`、`alterViewUnsetReservedProperties`、`alterViewIsNotSupported` 等。

辅助方法 `viewName(String)` 本身未修改，保持原有实现：

```java
private String viewName(String viewName) {
  return viewName + new Random().nextInt(1000000);
}
```

#### 3. `showViews` 测试适配（第 1421-1466 行）

`showViews` 测试原先使用硬编码名称创建视图并在断言中写死对应字符串。修改后：

- 视图名改为变量：`String v1 = viewName("v1")`、`String prefixV2 = viewName("prefixV2")` 等。
- `CREATE VIEW` 语句改为 `sql("CREATE VIEW %s AS %s", v1, sql)` 形式。
- 断言中的硬编码字符串替换为变量引用，如 `row(NAMESPACE.toString(), prefixV2, false)`。
- 临时视图名的小写匹配改为 `tempViewForListing.toLowerCase(Locale.ROOT)` 和 `globalViewForListing.toLowerCase(Locale.ROOT)`，因为 Spark 以大小写不敏感方式存储临时视图名。

```diff
-    Object[] tempView = row("", "tempviewforlisting", true);
+    Object[] tempView = row("", tempViewForListing.toLowerCase(Locale.ROOT), true);
```

```diff
-            row("global_temp", "globalviewforlisting", true), tempView);
+            row("global_temp", globalViewForListing.toLowerCase(Locale.ROOT), true), tempView);
```

## 小结

- **成效**：通过将所有硬编码视图名/函数名替换为随机化名称，彻底消除了 `TestViews` 中因名称冲突导致的测试间污染问题；同时通过 `toLowerCase(Locale.ROOT)` 修复了 locale 敏感的小写转换问题。改动为纯测试代码，不影响生产行为。
- **影响范围**：仅修改 `spark/v3.5/spark-extensions` 测试模块的一个测试文件，无 API/行为变更。
- **回迁到 1.4.x 的注意事项**：可直接 cherry-pick。需确认 1.4.x 分支的 `spark/v3.5/spark-extensions` 模块下 `TestViews.java` 的方法列表与 main 分支一致；若 1.4.x 上已有部分测试使用了硬编码名称且方法签名有差异，需手工对齐。辅助方法 `viewName(String)` 使用 `new Random().nextInt(1000000)` 每次创建新 Random 实例，虽非最优（推荐复用单个 Random 实例），但功能正确，回迁时保持原样即可。
