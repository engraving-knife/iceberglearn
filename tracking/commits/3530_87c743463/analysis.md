# 提交 3530：Build: Ban toLowerCase/toUpperCase without locale (#15960)

## 提交信息

- **序号**：3530 / 4088
- **哈希**：87c743463b6311f2412e1addf19cf204c1b79e3d
- **短哈希**：87c743463
- **日期**：2026-04-14 09:18:09 +0200
- **作者**：Yuya Ebihara
- **提交说明**：Build: Ban toLowerCase/toUpperCase without locale (#15960)
- **PR/Issue**：#15960

## 总体目的

此前在 #15956/#15958（提交 3523）中已经发现并修复了 `TableIdentifier.toLowerCase()` 因使用无参 `String::toLowerCase` 而在土耳其语等 Locale 下产生错误结果的 bug。这类 bug 的根源是 Java 无参的 `toLowerCase()`/`toUpperCase()` 会使用 JVM 默认 Locale，导致大小写转换行为不确定。

为了防止类似 bug 再次引入，本提交通过静态检查工具在构建阶段禁止使用无参的 `toLowerCase`/`toUpperCase`（包括方法引用形式 `String::toLowerCase`/`String::toUpperCase`），强制开发者显式传入 Locale（推荐 `Locale.ROOT` 用于与 Locale 无关的操作）。

这是「治本」措施：既有 bug 修复 + 静态规则防止回归。

## 如何达成设计目的

启用两条规则：
1. **Checkstyle 的 `RegexpSinglelineJava`**：通过正则 `String::to(Lower|Upper)Case` 检测方法引用形式的无参调用，并给出提示消息。设置 `ignoreComments=true` 避免误报注释中的内容。
2. **Error Prone 的 `StringCaseLocaleUsage`**：将规则级别设为 `ERROR`。该规则能检测更完整的无参 `toLowerCase()`/`toUpperCase()` 调用形式（包括直接调用 `str.toLowerCase()`）。注释说明方法引用形式的检测由 checkstyle 负责。

两者互补：checkstyle 负责方法引用形式（Error Prone 的规则不覆盖方法引用），Error Prone 负责普通调用形式。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml` (+5/-0 lines)

**修改目的**：新增 checkstyle 规则禁止 `String::toLowerCase`/`String::toUpperCase` 方法引用。

**工作逻辑**：
```xml
<module name="RegexpSinglelineJava">
    <property name="ignoreComments" value="true"/>
    <property name="format" value="String::to(Lower|Upper)Case"/>
    <property name="message" value="Use toLowerCase(Locale)/toUpperCase(Locale) instead of no-arg versions or method references. Prefer Locale.ROOT for locale-insensitive operations."/>
</module>
```
正则匹配 `String::toLowerCase` 和 `String::toUpperCase` 两种方法引用形式，命中时输出引导消息。

### `baseline.gradle` (+2/-0 lines)

**修改目的**：启用 Error Prone 的 `StringCaseLocaleUsage` 规则为 ERROR 级别。

**工作逻辑**：
```groovy
// This rule doesn't enforce the use of method references. That's handled by checkstyle.
'-Xep:StringCaseLocaleUsage:ERROR',
```
注释明确说明该规则不覆盖方法引用形式（由 checkstyle 负责），两者分工互补。

## 总结

本提交通过同时启用 checkstyle 正则规则和 Error Prone 的 `StringCaseLocaleUsage` 规则，从构建层面禁止使用无参的 `toLowerCase()`/`toUpperCase()`（包括方法引用形式），强制开发者显式传入 Locale。这是对 #15956 中发现的国际化 bug 的「治本」措施，防止类似 Locale 相关的回归 bug 再次引入。
