# 提交 0817：Spark 3.4, 3.5: Follow-up for #10442, Remove static test import (#10451)

## 提交信息

- **序号**：0817 / 4088
- **哈希**：afc30818b734879e53a24f90f034685cf8fc56bc
- **短哈希**：afc30818b
- **日期**：2024-06-05 18:32:45 -0700
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark 3.4, 3.5: Follow-up for #10442, Remove static test import (#10451)
- **PR/Issue**：#10451（follow-up of #10442）

## 总体目的

本提交是 PR #10442 的后续清理（follow-up）。在 Spark 3.4 和 3.5 两个版本的 `SmokeTest.java` 集成测试中，将 AssertJ 的静态导入（`import static org.assertj.core.api.Assertions.assertThat;`）替换为普通导入（`import org.assertj.core.api.Assertions;`），并将调用方式从 `assertThat(...)` 改为 `Assertions.assertThat(...)`。

PR #10442 引入了 `showView()` 测试方法，其中使用了 AssertJ 的 `assertThat`。由于该 SmokeTest 类中同时混合使用了 JUnit 的 `Assert`（`import org.junit.Assert;`）和 AssertJ 的 `assertThat`，静态导入 AssertJ 的 `assertThat` 可能造成代码风格不统一或潜在的命名冲突隐患。本提交通过移除静态导入、改用全限定调用方式来消除这个问题，保持测试代码的清晰性。

## 如何达成设计目的

修改方式非常简单：在 Spark 3.4 和 3.5 两个版本的 `SmokeTest.java` 中：

1. 移除静态导入行 `import static org.assertj.core.api.Assertions.assertThat;`
2. 新增普通导入 `import org.assertj.core.api.Assertions;`
3. 将 `showView()` 方法中的 `assertThat(sql("SHOW VIEWS"))` 改为 `Assertions.assertThat(sql("SHOW VIEWS"))`

这样做的目的：
- 避免静态导入 AssertJ 的 `assertThat` 与该文件中其他静态方法（如 JUnit 的 `Assert.assertXxx`）在阅读时产生混淆。
- 使 AssertJ 的使用更显式，便于读者一眼识别这是 AssertJ 断言而非 JUnit 断言。
- 符合代码审查（PR #10442）中提出的改进建议。

## 修改详情

### `spark/v3.4/spark-runtime/src/integration/java/org/apache/iceberg/spark/SmokeTest.java`

**修改目的**：移除 AssertJ `assertThat` 的静态导入，改用全限定调用。

**工作逻辑**：
- 移除 `import static org.assertj.core.api.Assertions.assertThat;`
- 新增 `import org.assertj.core.api.Assertions;`
- 在 `showView()` 方法中将 `assertThat(sql("SHOW VIEWS")).contains(row("default", "test", false));` 改为 `Assertions.assertThat(sql("SHOW VIEWS")).contains(row("default", "test", false));`

该文件仍保留 `import org.junit.Assert;`（JUnit 4 的断言），两种断言库共存但调用方式都改为带类名前缀的形式，风格更统一。

### `spark/v3.5/spark-runtime/src/integration/java/org/apache/iceberg/spark/SmokeTest.java`

**修改目的**：与 Spark 3.4 版本同步，移除 AssertJ `assertThat` 的静态导入，改用全限定调用。

**工作逻辑**：
- 移除 `import static org.assertj.core.api.Assertions.assertThat;`
- 新增 `import org.assertj.core.api.Assertions;`
- 在 `showView()` 方法中将 `assertThat(...)` 改为 `Assertions.assertThat(...)`

Spark 3.5 版本的 SmokeTest 与 3.4 版本结构类似，修改完全对称。

## 小结

- **成效**：消除了 SmokeTest 中 AssertJ 静态导入可能带来的代码风格不一致和命名混淆问题，使测试代码更清晰可读。两个 Spark 版本（3.4、3.5）的修改保持同步。
- **影响范围**：仅影响 Spark 3.4 和 3.5 的 `spark-runtime` 模块下的 `SmokeTest.java` 集成测试，不影响任何生产代码或功能行为。属于纯代码风格清理。
- **回迁注意事项**：
  1. 这是一个极低风险的代码风格清理，回迁非常简单。
  2. 需确认目标分支中 PR #10442（引入 `showView()` 测试的 PR）是否已回迁，否则本提交的修改将无意义（没有 `assertThat` 调用可改）。
  3. 两个 Spark 版本的修改需同步回迁。
  4. 不影响任何运行时行为，无需额外测试验证。
