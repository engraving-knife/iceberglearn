# 提交 1910：Build: Enforce error message check on Exception assertions (#12624)

## 提交信息

- **序号**：1910 / 4088
- **哈希**：cbf34a6ab980ab9b7bb01e45c9e2bcfad8e2986b
- **短哈希**：cbf34a6ab
- **日期**：2025-03-24 11:09:28 +0100
- **作者**：Leon Lin
- **提交说明**：Build: Enforce error message check on Exception assertions (#12624)
- **PR/Issue**：#12624

## 总体目的

Iceberg 测试代码大量使用 AssertJ 的 `assertThatThrownBy(...)` 与 `assertThatExceptionOfType(...)` 来断言被测代码抛出的异常。但许多断言只校验了异常类型（`.isInstanceOf(...)` / `.isThrownBy(...)`），没有校验异常消息（`.hasMessage(...)` / `.withMessage(...)`）。仅断言类型存在两个问题：

1. 断言强度弱：只要抛出同一类型的异常就通过，哪怕消息与预期完全不符，无法防止回归把异常原因改变。
2. 异常消息往往包含关键的失败原因，断言它能避免别人误改抛错路径。

本提交通过在 checkstyle 配置中加入两条正则规则，强制要求：凡是 `assertThatThrownBy(...)` 链中使用了 `isInstanceOf(...)` 的，必须同时出现 `.hasMessage*(`；凡是 `assertThatExceptionOfType(...)` 链中使用了 `isThrownBy(...)` 的，必须同时出现 `.withMessage*(`。随后修改约 62 个测试文件，为既有不满足规则的断言补上消息检查（或在 JDK 不保证消息的场景用 `@SuppressWarnings` 显式豁免）。

## 如何达成设计目的

1. 在 `.baseline/checkstyle/checkstyle.xml` 中新增两个 `RegexpMultiline` 模块：
   - `AssertThatThrownByWithMessageCheck`：匹配 `assertThatThrownBy(... isInstanceOf(... ;` 但中间没出现 `.hasMessage*(` 的写法，命中即报错。
   - `AssertThatExceptionOfTypeWithMessageCheck`：匹配 `assertThatExceptionOfType(... isThrownBy(... ;` 但中间没出现 `.withMessage*(` 的写法，命中即报错。
   两条规则都用负向预查 `(?!\.hasMessage\w*\()` 跨行匹配，确保整条语句结束前没有消息检查时才报警。
2. 批量修改测试文件：为每个原本只有 `isInstanceOf` 的断言追加 `.hasMessage(...)` / `.hasMessageContaining(...)` / `.hasMessageMatching(...)` / `.hasMessage(null)` 等。
3. 对个别无法保证消息内容的场景（如 JDK 自带的 `NullPointerException` 在不同 JDK 版本消息可能为空），用 `@SuppressWarnings("checkstyle:AssertThatThrownByWithMessageCheck")` 显式豁免，并加注释说明。

## 修改详情

### `.baseline/checkstyle/checkstyle.xml` (修改, +14 lines)

**修改目的**：引入两条强制消息检查的 checkstyle 规则。

**工作逻辑**：

```xml
<module name="RegexpMultiline">
  <property name="id" value="AssertThatThrownByWithMessageCheck"/>
  <property name="fileExtensions" value="java"/>
  <property name="matchAcrossLines" value="true"/>
  <property name="format" value="assertThatThrownBy\((?:(?!\.hasMessage\w*\().)*?isInstanceOf\((?:(?!\.hasMessage\w*\().)*?;"/>
  <property name="message" value="assertThatThrownBy must include a message check like .hasMessage(...)"/>
</module>
```

正则核心：从 `assertThatThrownBy(` 开始，到 `isInstanceOf(`，再到 `;` 结束，整段中若没有 `.hasMessage*(`（用负向预查 `(?!\.hasMessage\w*\()` 逐字符推进保证），则匹配命中并报错。第二条 `AssertThatExceptionOfTypeWithMessageCheck` 同理，针对 `assertThatExceptionOfType(...).isThrownBy(...)` 链要求 `.withMessage*(`。

### 约 62 个测试文件 (修改, +218/-57 lines 跨文件合计)

**修改目的**：为既有异常断言补齐消息检查以满足新规则。

**工作逻辑**（典型模式）：

- `api/.../TestPathParsing.java`、`TestExpressionBinding.java`：把 `.isInstanceOf(IllegalArgumentException.class)` 后追加 `.hasMessageMatching("(Unsupported|Invalid) path.*")`。
- `core/.../TestPartitionMap.java`：对 JDK 内置集合抛出的 `UnsupportedOperationException` 追加 `.hasMessage(null)` 或 `.hasMessage("Cannot set value")`；对 `NullPointerException` 这种不同 JDK 消息不一致的，加 `@SuppressWarnings("checkstyle:AssertThatThrownByWithMessageCheck")` 并注释 "no check on the underlying error msg as it might be missing based on the JDK version"。
- `core/.../TestClientPoolImpl.java`、`TestManifestReaderStats.java`、`TestRESTCatalog.java` 等：追加 `.hasMessage(...)` / `.hasMessageContaining(...)`。
- 涉及模块：api、core、aws、aliyun、dell、flink（3.3/3.4/3.5）、gcp、hive、kafka-connect、nessie、orc、spark（3.3/3.4/3.5）的扩展与 source 测试。

例如 `TestPartitionMap` 中：

```java
assertThatThrownBy(() -> map.entrySet().iterator().next().setValue("other"))
    .isInstanceOf(UnsupportedOperationException.class)
    .hasMessage("Cannot set value");
```

而对于 JDK 行为相关：

```java
@SuppressWarnings("checkstyle:AssertThatThrownByWithMessageCheck")
public void testNullKey() {
  // no check on the underlying error msg as it might be missing based on the JDK version
  ...
  assertThatThrownBy(() -> map.put(null, "value")).isInstanceOf(NullPointerException.class);
}
```

## 总结

本提交通过 checkstyle 规则强制测试中的异常断言必须校验异常消息，提升断言强度，避免只校验异常类型导致回归被漏检；同时批量修订约 62 个测试文件补齐 `.hasMessage(...)` / `.withMessage(...)`，并对 JDK 版本相关的不确定消息场景提供 `@SuppressWarnings` 豁免机制。
