# 提交 2824：Core: Minor code improvements (#14489)

## 提交信息

- **序号**：2824 / 4088
- **哈希**：94d9287b7a3b7474aa0776c315ec000fc97de329
- **短哈希**：94d9287b7
- **日期**：2025-11-03 07:45:18 -0800
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Minor code improvements (#14489)
- **PR/Issue**：#14489

## 总体目的

本提交是一组小规模的代码质量改进，不涉及功能变更。主要解决两类代码异味（code smell）：

1. **已废弃的异常处理模式**：`PartitionSpec.escape()` 方法使用 `URLEncoder.encode(string, "UTF-8")`，该方法声明抛出 `UnsupportedEncodingException`（受检异常），需要 try-catch 包裹。而使用 `StandardCharsets.UTF_8`（Charset 常量）的重载版本不会抛出受检异常，代码更简洁且是推荐做法。

2. **冗余的 lambda 包装**：三个 REST 响应解析器中将简单的方法引用包装成多行 lambda（`node -> { return fromJson(node, ...); }`），可以直接简化为单行 lambda 表达式（`node -> fromJson(node, ...)`），减少不必要的样板代码。

这些改进提升了代码可读性和可维护性。

## 如何达成设计目的

针对每个改进点进行最小化修改：

1. 在 `PartitionSpec.escape()` 中用 `StandardCharsets.UTF_8` 替换字符串 "UTF-8"，移除 try-catch 块和 `UnsupportedEncodingException` 导入。
2. 在三个 REST 响应解析器中，将多行 lambda 简化为单行 lambda。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java` (+8/-6 lines, 实际净减)

**修改目的**：简化 URL 编码的异常处理。

**工作逻辑**：`escape()` 方法原为：
```java
try {
  return URLEncoder.encode(string, "UTF-8");
} catch (UnsupportedEncodingException e) {
  throw new RuntimeException(e);
}
```
改为：
```java
return URLEncoder.encode(string, StandardCharsets.UTF_8);
```
使用 `StandardCharsets.UTF_8`（`Charset` 类型）的 `URLEncoder.encode` 重载不抛出受检异常，因为 UTF-8 是 Java 平台保证支持的字符集，`UnsupportedEncodingException` 不可能发生。同时更新导入：移除 `java.io.UnsupportedEncodingException`，新增 `java.nio.charset.StandardCharsets`。

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchPlanningResultResponseParser.java` (+6/-5 lines, 实际净减)

**修改目的**：简化 lambda 表达式。

**工作逻辑**：将
```java
return JsonUtil.parse(json, node -> {
  return fromJson(node, specsById, caseSensitive);
});
```
简化为
```java
return JsonUtil.parse(json, node -> fromJson(node, specsById, caseSensitive));
```
移除冗余的 `return` 语句和大括号。

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchScanTasksResponseParser.java` (+6/-5 lines, 实际净减)

**修改目的**：同上，简化 lambda 表达式。

**工作逻辑**：同样将多行 lambda 简化为单行 lambda。

### `core/src/main/java/org/apache/iceberg/rest/responses/PlanTableScanResponseParser.java` (+5/-4 lines, 实际净减)

**修改目的**：同上，简化 lambda 表达式。

**工作逻辑**：将多行 lambda 简化为单行 lambda，并因简化后可减少缩进层级。

## 总结

本提交是一组低风险的代码质量改进：将 `PartitionSpec` 中已废弃的字符串编码异常处理替换为现代的 `StandardCharsets` 用法，并将三个 REST 响应解析器中冗余的多行 lambda 简化为单行。这些改进提升了代码简洁性和可维护性，不改变任何功能行为，净减少约 15 行代码。
