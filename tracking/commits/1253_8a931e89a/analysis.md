# 提交 1253：Flink: disable the flaky range distribution bucketing tests for now (#11347)

## 提交信息

- **序号**：1253 / 4088
- **哈希**：8a931e89a8dc0731aa627c04cb3676b50a6a0ad3
- **短哈希**：8a931e89a
- **日期**：2024-10-18（Fri Oct 18 09:36:11 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: disable the flaky range distribution bucketing tests for now (#11347)
- **PR/Issue**：#11347

## 总体目的

Iceberg 的 Flink 集成提供「range distribution bucketing」（范围分桶分布）功能，用于在写入分桶表时按数据分布对 Writer 进行均衡。对应的测试类 `TestFlinkIcebergSinkRangeDistributionBucketing` 在 CI 上表现出不稳定（flaky）：偶发失败但难以稳定复现。这类 flaky 测试会拖累主分支与 PR 的 CI 通过率，干扰维护者判断提交质量，也浪费排查时间。

在提交 #11305（引入 `IcebergSinkBuilder` 统一接口）合并后的评论中（见 PR 评论链接 https://github.com/apache/iceberg/pull/11305#issuecomment-2415207097），社区确认该测试存在不稳定性。本提交采取临时止血措施：在 Flink 1.19 与 1.20 两个版本的该测试类上添加 `@Ignore` 注解，暂时跳过整套测试，待后续定位根因后再恢复。

## 如何达成设计目的

直接在测试类级别添加 JUnit 的 `@Ignore` 注解（配带指向讨论来源的注释），使整套测试在该注解生效期间不会被 JUnit 执行。这是「先止损、后排查」的常规做法：用一行注解换取 CI 的稳定性，同时通过注释留下追踪线索（PR 评论链接）便于后续恢复时回溯上下文。改动只作用于测试代码，不触碰任何生产逻辑，因此不会影响发布产物或运行时行为。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkRangeDistributionBucketing.java`

**修改目的**：禁用 Flink 1.19 集成下不稳定的 range distribution bucketing 测试。

**工作逻辑**：在 import 区新增 `import org.junit.Ignore;`；在类级别已有的 `@Timeout(value = 30)` 注解下方追加：

```java
@Ignore // https://github.com/apache/iceberg/pull/11305#issuecomment-2415207097
public class TestFlinkIcebergSinkRangeDistributionBucketing {
```

`@Ignore` 是 JUnit 4 的注解（该测试类混用 JUnit 4/5 风格，类级注解由 JUnit 5 的 vintage 引擎兼容处理），作用于类时会让该类内所有 `@Test` 方法都被跳过，并在测试报告中标记为 skipped/ignored。注释中的链接指向触发此次禁用决策的 PR 评论，便于后续维护者理解来龙去脉。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkRangeDistributionBucketing.java`

**修改目的**：禁用 Flink 1.20 集成下同一套不稳定测试。

**工作逻辑**：与 1.19 版本完全一致的改动（import `@Ignore` + 类级注解 + 指向同一 PR 评论的注释）。1.20 与 1.19 的该测试文件内容相同，故同步施加 `@Ignore`。

## 小结

- **成效**：Flink 1.19/1.20 的 `TestFlinkIcebergSinkRangeDistributionBucketing` 测试类被整体跳过，消除 CI 上的 flaky 失败，恢复主分支与 PR 流水线的稳定性；同时通过注释保留了问题来源链接，便于后续排查与恢复。
- **影响范围**：仅 2 个 Flink 测试文件、共 4 行新增（每个文件 1 行 import + 1 行注解 + 注释），不涉及任何生产代码或构建配置。
- **回迁到 1.4.x 的注意事项**：这是针对 main 分支上 #11305 合并后暴露的测试不稳定性的临时处置。1.4.x 分支若不存在对应的 range distribution bucketing 测试或该测试未表现 flaky，则**无需回迁**；即便 1.4.x 存在该测试且偶发失败，也应先在 1.4.x 自身环境复现并确认根因后再决定是否禁用，盲目回迁 `@Ignore` 会掩盖 1.4.x 上可能存在的真实缺陷。本质上这是一项临时止损手段，后续应配合真正的 bug 修复一起评估。
