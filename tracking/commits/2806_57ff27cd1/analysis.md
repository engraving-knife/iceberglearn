# 提交 2806：API: Add exception definitions for scan planning (#14442)

## 提交信息

- **序号**：2806 / 4088
- **哈希**：57ff27cd1037d52982428f7efdb5143550bfae56
- **短哈希**：57ff27cd1
- **日期**：2025-10-30 09:25:26 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：API: Add exception definitions for scan planning (#14442)
- **PR/Issue**：#14442

## 总体目的

本提交为 Iceberg API 添加扫描计划（scan planning）相关的异常定义，为后续的分布式扫描计划功能奠定基础。

随着 Iceberg 的扫描计划功能演进，特别是 REST Catalog 和分布式扫描计划（distributed scan planning）的发展，需要更细粒度的异常类型来区分不同的错误场景。之前扫描计划相关的错误可能使用通用的异常类型，无法准确表达特定的错误原因。

本提交新增两个异常类：
1. `NoSuchPlanIdException`：当尝试获取不存在的扫描计划 ID 的结果时抛出。这对应于异步扫描计划场景：客户端提交扫描计划请求获得 plan ID，后续用 plan ID 获取结果时，如果该 ID 不存在（可能已过期或从未创建），应抛出此异常。
2. `NoSuchPlanTaskException`：当尝试获取不存在的计划任务的结果时抛出。这对应于扫描计划被拆分为多个任务（plan task）的场景，如果某个任务 ID 不存在，应抛出此异常。

这两个异常都继承自 `RESTException`，表明它们主要用于 REST Catalog 交互场景。

## 如何达成设计目的

在 `api/src/main/java/org/apache/iceberg/exceptions/` 包下新增两个异常类，均继承自 `RESTException`，提供格式化消息的构造方法。

## 修改详情

### `api/src/main/java/org/apache/iceberg/exceptions/NoSuchPlanIdException.java` (+38/-0 lines, new file)

**修改目的**：定义扫描计划 ID 不存在的异常。

**工作逻辑**：继承自 `RESTException`，使用 `@FormatMethod` 注解标记支持格式化消息。提供两个构造方法：一个接受消息和参数，另一个接受原因异常（cause）、消息和参数。Javadoc 说明：当尝试获取不存在的 plan ID 的扫描计划结果时抛出。

### `api/src/main/java/org/apache/iceberg/exceptions/NoSuchPlanTaskException.java` (+34/-0 lines, new file)

**修改目的**：定义计划任务不存在的异常。

**工作逻辑**：同样继承自 `RESTException`，使用 `@FormatMethod` 注解。提供两个构造方法（消息/参数，cause/消息/参数）。Javadoc 说明：当尝试获取不存在的 plan task 的结果任务时抛出。

## 总结

本提交为 Iceberg API 新增了两个扫描计划相关的异常定义：`NoSuchPlanIdException`（计划 ID 不存在）和 `NoSuchPlanTaskException`（计划任务不存在）。两者均继承自 `RESTException`，为后续的分布式扫描计划和 REST Catalog 异步扫描计划功能提供异常基础设施。本提交仅添加异常定义，不包含使用这些异常的业务逻辑代码。
