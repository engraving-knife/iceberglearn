# 提交 1297：Flink: Fix disabling flaky range distribution bucketing tests (#11410)

## 提交信息

- **序号**：1297 / 4088
- **哈希**：86a8560315432d6c63af8f6bc1379618d7b432cf
- **短哈希**：86a856031
- **日期**：2024-10-29（Tue Oct 29 00:42:58 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Flink: Fix disabling flaky range distribution bucketing tests (#11410)
- **PR/Issue**：#11410

## 总体目的

Iceberg 的 Flink 集成模块中，`TestFlinkIcebergSinkRangeDistributionBucketing` 测试类用于验证 Flink Sink 在范围分布（range distribution）下进行分桶（bucketing）的行为。该测试存在不稳定（flaky）问题，此前通过在类上添加 `@Ignore` 注解来禁用整个测试类（参见 PR #11305 的评论链接）。

然而，`@Ignore` 是 JUnit 4（`org.junit.Ignore`）的注解，而该测试类使用的是 JUnit 5 / Jupiter（`org.junit.jupiter.api.Test` 等）。JUnit 5 不识别 JUnit 4 的 `@Ignore` 注解，因此这个禁用实际上是无效的——测试仍然会被执行，继续出现 flaky 失败。本提交将 `@Ignore` 替换为 JUnit 5 的等效注解 `@Disabled`，使禁用真正生效。

## 如何达成设计目的

修改 Flink v1.19 和 v1.20 两个版本目录下的同名测试类，将 JUnit 4 的 `@Ignore` 注解替换为 JUnit 5 的 `@Disabled` 注解，并同步更新对应的 import 语句。这是注解类型的纠正，不改变测试本身的逻辑，仅让"禁用"这个意图真正生效。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkRangeDistributionBucketing.java`

**修改目的**：将 `@Ignore` 替换为 `@Disabled`，使测试禁用在 JUnit 5 下生效。

**工作逻辑**：

1. 修改 import 语句：
   - 删除 `import org.junit.Ignore;`
   - 新增 `import org.junit.jupiter.api.Disabled;`（按字母序插入到 `BeforeEach` 和 `Test` 之间）

2. 修改类级注解：
   ```java
   // 修改前
   @Timeout(value = 30)
   @Ignore // https://github.com/apache/iceberg/pull/11305#issuecomment-2415207097
   public class TestFlinkIcebergSinkRangeDistributionBucketing {

   // 修改后
   @Timeout(value = 30)
   @Disabled // https://github.com/apache/iceberg/pull/11305#issuecomment-2415207097
   public class TestFlinkIcebergSinkRangeDistributionBucketing {
   ```

`@Disabled` 是 JUnit Jupiter 提供的注解，作用于类级别时会禁用该类中所有 `@Test` 方法。注释中的 GitHub 链接指向 PR #11305 的评论，记录了该测试 flaky 的背景。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkRangeDistributionBucketing.java`

**修改目的**：与 v1.19 完全相同的修改。

**工作逻辑**：v1.20 目录下的测试文件与 v1.19 内容一致，做相同的 import 和注解替换。两个文件改动行数相同（各 4 行：2 行 import 变更 + 2 行注解变更，实际 diff 为 2 增 2 删）。

## 小结

- **成效**：修正了 JUnit 4/5 注解混用问题，使 flaky 测试类被真正禁用，避免 CI 中出现间歇性失败。`@Disabled`（JUnit 5）正确生效，而原先的 `@Ignore`（JUnit 4）在 Jupiter 引擎下被忽略。
- **影响范围**：仅测试代码，涉及 `flink/v1.19` 和 `flink/v1.20` 两个模块下的同名测试类，各 4 行改动，无生产代码变更。
- **回迁到 1.4.x 的注意事项**：这是测试基础设施修复，不影响运行时产物。如果 1.4.x 分支的 Flink 模块中存在相同的 `@Ignore` 误用问题（且该分支也支持 Flink 1.19/1.20），建议回迁此修复以保证 CI 稳定。如果 1.4.x 不包含这两个 Flink 版本目录或测试已用其他方式处理，则无需回迁。该修复无任何功能风险。
