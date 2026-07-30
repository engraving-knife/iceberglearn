# 提交 0776：Core, Spark 3.4: Remove redundant output in tests (#10348)

## 提交信息

- **序号**：0776 / 4088
- **哈希**：2886ef4bf6cf575f9780a5bfd351a4f4d51cce4b
- **短哈希**：2886ef4bf
- **日期**：2024-05-17 10:20:57 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Core, Spark 3.4: Remove redundant output in tests (#10348)
- **PR/Issue**：#10348

## 总体目的

本提交清理了三处测试代码中遗留的 `System.out.println(...)` 调用。这些打印语句在调试阶段可能曾用于人工观察中间产物（如序列化后的 JSON、物理执行计划），但在测试稳定后已无实际作用，反而会污染测试输出日志、干扰 CI 上大量测试用例的可读性，并隐含"调试残留代码"的味道（code smell）。提交标题中的 "redundant output" 正是指这些冗余的标准输出。

具体涉及三类场景：

1. **Core 指标序列化测试**：`TestCommitMetricsResultParser` 与 `TestScanMetricsResultParser` 在断言 JSON 序列化结果前打印了实际生成的 JSON，本意应是排错时核对格式，但断言 `assertThat(json).isEqualTo(expectedJson)` 已能精确验证内容，打印纯属多余。
2. **Spark 3.4 行级操作下推测试**：`TestSystemFunctionPushDownInRowLevelOperations` 在收集物理计划中的函数调用前，打印了 `"!!! WRITE PLAN !!!"` 字样及整个写计划 `write.toString()`。这种带感叹号的临时打印明显是开发调试痕迹。

删除这些打印语句不会改变任何测试断言与行为，纯粹是测试卫生（test hygiene）层面的清理。

## 如何达成设计目的

整体设计思路是直接移除三处 `System.out.println(...)` 调用，不做任何逻辑改动。改动量极小（共删除 4 行），分布在 core 与 spark/v3.4 两个模块的三个测试文件中。由于这些打印语句均位于已有断言之前或之后、且不参与任何控制流，删除后测试语义完全保持不变，仅减少标准输出噪音。

## 修改详情

### `core/src/test/java/org/apache/iceberg/metrics/TestCommitMetricsResultParser.java`

**修改目的**：移除提交指标结果序列化测试中的冗余 JSON 打印。

**工作逻辑**：在测试空对象序列化为 `{ }` 的用例中，原代码先调用 `CommitMetricsResultParser.toJson(commitMetricsResult, true)` 得到 `json`，紧接一行 `System.out.println(json);`，随后才用 `assertThat(json).isEqualTo(expectedJson)` 断言。本提交删除该打印行，使测试仅保留"生成—断言—反序列化再断言"的标准流程。删除后，若断言失败，AssertJ 仍会在失败信息中展示实际与预期 JSON，调试信息并不丢失。

### `core/src/test/java/org/apache/iceberg/metrics/TestScanMetricsResultParser.java`

**修改目的**：移除扫描指标结果序列化测试中的冗余 JSON 打印。

**工作逻辑**：与上一文件完全对称。在空对象序列化为 `{ }` 的用例中，`ScanMetricsResultParser.toJson(scanMetricsResult, true)` 之后原本有一行 `System.out.println(json);`，本提交将其删除，仅保留 `Assertions.assertThat(json).isEqualTo(expectedJson)` 及随后的反序列化断言。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestSystemFunctionPushDownInRowLevelOperations.java`

**修改目的**：移除行级操作函数下推测试中对物理写计划的调试打印。

**工作逻辑**：在私有辅助方法 `executeAndCollectFunctionCalls(String query, Object... args)` 中，原代码在拿到 `V2TableWriteExec write` 之后打印了两行：`System.out.println("!!! WRITE PLAN !!!");` 与 `System.out.println(write.toString());`，然后才调用 `SparkPlanUtil.collectExprs(...)` 收集计划中的静态调用与函数应用表达式。本提交删除这两行打印，使该方法专注于"执行查询 → 取写计划 → 收集表达式"的职责，不再向标准输出泄露整个物理计划。带 `!!!` 的标记式打印是典型的临时调试残留，删除后测试逻辑不受影响。

## 小结

- **成效**：净化了三处测试用例的标准输出，避免在 CI 日志中产生与断言无关的噪音（JSON 串、物理计划树），提升大批量测试运行时的日志可读性；同时消除了"调试残留"这一代码味道。
- **影响范围**：仅触及测试代码，无生产代码改动，无 API/行为变更，无兼容性影响。
- **回迁注意事项**：此为纯测试清理，回迁到 1.4.x 分支无任何风险。若 1.4.x 分支上对应测试文件仍保留这些 `System.out.println`，直接套用即可；需注意三处文件路径与 1.4.x 分支结构是否一致（Spark 3.4 路径 `spark/v3.4/spark-extensions/...` 在 1.4.x 上同样存在，回迁应无障碍）。
