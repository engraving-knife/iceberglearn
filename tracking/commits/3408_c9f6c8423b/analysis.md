# 提交 3408：Core: Sent minRowsRequested to REST server (#15661)

## 提交信息

- **序号**：3408 / 4088
- **哈希**：c9f6c8423be7cbf52c9cf678896deca478b3b6d1
- **短哈希**：c9f6c8423b
- **日期**：2026-03-17 16:57:45 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Sent minRowsRequested to REST server (#15661)
- **PR/Issue**：#15661

## 总体目的

将客户端的 `minRowsRequested` 参数传递到 REST 服务器端，使服务器在扫描规划时能够利用此提示优化返回的文件扫描任务数量。此前 `RESTTableScan` 虽然在客户端上下文中维护了 `minRowsRequested`，但在发送 `PlanTableScanRequest` 到服务器时未包含此参数，服务器无法据此优化扫描结果。

## 如何达成设计目的

1. 在 `RESTTableScan` 构建 `PlanTableScanRequest` 时，将 `context().minRowsRequested()` 添加到请求中
2. 在 `CatalogHandlers` 的 `configureScan` 方法中，将 `minRowsRequested` 设置到配置的扫描上
3. 在 `planFilesFor` 和 `asyncPlanFiles` 方法中传递 `minRowsRequested` 参数，使用 `Iterables.limit` 限制返回的文件扫描任务数量
4. 新增测试验证 `minRowsRequested` 被正确传递到 REST 请求中

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/RESTTableScan.java` (+2/-1 lines)

**修改目的**：在 PlanTableScanRequest 中包含 minRowsRequested。

**工作逻辑**：
- 在构建 `PlanTableScanRequest` 的 builder 链中添加 `.withMinRowsRequested(context().minRowsRequested())`

### `core/src/main/java/org/apache/iceberg/rest/CatalogHandlers.java` (+38/-7 lines)

**修改目的**：在服务器端处理 minRowsRequested 参数。

**工作逻辑**：

**configureScan 方法**：
- 新增检查：`if (request.minRowsRequested() != null) { configuredScan = configuredScan.minRowsRequested(request.minRowsRequested()); }`

**planFilesFor 方法**：
- 新增 `Long minRowsRequested` 参数
- 当 `minRowsRequested` 不为 null 时，使用 `Iterables.limit(planTasks, (int) Math.min(minRowsRequested, Integer.MAX_VALUE))` 限制返回的文件扫描任务数量
- Javadoc 说明：minRowsRequested 是服务器提示，服务器不要求必须返回那么多行，可以返回更多或更少

**asyncPlanFiles 方法**：
- 新增 `Long minRowsRequested` 参数，传递给 `planFilesFor`
- 同步和异步路径都传递此参数

### `core/src/test/java/org/apache/iceberg/rest/TestRESTScanPlanning.java` (+39 lines)

**修改目的**：验证 minRowsRequested 被正确传递到 REST 请求。

**工作逻辑**：
- 新增 `scanPlanningWithMinRowsRequested` 参数化测试（覆盖所有 PlanningMode）
- 向表插入 FILE_B 和 FILE_C
- 使用 `minRowsRequested(1L)` 调用 planFiles，验证返回 1 个任务
- 使用 `ArgumentCaptor` 捕获 HTTPRequest，验证请求体中的 `minRowsRequested` 为 1L
- 使用 `minRowsRequested(100L)` 调用 planFiles，验证返回 3 个任务（所有文件），请求体中 minRowsRequested 为 100L

## 总结

本提交将客户端的 `minRowsRequested` 参数传递到 REST 服务器端，使服务器能据此优化扫描规划，避免返回不必要的过多文件扫描任务。服务器使用 `Iterables.limit` 限制返回的任务数量，但该参数仅作为提示，服务器可根据实际情况返回更多或更少的行。新增测试验证了参数的正确传递。
