# 提交 1269：Spark 3.4: Randomize view/function names in testing (#11382)

## 提交信息

- **序号**：1269 / 4088
- **哈希**：60181f9a763fb3912f071ae0a3dcdaeff6ddb477
- **短哈希**：60181f9a7
- **日期**：2024-10-23（Wed Oct 23 19:16:39 2024 +0200）
- **作者**：Eduard Tudenhoefner <etudenhoefner@gmail.com>
- **提交说明**：Spark 3.4: Randomize view/function names in testing (#11382)
- **PR/Issue**：#11382

## 总体目的

本提交是 PR #11381（提交 1268）向 Spark 3.4 模块的同步回移植。`TestViews` 在 Spark 3.4 扩展模块中同样存在，且面临完全相同的问题：

1. **测试间名称污染**：绝大多数测试用例使用硬编码的字符串字面量（如 `"simpleView"`、`"trinoView"`、`"test_avg"` 等）作为视图名和临时函数名。在共享 SparkSession 下，前一个测试残留的视图/函数会影响后续测试，导致间歇性失败。
2. **`toLowerCase` 缺少 `Locale` 参数**：`showViews` 测试中以硬编码小写字符串匹配临时视图名，未使用 `Locale.ROOT`。

本提交的目标与 1268 完全一致：消除 `TestViews`（Spark 3.4 版本）中的名称冲突和 locale 敏感问题。

## 如何达成设计目的

与提交 1268 完全相同的设计思路，区别仅在于目标模块为 `spark/v3.4` 而非 `spark/v3.5`：

1. **统一使用 `viewName()` 辅助方法**：将所有硬编码视图名/函数名替换为 `viewName(baseName)` 调用，辅助方法通过 `viewName + new Random().nextInt(1000000)` 拼接随机后缀。
2. **`showViews` 测试适配**：断言中的硬编码字符串替换为变量引用，临时视图名使用 `toLowerCase(Locale.ROOT)` 匹配。
3. **引入 `Locale` 导入**。

## 修改详情

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestViews.java`（修改，169 行变动：+87 / -82）

**修改目的**：将所有硬编码的视图名和函数名替换为随机化名称，并修复 `toLowerCase` 的 Locale 问题。

**工作逻辑**：

改动内容与提交 1268 中的 `spark/v3.5` 版本完全镜像，包括：

1. **新增 `Locale` 导入**：`import java.util.Locale`。

2. **视图名/函数名统一随机化**（约 40 处替换）：将所有测试方法中的硬编码名称替换为 `viewName(baseName)` 调用。典型替换：

   ```diff
   -    String viewName = "simpleView";
   +    String viewName = viewName("simpleView");
   ```

   ```diff
   -    String functionName = "test_avg";
   +    String functionName = viewName("test_avg");
   ```

   覆盖的测试方法列表与 1268 一致（`readFromView`、`readFromTrinoView`、`readFromMultipleViews`、`dropView`、`createViewIfNotExists`、`showCreateSimpleView`、`alterViewSetProperties` 等全部测试方法）。

   辅助方法 `viewName(String)` 保持不变：

   ```java
   private String viewName(String viewName) {
     return viewName + new Random().nextInt(1000000);
   }
   ```

3. **`showViews` 测试适配**：视图名改为变量（`String v1 = viewName("v1")` 等），`CREATE VIEW` 使用 `%s` 占位符，断言引用变量，临时视图名用 `toLowerCase(Locale.ROOT)` 匹配。

   ```diff
   -    Object[] tempView = row("", "tempviewforlisting", true);
   +    Object[] tempView = row("", tempViewForListing.toLowerCase(Locale.ROOT), true);
   ```

**与 1268 的差异**：仅在于文件路径（`spark/v3.4/` vs `spark/v3.5/`）和基类（`SparkExtensionsTestBase` vs `ExtensionsTestBase`，这是因为 Spark 3.4 与 3.5 的测试基类命名不同），改动逻辑完全相同。

## 小结

- **成效**：与提交 1268 相同，消除了 Spark 3.4 模块 `TestViews` 中因名称冲突和 locale 问题导致的测试不稳定性。改动为纯测试代码，不影响生产行为。
- **影响范围**：仅修改 `spark/v3.4/spark-extensions` 测试模块的一个测试文件，无 API/行为变更。
- **回迁到 1.4.x 的注意事项**：可直接 cherry-pick。需确认 1.4.x 分支的 `spark/v3.4/spark-extensions` 模块下 `TestViews.java` 的方法列表与 main 分支一致。本提交与 1268 是同一改动的两个 Spark 版本副本，回迁时应同时回迁两者以保持一致性。
