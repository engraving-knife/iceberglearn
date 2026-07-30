# 提交 0610：API: Fix `TestStrictMetricsEvaluator` assertion message

## 提交信息

- **序号**：0610 / 4088
- **哈希**：f614a3f8b0379188efcc0c38caa486c94c6a52a3
- **短哈希**：f614a3f8b
- **日期**：2024-03-18 16:42:14 +0100
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：API: Fix `TestStrictMetricsEvaluator` assertion message (#9992)
- **PR/Issue**：#9992

## 总体目的

本提交修复 `TestStrictMetricsEvaluator` 中一处**误导性断言消息**（assertion description）。该消息与断言的实际期望值矛盾，会在测试失败时给出反向误导，增加排查成本。这是一次纯测试可读性/可维护性修复，不改变任何测试逻辑或产品代码行为。

具体问题：在 `testIntegerNotIn()` 测试方法中，对一个 `notIn` 谓词的严格求值结果做断言时，断言期望 `isTrue()`（文件应严格匹配、应读取），但消息文本却写成了 "Should not match: all values !=5 and !=6"——"Should not match" 与 `isTrue()` 语义相反。提交说明明确了正确语义："All values (`5` and `6`) are not between the upper and lower bound of `[30, 79]`."，即 5 和 6 都落在文件 `id` 列的取值区间 `[30, 79]` 之外，因此文件中所有行都满足 `id NOT IN (5, 6)`，严格求值器应返回 `true`（严格匹配）。

## 如何达成设计目的

修复方式是将断言描述从 "Should not match" 改为 "Should match"，使其与 `isTrue()` 期望及实际求值语义一致。改动极小（单行单词替换），不触及断言方法本身、不改变测试通过/失败状态（消息文本不影响断言布尔判定），仅修正人类阅读失败报告时的认知。

修正后，该断言与同方法内其他 `isTrue()` 断言的消息风格统一，例如：
- 第 727 行（当前 1.4.x）：`assertThat(shouldRead).as("Should match: no values == 80 or == 81").isTrue();`
- `testIntegerNotEq()` 第 602 行：`assertThat(shouldRead).as("Should match: no values == 5").isTrue();`

均采用 "Should match" + `isTrue()` 的正确搭配模式。

## 修改详情

### `api/src/test/java/org/apache/iceberg/expressions/TestStrictMetricsEvaluator.java`

**修改目的**：将 `testIntegerNotIn()` 中第一处 `notIn` 断言的描述文本从 "Should not match" 修正为 "Should match"，消除消息与 `isTrue()` 期望之间的语义矛盾。

**工作逻辑**：

被测代码上下文——`StrictMetricsEvaluator`（`api/src/main/java/org/apache/iceberg/expressions/StrictMetricsEvaluator.java`）是 Iceberg 表达式体系中的**文件级严格求值器**，与 `InclusiveMetricsEvaluator`（包容性求值器，判定文件"是否可能包含匹配行"用于数据跳过）语义相反。`StrictMetricsEvaluator` 判定文件中**所有行是否必然匹配**过滤条件：返回 `true`（`ROWS_MUST_MATCH`）表示文件所有行都满足谓词，返回 `false`（`ROWS_MIGHT_NOT_MATCH`）表示不能保证全部匹配。典型用途是"可安全删除文件"等需要保证不误删的场景。

其 `notIn(BoundReference, Set)` 方法（StrictMetricsEvaluator 第 565–619 行）的逻辑是：用文件列的下界过滤掉所有小于下界的 NOT-IN 值，再用上界过滤掉所有大于上界的 NOT-IN 值；若过滤后集合为空（即所有 NOT-IN 值都落在 `[lower, upper]` 之外），则所有行必然满足 NOT IN →返回 `ROWS_MUST_MATCH`（true）。

测试用例（`testIntegerNotIn()`，改动行位于该方法首段）：
- 测试常量：`INT_MIN_VALUE = 30`、`INT_MAX_VALUE = 79`，即被测 `FILE` 的 `id` 列取值区间为 `[30, 79]`。
- 表达式：`notIn("id", INT_MIN_VALUE - 25, INT_MIN_VALUE - 24)` 即 `notIn("id", 5, 6)`——"id 不在 {5, 6} 中"。
- 求值：5 和 6 都小于下界 30，经下界过滤后 NOT-IN 值集合为空 → 所有行必然满足 `id NOT IN (5, 6)` → 严格求值返回 `true`。
- 断言：`assertThat(shouldRead).isTrue()` 期望 `true`，符合上述求值结果。
- **消息缺陷**：原消息 "Should not match: all values !=5 and !=6" 中的 "Should not match" 与 `isTrue()` 矛盾；而 "all values !=5 and !=6" 恰恰是**应该匹配**的理由（所有值都不等于 5 和 6，故 NOT IN 成立）。

修改将 "Should not match" 改为 "Should match"（保留后半句 "all values !=5 and !=6"），使消息正确表达"因为所有值都不等于 5 和 6，所以文件严格匹配 NOT IN 谓词，应读取"。

注：该改动行在 main 分支提交时位于第 595 行，在当前 1.4.x 检出中因分支上有额外的 Javadoc 注释（中文测试场景说明）而位于第 707 行，但内容一致。

## 小结

本提交通过单行消息文本修正（"Should not match" → "Should match"），消除了 `TestStrictMetricsEvaluator.testIntegerNotIn()` 中断言描述与 `isTrue()` 期望之间的语义矛盾。`StrictMetricsEvaluator` 是 Iceberg 文件级严格裁剪的核心组件（判定文件所有行是否必然匹配谓词，用于安全删除等场景），该测试验证 `notIn` 谓词在 NOT-IN 值全部落在文件列上下界之外时返回严格匹配的正确性。修复不影响测试通过/失败结果（消息不影响布尔判定），仅提升失败报告的可读性与排查效率。回迁到 1.4.x 风险极低：纯测试消息文本改动，无逻辑变更，无依赖关系，可独立回迁。
