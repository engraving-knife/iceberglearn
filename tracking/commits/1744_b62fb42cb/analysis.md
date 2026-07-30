# 提交 1744：API: Fix TestInclusiveMetricsEvaluator notStartsWith tests. (#12303)

## 提交信息

- **序号**：1744 / 4088
- **哈希**：b62fb42cb5bd492feb798d34cbe75dc61d233454
- **短哈希**：b62fb42cb
- **日期**：2025-02-18 08:21:30 -0800
- **作者**：Ryan Blue
- **提交说明**：API: Fix TestInclusiveMetricsEvaluator notStartsWith tests. (#12303)
- **PR/Issue**：#12303

## 总体目的

`TestInclusiveMetricsEvaluator` 是 Iceberg API 模块中测试包容性指标评估器（InclusiveMetricsEvaluator）的测试类。该评估器根据数据文件的统计指标（如值计数、null 计数、上下界等）来判断一个谓词（如 `notStartsWith`）是否可以在文件级别被跳过。

在 `notStartsWith` 的测试中，测试数据文件 `FILE_4` 的值计数（value counts）配置不正确：总记录数为 50，但"any value counts"（包括 null 的值计数）被设为 20，"null value counts"被设为 2。这意味着有 20 条记录有值（包括 2 条 null），但这与总记录数 50 矛盾——如果只有 20 条记录有值且其中 2 条是 null，那剩余的 30 条记录是什么？

这种不一致的计数数据可能导致 `notStartsWith` 评估器的测试结果不准确——评估器可能会基于错误的 null 计数做出错误的文件跳过决策，使测试无法正确验证 `notStartsWith` 的实际行为。本提交的目标是修正这些不正确的计数数据，并补充新的测试用例以更全面地覆盖 `notStartsWith` 的评估逻辑。

## 如何达成设计目的

提交通过以下修改达成目标：

1. **修正计数数据**：将 `FILE_4` 的"any value counts"从 20 修正为 50（与总记录数一致），"null value counts"从 2 修正为 0（表示没有 null 值，所有记录都有值）。这影响了 `FILE_4` 的三个实例（分别用于不同的测试场景）。

2. **新增测试文件**：新增 `FILE_5` 数据文件，其字符串字段的上下界为 `"abc"` 到 `"abcdefghi"`，值计数为 50（无 null），用于专门测试 `notStartsWith` 在特定前缀场景下的行为。

3. **新增测试断言**：在 `testNotStartsWith` 方法中新增两个断言：
   - `notStartsWith("required", "abc")` 对 `FILE_5`：由于文件中所有字符串都以 "abc" 开头（下界为 "abc"），应该可以跳过该文件（返回 false）。
   - `notStartsWith("required", "abcd")` 对 `FILE_5`：由于下界 "abc" 比前缀 "abcd" 短，无法确定是否所有值都以 "abcd" 开头，因此不能跳过（返回 true）。

## 修改详情

### `api/src/test/java/org/apache/iceberg/expressions/TestInclusiveMetricsEvaluator.java`（修改, +20/-6 lines）

**修改目的**：修正 `FILE_4` 的值计数数据并新增 `FILE_5` 及对应测试。

**工作逻辑**：
1. **FILE_4 修正**（3 处）：将三处 `FILE_4` 实例中的"any value counts"从 `ImmutableMap.of(3, 20L)` 改为 `ImmutableMap.of(3, 50L)`，"null value counts"从 `ImmutableMap.of(3, 2L)` 改为 `ImmutableMap.of(3, 0L)`。字段 3 是 `required` 字符串字段，修正后计数与总记录数 50 一致，且没有 null 值。

2. **FILE_5 新增**：新增一个 `TestDataFile` 实例，文件名为 `"file_4.avro"`，总记录数 50，值计数 50（无 null），字符串字段（field 3）的下界为 `"abc"`、上界为 `"abcdefghi"`。

3. **测试断言新增**：在 `testNotStartsWith` 方法末尾新增两个断言：
   - `notStartsWith("required", "abc")` 评估 `FILE_5`：断言返回 `false`（应跳过），因为文件下界 "abc" 等于前缀 "abc"，意味着所有值都以 "abc" 开头，`notStartsWith("abc")` 不可能匹配任何值。
   - `notStartsWith("required", "abcd")` 评估 `FILE_5`：断言返回 `true`（应读取），因为文件下界 "abc" 比前缀 "abcd" 短，可能存在以 "abc" 但不以 "abcd" 开头的值，不能跳过。

## 小结

- **成效**：修正了 `TestInclusiveMetricsEvaluator` 中 `FILE_4` 数据文件不正确的值计数（20→50）和 null 计数（2→0），使测试数据与总记录数一致。新增了 `FILE_5` 和两个 `notStartsWith` 测试用例，覆盖了"前缀等于下界"和"前缀长于下界"两个关键边界场景。
- **影响范围**：仅影响 api 模块的测试文件，不修改任何生产代码。修正了测试数据的正确性，确保 `notStartsWith` 评估器的测试覆盖更准确和全面。
- **回迁到 1.4.x 的注意事项**：此提交为测试修复，无功能影响，回迁风险低。需确认 1.4.x 分支中该测试文件存在且 `FILE_4` 的计数数据一致。如果 1.4.x 的 `InclusiveMetricsEvaluator` 实现不同，需确认测试断言的预期结果仍然正确。建议回迁。
