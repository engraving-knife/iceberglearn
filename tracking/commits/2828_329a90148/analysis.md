# 提交 2828：Core: Restrict visibility of spec-by-id API for scan planning responses (#14485)

## 提交信息

- **序号**：2828 / 4088
- **哈希**：329a9014875f1c4ee9fc5071639cf12f281f20de
- **短哈希**：329a90148
- **日期**：2025-11-04 08:33:32 +0100
- **作者**：Prashant Singh
- **提交说明**：Core: Restrict visibility of spec-by-id API for scan planning responses (#14485)
- **PR/Issue**：#14485

## 总体目的

本提交为 REST 扫描计划（scan planning）响应中的 `specsById`（分区规范按 ID 映射）API 标记为 `@Deprecated`，计划在 1.12.0 版本降低其可见性。这是 API 演进管理的一部分：该 API 目前是 `public` 的，但设计上不应作为公共 API 暴露给外部使用者，未来将收紧可见性范围。

同时，本提交修复了三个响应解析器中遗漏设置 `specsById` 的问题。此前 `FetchPlanningResultResponseParser`、`FetchScanTasksResponseParser`、`PlanTableScanResponseParser` 在构建响应对象时没有调用 `withSpecsById(specsById)`，导致这些响应中的分区规范映射可能为空，影响下游使用 `specsById` 的逻辑。本提交补全了这一设置。

这两项修改看似矛盾（既标记废弃又在补全功能），但实际上是合理的：先补全功能确保当前版本正确工作，同时标记废弃为未来版本收紧 API 可见性做铺垫。

## 如何达成设计目的

1. **标记废弃**：在 `BaseScanTaskResponse` 类及其内部 Builder 类中，为 `specsById()` 和 `withSpecsById()` 方法添加 `@Deprecated` 注解和 Javadoc 说明，标注自 1.11.0 起废弃，将在 1.12.0 降低可见性。这给使用者提供了迁移窗口。

2. **补全 specsById 设置**：在三个响应解析器的 `fromJson` 方法中，Builder 构建响应时补充 `.withSpecsById(specsById)` 调用，确保分区规范映射被正确传入响应对象。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/responses/BaseScanTaskResponse.java` (+12/-0 lines)

**修改目的**：标记 spec-by-id 相关 API 为废弃。

**工作逻辑**：在三个方法上添加 `@Deprecated` 注解和 Javadoc：
- `specsById()`（响应类的 getter，约第 58 行）：标注废弃，说明自 1.11.0 起将在 1.12.0 降低可见性。
- `withSpecsById(Map<Integer, PartitionSpec> specs)`（Builder 的 setter，约第 94 行）：同样标注废弃。
- `specsById()`（Builder 的 getter，约第 115 行）：同样标注废弃。

三处 Javadoc 均为 "@deprecated since 1.11.0, visibility will be reduced in 1.12.0."

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchPlanningResultResponseParser.java` (+1/-0 lines)

**修改目的**：补全 specsById 设置。

**工作逻辑**：在 Builder 构建响应时，在 `.withDeleteFiles(deleteFiles)` 之后添加 `.withSpecsById(specsById)`，确保分区规范映射被设置到响应对象中。

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchScanTasksResponseParser.java` (+1/-0 lines)

**修改目的**：补全 specsById 设置。

**工作逻辑**：在 Builder 构建响应时，在 `.withPlanTasks(planTasks)` 之后添加 `.withSpecsById(specsById)`。

### `core/src/main/java/org/apache/iceberg/rest/responses/PlanTableScanResponseParser.java` (+1/-0 lines)

**修改目的**：补全 specsById 设置。

**工作逻辑**：在 Builder 构建响应时，在 `.withDeleteFiles(deleteFiles)` 之后添加 `.withSpecsById(specsById)`。

## 总结

本提交做了两件事：一是将 `BaseScanTaskResponse` 及其 Builder 中的 `specsById` 相关 API 标记为 `@Deprecated`（自 1.11.0），为 1.12.0 收紧可见性做铺垫；二是补全三个扫描计划响应解析器中遗漏的 `withSpecsById(specsById)` 调用，确保响应对象中分区规范映射被正确设置。这体现了 API 演进管理的渐进策略：先确保功能正确，再逐步收紧 API 暴露面。
