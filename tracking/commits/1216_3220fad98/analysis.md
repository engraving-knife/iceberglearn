# 提交 1216：Core: Fix UnicodeUtil#truncateStringMax returns malformed string. (#11161)

## 提交信息

- **序号**：1216 / 4088
- **哈希**：3220fad9826fa32fbb46e01158638c5fa0a0d5f2
- **短哈希**：3220fad98
- **日期**：2024-10-08（Tue Oct 8 00:05:59 2024 +0800）
- **作者**：Yujiang Zhong <42907416+zhongyujiang@users.noreply.github.com>
- **提交说明**：Core: Fix UnicodeUtil#truncateStringMax returns malformed string. (#11161)
- **PR/Issue**：#11161

## 总体目的

`UnicodeUtil.truncateStringMax` 用于计算字符串列的上界截断值（truncate upper bound），是 Iceberg 统计信息（column metrics）收集时的关键工具：给定一个字符串和目标长度 `length`，返回一个长度不超过 `length` 个 Unicode code point 的字符串，使其在字典序上大于等于原字符串，作为该列在该数据文件中的上界。这一上界用于查询时按范围过滤数据文件。

原实现存在两个 bug：

1. **溢出未处理**：当截断后最后一个 code point 已经是 `Character.MAX_CODE_POINT`（U+10FFFF，UTF-8 最大字符）时，原代码执行 `codePoint + 1` 会溢出为 `0`（int 回绕）或产生无效 code point，导致返回的字符串是 malformed（畸形）的，进而使上界比较错误。
2. **代理区 code point 未跳过**：Unicode 标准中，码点 U+D800..U+DFFF 是代理区（surrogate code points），用于 UTF-16 编码代理对，**不是合法的 Unicode scalar value**，任何映射到该区间的 UTF-8 字节序列都是非法的。原代码直接 `codePoint + 1` 不会跳过这个区间，可能产生非法 code point，使截断结果在 UTF-8 编码下是 malformed string。

本提交新增 `incrementCodePoint(int)` 私有方法，正确处理这两种边界情况：到达 `MAX_CODE_POINT` 时返回 `0` 表示溢出（外层逻辑据此放弃该长度尝试更短的）；遇到 `MIN_SURROGATE - 1` 时直接跳到 `MAX_SURROGATE + 1`，跳过整个代理区。

同时修正了 `Comparators` 中相关注释，把方法名 `isCharInUTF16HighSurrogateRange` 更正为 `isCharHighSurrogate`，使注释与实际方法名一致。

## 如何达成设计目的

1. **抽取增量方法**：把原 `truncateStringMax` 中内联的 `codePoint + 1` 替换为对新方法 `incrementCodePoint(codePoint)` 的调用，集中处理边界。
2. **`incrementCodePoint` 的三段逻辑**：
   - 先用 `Preconditions.checkArgument` 断言传入 code point 不在代理区（防御性，因为外层从合法字符串解析得到的 code point 不应落入代理区；若误入则抛出明确异常）。
   - 若 `codePoint == Character.MIN_SURROGATE - 1`（即 U+D7FF，代理区前最后一个合法 scalar value），返回 `Character.MAX_SURROGATE + 1`（即 U+E000，代理区后第一个合法 scalar value），跳过整个代理区。
   - 若 `codePoint == Character.MAX_CODE_POINT`（U+10FFFF），返回 `0` 表示溢出。
   - 否则返回 `codePoint + 1`。
3. **外层判断简化**：原 `if (nextCodePoint != 0 && Character.isValidCodePoint(nextCodePoint))` 中，`isValidCodePoint` 已不再需要（因为 `incrementCodePoint` 保证返回值要么是合法 scalar value，要么是 0），简化为 `if (nextCodePoint != 0)`。
4. **测试补充**：在 `TestMetricsTruncation` 中新增 `test8`（末尾是最大 UTF-8 字符，截断后应递增倒数第二个字符）与 `test9`（末尾是 `MIN_SURROGATE - 1`，截断后应跳到 `MAX_SURROGATE + 1`）两组用例；并修正 `test6` 的预期变量命名（`test6_2_expected` → `test6_1_expected`）与截断长度（2 → 1）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Comparators.java`

**修改目的**：修正注释中过时的方法名。

**工作逻辑**：将 `CharSequenceComparator` 类上方的注释从 "isCharInUTF16HighSurrogateRange method detects a 4-byte character" 改为 "isCharHighSurrogate method detects a high surrogate (4-byte character)"。纯注释修正，无代码逻辑变化，使注释与比较器实际调用的 `Character.isHighSurrogate` 方法语义一致。

### `api/src/main/java/org/apache/iceberg/util/UnicodeUtil.java`

**修改目的**：修复 `truncateStringMax` 在 code point 溢出与代理区场景下返回畸形字符串的 bug。

**工作逻辑**：

- 在 `truncateStringMax` 循环中，把 `int nextCodePoint = truncatedStringBuilder.codePointAt(offsetByCodePoint) + 1;` 改为 `int nextCodePoint = incrementCodePoint(truncatedStringBuilder.codePointAt(offsetByCodePoint));`。
- 把后续判断 `if (nextCodePoint != 0 && Character.isValidCodePoint(nextCodePoint))` 简化为 `if (nextCodePoint != 0)`（因为 `incrementCodePoint` 已保证非 0 返回值是合法 scalar value）。
- 新增私有静态方法 `incrementCodePoint(int codePoint)`：
  - 用 `Preconditions.checkArgument(codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE, "invalid code point: %s", codePoint)` 断言不在代理区。
  - 若 `codePoint == Character.MIN_SURROGATE - 1`，返回 `Character.MAX_SURROGATE + 1`（跳过代理区）。
  - 若 `codePoint == Character.MAX_CODE_POINT`，返回 `0`（溢出信号）。
  - 否则返回 `codePoint + 1`。
- 注释引用 Unicode 16.0.0 规范第 3 章，说明代理区 code point 不是 Unicode scalar value，UTF-8 中映射到该区间的字节序列是 ill-formed 的。

### `core/src/test/java/org/apache/iceberg/TestMetricsTruncation.java`

**修改目的**：补充溢出与代理区跳过场景的测试用例。

**工作逻辑**：

- 新增 `test8 = "a\uDBFF\uDFFFc"`（中间包含最大 4 字节 UTF-8 字符 U+10FFFF），截断到长度 2 时预期结果为 `"b"`（末尾 U+10FFFF 无法递增，故截断到长度 1 即 `"a"`，递增为 `"b"`，等价于长度 2 的截断结果）。新增两条断言验证截断值大于等于原字符串、且等于 `test8_2_expected = "b"`。
- 新增 `test9 = "a" + (char)(Character.MIN_SURROGATE - 1) + "b"`，即中间字符为 U+D7FF（代理区前最后一个合法 scalar value）。截断到长度 2 时，末尾字符 U+D7FF 递增应跳过代理区到 U+E000，预期 `test9_2_expected = "a" + (char)(Character.MAX_SURROGATE + 1)`。新增两条断言验证。
- 修正 `test6` 相关：原变量 `test6_2_expected` 重命名为 `test6_1_expected`（因实际用于长度 1 截断的断言），并把 `truncateStringMax(Literal.of(test6), 2)` 改为 `truncateStringMax(Literal.of(test6), 1)`（修正测试意图，验证长度 1 截断的结果）。

## 小结

- **成效**：修复了字符串上界截断在两种边界场景（最大 code point 溢出、代理区跳过）下返回畸形字符串的 bug，避免因此导致的列统计上界错误，进而避免查询时按范围过滤数据文件的误判（可能漏过本应过滤的文件，或误过滤本应保留的文件）。这是一个正确性 bug 修复。
- **影响范围**：`api` 模块 `UnicodeUtil` 与 `Comparators`（注释），`core` 模块测试。改动局限在字符串截断工具，无 API 签名变化。
- **回迁到 1.4.x 的注意事项**：这是一个影响数据正确性的 bug 修复，**强烈建议回迁到 1.4.x**。1.4.x 用户若表中含有包含最大 UTF-8 字符（U+10FFFF）或代理区边界字符（U+D7FF）的字符串列，且依赖列统计进行范围过滤，则可能因该 bug 得到错误的查询结果。回迁风险低：改动局部、有完整测试覆盖、无 API 兼容性问题。回迁时需同时带上 `UnicodeUtil` 与 `TestMetricsTruncation` 的改动，并确认 1.4.x 上 `Comparators` 注释修正不会与既有内容冲突。
