# 提交 2506：Core: Request/Response models and parsers for REST Scan Planning (#13004)

## 提交信息

- **序号**：2506 / 4088
- **哈希**：d5476cae1b4e9b560f0126b7b9117630da9a37ca
- **短哈希**：d5476cae1
- **日期**：2025-08-15 14:11:40 -0600
- **作者**：Prashant Singh
- **提交说明**：Core: Request/Response models and parsers for REST Scan Planning (#13004)
- **PR/Issue**：#13004

## 总体目的

本提交为 Iceberg REST Catalog 引入了 Scan Planning（扫描计划）的请求/响应数据模型和 JSON 解析器，是 REST Catalog 服务端扫描计划功能的基础设施层。

传统的 REST Catalog 扫描流程中，客户端在 `loadTable` 后本地执行扫描计划（scan planning），生成 FileScanTask。然而对于大型表，将扫描计划下推到服务端执行可以减轻客户端负担，并允许服务端进行优化。本提交实现了 REST Scan Planning 协议的数据模型层，支持两种模式：

1. **同步模式**：客户端提交 `PlanTableScanRequest`，服务端立即返回 `PlanTableScanResponse`（status=completed），包含完整的 FileScanTask 列表。
2. **异步模式**：服务端返回 planId（status=submitted），客户端随后通过 `FetchScanTasksRequest` 逐个获取 plan task 对应的 FileScanTask，或通过 `FetchPlanningResultResponse` 获取最终结果。

这为后续实现服务端扫描计划的客户端和服务端逻辑奠定了数据基础。

## 如何达成设计目的

关键设计点：

1. **PlanStatus 枚举**：定义扫描计划的状态（COMPLETED、SUBMITTED、CANCELLED、FAILED）。
2. **BaseScanTaskResponse 基类**：抽取 planTasks、fileScanTasks、deleteFiles、specsById 等公共字段，使用泛型 Builder 模式支持子类链式构建。
3. **PlanTableScanRequest**：封装扫描计划请求参数（snapshotId、select、filter、caseSensitive、useSnapshotSchema、startSnapshotId、endSnapshotId、statsFields），包含互斥验证逻辑。
4. **三种响应类型**：
   - `PlanTableScanResponse`：初始计划响应，包含 planStatus 和可选的 planId 或 task 列表。
   - `FetchScanTasksResponse`：获取单个 plan task 的响应。
   - `FetchPlanningResultResponse`：获取计划最终结果的响应。
5. **Parser 体系**：为每种请求/响应实现 JSON 序列化/反序列化 parser。
6. **RESTSerializers**：注册自定义 Jackson 序列化器，处理 Expression、FileScanTask、DeleteFile 等复杂类型的 JSON 转换。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/PlanStatus.java` (+48/-0 lines, 新文件)

**修改目的**：定义扫描计划状态枚举。

**工作逻辑**：定义 COMPLETED、SUBMITTED、CANCELLED、FAILED 四种状态，提供 `fromName` 静态工厂方法从字符串解析。

### `core/src/main/java/org/apache/iceberg/rest/responses/BaseScanTaskResponse.java` (+113/-0 lines, 新文件)

**修改目的**：提取扫描任务响应的公共基类。

**工作逻辑**：包含 planTasks（字符串列表，异步模式下的任务描述）、fileScanTasks、deleteFiles、specsById 字段。使用泛型 Builder 模式 `Builder<B extends Builder<B, R>, R extends BaseScanTaskResponse>` 支持子类链式调用。

### `core/src/main/java/org/apache/iceberg/rest/requests/PlanTableScanRequest.java` (+174/-0 lines, 新文件)

**修改目的**：定义扫描计划请求模型。

**工作逻辑**：包含 snapshotId、select（投影列）、filter（过滤表达式）、caseSensitive、useSnapshotSchema、startSnapshotId/endSnapshotId（增量扫描）、statsFields。`validate()` 确保要么提供 snapshotId，要么同时提供 startSnapshotId 和 endSnapshotId（互斥）。

### `core/src/main/java/org/apache/iceberg/rest/responses/PlanTableScanResponse.java` (+110/-0 lines, 新文件)

**修改目的**：定义扫描计划响应模型。

**工作逻辑**：继承 BaseScanTaskResponse，新增 planStatus 和 planId。`validate()` 确保状态与内容的约束关系：submitted 时必须有 planId 且无 tasks；completed 时有 tasks 且无 planId；cancelled 不合法。

### `core/src/main/java/org/apache/iceberg/rest/requests/FetchScanTasksRequest.java` (+47/-0 lines, 新文件)

**修改目的**：定义获取扫描任务请求。

**工作逻辑**：仅包含 planTask 字段（一个字符串，标识要获取的任务），validate 确保 non-null。

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchScanTasksResponse.java` (+65/-0 lines, 新文件)

**修改目的**：定义获取扫描任务响应。

**工作逻辑**：继承 BaseScanTaskResponse，validate 确保 planTasks 和 fileScanTasks 不同时为 null，且 deleteFiles 仅在有 fileScanTasks 时存在。

### `core/src/main/java/org/apache/iceberg/rest/responses/FetchPlanningResultResponse.java` (+81/-0 lines, 新文件)

**修改目的**：定义获取计划结果响应。

**工作逻辑**：继承 BaseScanTaskResponse，新增 planStatus。validate 确保仅在 completed 状态下返回 tasks。

### Parser 文件 (6 个新文件)

- `PlanTableScanRequestParser.java` (+135 lines)
- `FetchScanTasksRequestParser.java` (+57 lines)
- `PlanTableScanResponseParser.java` (+106 lines)
- `FetchScanTasksResponseParser.java` (+88 lines)
- `FetchPlanningResultResponseParser.java` (+96 lines)
- `TableScanResponseParser.java` (+127 lines)
- `RESTFileScanTaskParser.java` (+109 lines)

**修改目的**：为每种请求/响应实现 JSON 序列化/反序列化。

### `core/src/main/java/org/apache/iceberg/rest/RESTSerializers.java` (+153/-0 lines, 新文件)

**修改目的**：注册自定义 Jackson 序列化器。

**工作逻辑**：注册 ExpressionSerializer、FileScanTaskSerializer、DeleteFileSerializer 等自定义序列化器，处理 Iceberg 特有类型的 JSON 转换。

### `core/src/main/java/org/apache/iceberg/ContentFileParser.java` (+12/-5 lines)

**修改目的**：增强 ContentFileParser 以支持扫描计划场景。

### 测试文件 (7 个新/修改文件)

- `TestPlanTableScanRequest.java` (+149 lines)
- `TestFetchScanTasksRequest.java` (+50 lines)
- `TestPlanTableScanResponseParser.java` (+232 lines)
- `TestFetchScanTasksResponseParser.java` (+159 lines)
- `TestFetchPlanningResultResponseParser.java` (+231 lines)
- `TestContentFileParser.java` (+9/-3 lines)
- `TestBase.java` (+4/-3 lines)

**修改目的**：验证请求/响应模型的序列化、反序列化和验证逻辑。

## 总结

本提交是 REST Scan Planning 功能的数据层基础，新增了约 2354 行代码（含测试）。通过定义清晰的请求/响应模型、状态枚举和 JSON parser，为后续实现服务端扫描计划的客户端调用和服务端处理奠定了基础。设计采用了泛型 Builder 模式和严格的验证逻辑，保证了数据模型的健壮性。这是一个具有前瞻性的大型基础设施提交，将支持 Iceberg REST Catalog 在大表场景下的扫描计划下推优化。
