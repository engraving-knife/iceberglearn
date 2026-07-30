# 提交 2871：Core: Increase visibility of ParserContext (#14572)

## 提交信息

- **序号**：2871 / 4088
- **哈希**：53c046efda5d6c6ac67caf7de29849ab7ac6d406
- **短哈希**：53c046efd
- **日期**：2025-11-12 05:52:34 -0800
- **作者**：Prashant Singh
- **提交说明**：Core: Increase visibility of ParserContext (#14572)
- **PR/Issue**：#14572

## 总体目的

`ParserContext` 是 Iceberg REST 模块中用于 JSON 序列化/反序列化上下文的辅助类，它使用 Jackson 的 `InjectableValues` 机制在解析 REST 请求/响应时注入额外的上下文数据（如分区规范映射等）。此前，`ParserContext` 的 `builder()` 方法和 `Builder` 内部类的可见性为包级私有（package-private，即默认的 `static`），这意味着只有 `org.apache.iceberg.rest` 包内的类才能创建和使用 `ParserContext.Builder`。

这一可见性限制阻碍了其他模块（如 Flink、Spark 或外部集成）复用 `ParserContext` 来构建自定义的解析上下文。将可见性提升为 `public` 后，外部代码可以更方便地构建 REST 响应的解析上下文，增强了 API 的可扩展性和可复用性。

## 如何达成设计目的

修改非常直接，仅涉及两处可见性声明的变更：

1. 将 `ParserContext.builder()` 方法从 `static`（包级私有）改为 `public static`。
2. 将 `ParserContext.Builder` 内部类从 `static class`（包级私有）改为 `public static class`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/ParserContext.java` (+2/-2 lines)

**修改目的**：提升 `builder()` 工厂方法和 `Builder` 内部类的可见性为 public。

**工作逻辑**：
- `builder()` 方法：从 `static Builder builder()` 改为 `public static Builder builder()`，使包外代码可以获取 Builder 实例。
- `Builder` 类：从 `static class Builder` 改为 `public static class Builder`，使包外代码可以引用 Builder 类型。

Builder 内部的 `data` Map、私有构造器和 `build()` 方法本身已经是 public 的，所以无需额外修改。

## 总结

该提交是一个小型的 API 可见性改进，将 `ParserContext` 的 `builder()` 方法和 `Builder` 内部类从包级私有提升为 public，使外部模块能够复用 REST 解析上下文构建机制。这为后续其他模块集成 REST 解析功能提供了便利。
