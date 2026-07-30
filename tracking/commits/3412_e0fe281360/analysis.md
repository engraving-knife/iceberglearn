# 提交 3412：Core: fix propertiesWithPrefix to strip prefix literally, not as regex (#15558)

## 提交信息

- **序号**：3412 / 4088
- **哈希**：e0fe281360281c0a9247858ca57e8b1099807718
- **短哈希**：e0fe281360
- **日期**：2026-03-18 00:22:54 -0700
- **作者**：Noritaka Sekiyama
- **提交说明**：Core: fix propertiesWithPrefix to strip prefix literally, not as regex (#15558)
- **PR/Issue**：#15558

## 总体目的

修复 `PropertyUtil.propertiesWithPrefix` 方法在剥离属性名前缀时使用 `replaceFirst` 导致的正则表达式特殊字符问题。`replaceFirst` 将前缀作为正则表达式处理，当前缀包含正则特殊字符（如 `[`、`.`、`(` 等）时，会导致前缀剥离失败或抛出 `PatternSyntaxException`。例如前缀 `"prefix[0]."` 中，`[0]` 在正则中匹配字符 `0`，`.` 匹配任意字符，导致无法正确剥离。

## 如何达成设计目的

1. 将 `e.getKey().replaceFirst(prefix, "")` 改为 `e.getKey().substring(prefix.length())`
2. `substring` 是纯字符串操作，不涉及正则表达式，能正确处理所有字符
3. 新增两个测试验证正则特殊字符和未闭合字符类的处理

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/PropertyUtil.java` (+1/-1 lines)

**修改目的**：修复前缀剥离使用正则表达式的问题。

**工作逻辑**：
- 原有代码：`e.getKey().replaceFirst(prefix, "")` — 将 prefix 作为正则模式替换为空字符串
- 修复后：`e.getKey().substring(prefix.length())` — 使用字符串长度直接截取，纯字面量操作

### `core/src/test/java/org/apache/iceberg/util/TestPropertyUtil.java` (+24 lines)

**修改目的**：验证正则特殊字符的正确处理。

**工作逻辑**：

**propertiesWithPrefixHandlesRegexSpecialChars 测试**：
- 前缀 `"prefix[0]."` 包含正则特殊字符 `[`、`]`、`.`
- 在正则中 `[0]` 匹配字符 `0`，`.` 匹配任意字符，`replaceFirst` 会错误匹配
- 验证修复后结果为 `{"key": "value"}`

**propertiesWithPrefixHandlesUnclosedRegexChars 测试**：
- 前缀 `"prefix[0."` 包含未闭合的字符类 `[`
- `replaceFirst` 会抛出 `PatternSyntaxException`
- 验证 `substring` 方法能正确处理，结果为 `{"key": "value"}`

## 总结

本提交修复了 `PropertyUtil.propertiesWithPrefix` 方法的前缀剥离 bug，将 `replaceFirst`（正则替换）改为 `substring`（字面量截取），正确处理包含正则特殊字符的前缀。新增两个测试覆盖正则特殊字符和未闭合字符类的场景。
