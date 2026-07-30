# 提交 2274：Core: Fix API breakage introduced by #13191 (#13386)

## 提交信息

- **序号**：2274 / 4088
- **哈希**：4278e91ddaafe7f95e29383c6604a6bbabf4fd77
- **短哈希**：4278e91dd
- **日期**：2025-06-25 13:49:09 -0400
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Fix API breakage introduced by #13191 (#13386)
- **PR/Issue**：#13386（修复 #13191 引入的问题）

## 总体目的

本提交修复 PR #13191（"Add context aware parsing"，添加上下文感知解析）引入的 API 二进制兼容性破坏。#13191 在 `BaseHTTPClient` 中新增了一个带有 `ParserContext` 参数的抽象方法 `execute(...)`，这对外部实现 `BaseHTTPClient` 的子类构成破坏性变更——任何继承该抽象类的第三方代码必须实现这个新方法，否则无法编译。

为修复此问题，本提交将该抽象方法改为具体方法（提供默认实现），当传入非 null 的 `ParserContext` 时抛出 `UnsupportedOperationException`，否则委托到不带 `ParserContext` 的原有抽象方法。同时将 `ParserContext` 类从包级私有改为 `public`，并修复了其 `Builder` 内部类中初始化为不可变空 Map 导致无法累积数据的问题。

此外，从 `.palantir/revapi.yml` 中移除了之前为该破坏性变更添加的临时豁免条目，因为该变更不再是破坏性的。

## 如何达成设计目的

- 将 `BaseHTTPClient` 中新增的带 `ParserContext` 的 `execute` 方法从 `abstract` 改为具体方法（`protected`），提供默认实现：校验 parserContext 为 null 后委托到原抽象方法，否则抛出 `UnsupportedOperationException`。这样第三方子类无需被迫实现新方法即可保持兼容。
- 将 `ParserContext` 类可见性从包级私有（`class`）改为 `public`，使其可作为公开 API 的参数类型被外部引用。
- 修复 `ParserContext.Builder`：将 `data` 字段从延迟初始化的 `Collections.emptyMap()`（不可变）改为使用 `Maps.newHashMap()`（可变）的立即初始化，并标记为 `final`，修复了 add 操作因不可变 Map 而失败的问题。
- 从 revapi 配置中移除对应的 accepted breaks 条目，因为该方法不再是抽象的，不再构成"添加抽象方法"的破坏。

## 修改详情

### `.palantir/revapi.yml` (+0/-6 lines)

**修改目的**：移除之前为 #13191 引入的"添加抽象方法"破坏性变更的豁免条目。

**工作逻辑**：revapi 是 API 兼容性检查工具，`.palantir/revapi.yml` 中 `acceptedBreaks` 列表记录了被豁免的已知破坏性变更。原先为 `java.method.abstractMethodAdded`（BaseHTTPClient 新增抽象 execute 方法）添加了豁免，理由是"Add context aware parsing"。由于本提交将该抽象方法改为具体方法，不再构成破坏，因此移除该豁免条目，恢复严格的兼容性检查。

### `core/src/main/java/org/apache/iceberg/rest/BaseHTTPClient.java` (+8/-2 lines)

**修改目的**：将带 `ParserContext` 参数的 `execute` 方法从抽象方法改为带默认实现的具体方法，恢复二进制兼容性。

**工作逻辑**：原代码 `protected abstract <T> T execute(..., ParserContext parserContext);` 改为：

```java
protected <T> T execute(..., ParserContext parserContext) {
  if (null != parserContext) {
    throw new UnsupportedOperationException("Parser context is not supported");
  }
  return execute(request, responseType, errorHandler, responseHeaders);
}
```

默认实现中，若 `parserContext` 非 null 则抛出 `UnsupportedOperationException`（表示基类不支持上下文感知解析，需要子类按需覆盖），若为 null 则委托到不带 `ParserContext` 的原有抽象 `execute` 方法。这样既保留了上下文感知解析的扩展点（子类可覆盖此方法），又保证了不实现该方法的既有子类仍能正常工作。

### `core/src/main/java/org/apache/iceberg/rest/ParserContext.java` (+4/-5 lines)

**修改目的**：将 `ParserContext` 改为公开类，并修复 `Builder` 的可变性问题。

**工作逻辑**：
1. `class ParserContext` 改为 `public class ParserContext`，因为 `BaseHTTPClient` 的公开/protected 方法签名引用了该类型，需要其对外可见。
2. `Builder` 内部类中，原 `private Map<String, Object> data;` 在构造函数中初始化为 `Collections.emptyMap()`（不可变），调用 `add()` 时会抛出 `UnsupportedOperationException`。改为 `private final Map<String, Object> data = Maps.newHashMap();`，使用 Iceberg 重定位的 Guava `Maps.newHashMap()` 创建可变 Map 并立即初始化、标记为 final。这修复了 Builder 无法累积数据的 bug。

## 总结

本提交修复了上下文感知解析功能引入的 API 兼容性破坏，通过将抽象方法改为带默认实现的具体方法恢复了二进制兼容性，同时修复了 `ParserContext.Builder` 的可变 Map 初始化 bug。这体现了 Iceberg 项目对 API 兼容性的严格把控——使用 revapi 持续监控，并在引入破坏时及时修复而非依赖豁免。
