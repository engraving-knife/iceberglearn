# 提交 2990：API: Reduce 'Scanning table' log verbosity for long list of strings (#14757)

## 提交信息

- **序号**：2990 / 4088
- **哈希**：122c4408b101bcf846440348040caef016e16626
- **短哈希**：122c4408b
- **日期**：2025-12-10
- **作者**：Raunaq Morarka
- **提交说明**：API: Reduce 'Scanning table' log verbosity for long list of strings (#14757)
- **PR/Issue**：#14757

## 总体目的

Iceberg 在扫描表时会将过滤表达式（如 `IN` 谓词）通过 `ExpressionUtil.toSanitizedString` 转成可读字符串用于日志（典型如 "Scanning table ... filter = ..."）。当 `IN` 谓词包含大量字符串值时，原有缩略逻辑 `abbreviateValues` 存在缺陷：它要求"去重后的不同值数量"与"原列表大小"之差至少达到 `LONG_IN_PREDICATE_ABBREVIATION_MIN_GAIN`（5）才会缩略，否则原样输出全部值。

这意味着对于"大量重复值"的场景缩略生效，但对于"大量互不相同的字符串值"（例如 15 个各不相同的字符串），由于去重后没有显著减少，差值小于 5，缩略不会触发，导致日志中打印出全部 15 个值（甚至上百个），污染日志、增加噪声，也不利于脱敏。

本提交修改缩略策略：当值数量达到阈值（10）时，无论去重增益如何，都最多只展示阈值数量（10）个值，超出部分以 "... (N values hidden, M in total)" 汇总，从而对长列表一律生效，显著降低日志噪声。

## 如何达成设计目的

思路是简化 `abbreviateValues`：移除"最小增益"判断，改为"截断到阈值数量"。对去重后的列表最多保留前 10 个值，若去重后数量仍小于原列表大小则追加隐藏提示。同时调整测试用例覆盖"大量不同字符串"这一原先会漏过的场景。

## 修改详情

### `api/src/main/java/org/apache/iceberg/expressions/ExpressionUtil.java` (+20/-13 lines)

**修改目的**：简化长 `IN` 谓词值的缩略逻辑，对超阈值列表强制截断。

**工作逻辑**：

原逻辑：当 `sanitizedValues.size() >= 10` 时，先去重得到 `distinctValues`，仅当 `distinctValues.size() <= size - 5`（即去重至少减少 5 个）才输出去重值并附 "... (size - distinct size values hidden, size in total)"；否则不缩略。

新逻辑：去重后取 `abbreviatedSize = Math.min(distinctValues.size(), 10)`，截取去重列表前 `abbreviatedSize` 个加入结果；若 `abbreviatedSize < sanitizedValues.size()`（仍有未展示的值），追加 "... (size - abbreviatedSize values hidden, size in total)"。这样无论去重增益多大，最终展示的值都不会超过 10 个，对长列表一律生效。同时移除了不再使用的 `LONG_IN_PREDICATE_ABBREVIATION_MIN_GAIN` 常量与 `Set` 导入。

### `api/src/test/java/org/apache/iceberg/expressions/TestExpressionUtil.java` (+11/-0 lines)

**修改目的**：补充"大量不同字符串"场景的缩略验证。

**工作逻辑**：新增测试构造一个包含 `LONG_IN_PREDICATE_ABBREVIATION_THRESHOLD + 5`（即 15）个不同字符串（`string_0`..`string_14`）的 `IN` 表达式，断言其 sanitized 字符串恰好展示前 10 个值的 hash 形式，并以 "... (5 values hidden, 15 in total)" 结尾。该场景正是原先因去重无增益而不会被缩略的情况，现在被新逻辑正确截断。

## 总结

本提交修复了 `ExpressionUtil` 中长 `IN` 谓词日志缩略的盲区，使"大量互不相同的字符串值"场景也能被截断到 10 个并汇总隐藏数量，有效降低了扫描日志的噪声与潜在信息泄露风险。改动小而精准，并补齐了针对性的回归测试。
