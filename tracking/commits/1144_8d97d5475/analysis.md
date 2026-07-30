# 提交 1144：OpenAPI: Add Scan Planning Endpoints to REST spec (#9695)

## 提交信息

- **序号**：1144
- **哈希**：8d97d54756a1b8ba1986858b2461013864b9e37f
- **短哈希**：8d97d5475
- **日期**：2024-09-10（Tue Sep 10 07:34:33 2024 -0700）
- **作者**：Rahil C <32500120+rahil-c@users.noreply.github.com>
- **提交说明**：OpenAPI: Add Scan Planning Endpoints to REST spec (#9695)
- **PR/Issue**：#9695

## 总体目的

Iceberg 的 REST Catalog 规范（`open-api/rest-catalog-open-api.yaml` 与由其生成的 Python 模型 `rest-catalog-open-api.py`）原本仅定义了表/元数据/提交相关接口，扫描（scan）的实际文件规划（file planning）一直由客户端自行完成——客户端拉取 manifest 后根据 snapshot、filter、partition spec 自行计算需要读取的 DataFile/DeleteFile 列表。

随着多引擎、多租户场景下"将扫描规划下沉到服务端"的需求出现（服务端可能拥有更完整的元数据缓存、更优的统计信息、更强的算力），社区决定在 REST 规范中引入一组**服务端扫描规划（server-side scan planning）端点**，让客户端可以请求服务端代为完成扫描规划，并以"任务（task）"的形式分批获取结果。本提交即向 REST 规范中追加这组端点及其对应的请求/响应 schema。

核心目标是：
1. 让扫描规划可以"异步化"——服务端可以返回 `submitted` 状态，客户端轮询拉取；
2. 让结果可以"分页化"——通过 `plan-task` 概念把大扫描拆分为多个 fetch 请求；
3. 让客户端可以"取消"未完成的规划，释放服务端资源；
4. 顺带补全 `Expression` 模型中缺失的 `True`/`False` 字面量表达式，使 `residual-filter` 等字段能表达"无过滤剩余"等场景。

## 如何达成设计目的

通过同步修改 OpenAPI 的两份等价描述文件完成：

1. **YAML 规范**（权威源）：在 `paths:` 下新增 4 条路径，分别对应 4 个端点：
   - `POST /v1/{prefix}/namespaces/{namespace}/tables/{table}/plan` —— 提交扫描规划请求（`planTableScan`）
   - `GET /v1/{prefix}/namespaces/{namespace}/tables/{table}/plan/{plan-id}` —— 拉取规划结果（`fetchPlanningResult`）
   - `DELETE /v1/{prefix}/namespaces/{namespace}/tables/{table}/plan/{plan-id}` —— 取消规划（`cancelPlanning`）
   - `POST /v1/{prefix}/namespaces/{namespace}/tables/{table}/tasks` —— 按 plan-task 拉取文件扫描任务（`fetchScanTasks`）
2. **components/schemas** 中新增 `PlanTableScanRequest`、`PlanTableScanResult`、`FetchPlanningResult`、`FetchScanTasksRequest`、`FetchScanTasksResult`、`ScanTasks`、`PlanTask`、`FileScanTask`、`PlanStatus`、`CompletedPlanningResult`、`CompletedPlanningWithIDResult`、`AsyncPlanningResult`、`EmptyPlanningResult`、`FailedPlanningResult`、`DeleteFile`、`FieldName`、`TrueExpression`、`FalseExpression` 等数据模型，并扩展 `Expression` 的 `oneOf`。
3. **components/parameters** 新增 `plan-id` 路径参数；**components/responses** 新增 3 个响应包装；**components/examples** 新增 `NoSuchPlanIdError`、`NoSuchPlanTaskError`。
4. **修复既有 schema**：`ContentFile.partition` 由 `Optional` 改为必填，并把 YAML 描述中跨行字符串重新折行。
5. **Python 模型**（`rest-catalog-open-api.py`）按相同语义同步生成对应 Pydantic 模型，并在文件末尾的 `update_forward_refs()` 列表中追加前向引用修正，避免循环依赖解析失败。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

#### 新增端点 1：`POST .../plan`（planTableScan）

**功能**：提交一次服务端扫描规划。请求体为 `PlanTableScanRequest`，响应为 `PlanTableScanResponse`（实际 schema 是 `PlanTableScanResult`，按 `status` 字段做 `oneOf` 判别）。

**请求字段**（`PlanTableScanRequest`）：
- `snapshot-id`（int64，可选）：点查询扫描的快照 ID；
- `select`（FieldName 数组，可选）：投影列；
- `filter`（Expression，可选）：过滤表达式；
- `case-sensitive`（bool，默认 true）：是否大小写敏感；
- `use-snapshot-schema`（bool，默认 false）：time-travel 时使用快照写入时的 schema（true），分支扫描时使用表当前 schema（false）；
- `start-snapshot-id` / `end-snapshot-id`（int64，可选）：增量扫描的起止快照，二者必须同时出现；
- `stats-fields`（FieldName 数组，可选）：要求服务端返回统计信息的列。

**响应状态语义**（在 description 中明确规定）：
- `completed`：规划完成，响应中必须包含 file-scan-tasks 与/或 plan-tasks；不可后续 fetch；
- `submitted`：服务端持有了状态并返回 `plan-id`，客户端需轮询 `fetchPlanningResult`；
- `failed`：响应必须是合法的错误响应；
- `cancelled` 在此端点视为非法（不允许在 `planTableScan` 响应中返回）。

**错误码**：400 / 401 / 403 / 404（表/命名空间不存在）/ 406 / 419 / 503 / 5XX。

#### 新增端点 2：`GET .../plan/{plan-id}`（fetchPlanningResult）

**功能**：用 `plan-id` 轮询规划结果。响应 schema 为 `FetchPlanningResult`，按 `status` 做 `oneOf`，支持 `completed`/`submitted`/`cancelled`/`failed` 四种状态——比 `planTableScan` 多了 `cancelled`（表示该 plan-id 已失效，客户端应丢弃）。

#### 新增端点 3：`DELETE .../plan/{plan-id}`（cancelPlanning）

**功能**：通知服务端释放为该 `plan-id` 持有的资源。成功返回 204 No Content。description 明确说明：当客户端已通过 `fetchScanTasks` 拉取完所有 plan-task 对应的扫描任务后，无需调用 cancel；只有在 `submitted` 期间或仍有未拉取 plan-task 时主动放弃才需要 cancel。

#### 新增端点 4：`POST .../tasks`（fetchScanTasks）

**功能**：把 `planTableScan`/`fetchPlanningResult` 返回的某个 `plan-task`（不透明字符串）作为请求体，换取该 plan-task 对应的 file-scan-tasks 列表。响应为 `FetchScanTasksResponse`，schema 为 `FetchScanTasksResult`（`ScanTasks` 的 allOf 扩展）。这是"分页拉取大扫描结果"的关键设计——服务端可以将一次大扫描拆为多个 plan-task，客户端按需拉取，避免单次响应过大。

#### 新增 `components/parameters/plan-id`

定义 `plan-id` 路径参数：`in: path`，`required: true`，`type: string`，描述为"ID used to track a planning request"。

#### 扩展 `Expression` 与新增 `TrueExpression`/`FalseExpression`

`Expression` 的 `oneOf` 在原有 `AndOrExpression`/`NotExpression`/`SetExpression` 之前新增 `TrueExpression` 与 `FalseExpression`。这两个 schema 仅含一个 `type` 字段，枚举值分别为 `"true"` 与 `"false"`，表达"恒真/恒假"的字面量表达式。`ExpressionType` 的 example 列表也相应追加 `"true"` 与 `"false"`。这是为了让 `residual-filter` 等字段能表达"过滤已完全在服务端应用，剩余谓词恒真"等语义。

#### 新增 `ScanTasks` 及其派生 schema

`ScanTasks` 是核心结果容器，包含三个可选数组：
- `delete-files`：被 file-scan-tasks 引用的删除文件列表（去重后由服务端集中下发）；
- `file-scan-tasks`：实际文件扫描任务；
- `delete-file-references` 通过 `FileScanTask` 内的索引数组指向 `delete-files`，避免重复传输 DeleteFile 内容。

围绕 `ScanTasks` 派生：
- `CompletedPlanningResult = ScanTasks + status:"completed"`；
- `CompletedPlanningWithIDResult = CompletedPlanningResult + plan-id?`（用于 `planTableScan` 响应，可能附带 plan-id 供后续取消）；
- `FailedPlanningResult = IcebergErrorResponse + status:"failed"`；
- `AsyncPlanningResult = { status:"submitted", plan-id? }`；
- `EmptyPlanningResult = { status: "submitted"|"cancelled" }`（用于 fetchPlanningResult 的非 completed/failed 分支）；
- `PlanStatus` 枚举 `completed`/`submitted`/`cancelled`/`failed`；
- `FetchPlanningResult` 与 `PlanTableScanResult` 通过 `discriminator: propertyName: status` 做 oneOf 判别，分别用于两个端点的响应。

#### 新增 `PlanTableScanRequest`/`FetchScanTasksRequest`/`PlanTask`/`FileScanTask`/`FieldName`/`DeleteFile`

- `PlanTask`：opaque string，由服务端生成，代表"一个待 fetch 的工作单元"；
- `FileScanTask`：`data-file`（DataFile）+ `delete-file-references`（整数索引数组，指向同响应中 `delete-files`）+ `residual-filter`（可选 Expression，需客户端再行求值的剩余谓词）；
- `FieldName`：完整字段名字符串，支持 `a.b.c`、`map.key.x`、`map.value.y`、`list.element.z` 等嵌套形式；
- `FetchScanTasksRequest`：`{ plan-task: PlanTask }`；
- `DeleteFile`：discriminator `content` 的 oneOf，区分 `PositionDeleteFile` 与 `EqualityDeleteFile`，与既有 ContentFile 体系对齐。

#### 修复 `ContentFile.partition`

将 `partition` 字段由 `Optional[List[PrimitiveTypeValue]]`（默认 None）改为必填 `List[PrimitiveTypeValue]`。理由：partition 值是文件定位的关键信息，对 unpartitioned 表也会传空数组，没有理由设为可选。这是发现并修复的既有规范缺陷。

同时把 `partition` 字段 description 中的换行字符串重新折行，避免 YAML 中跨行字符串的歧义。

#### 新增响应/示例

- `components/responses` 新增 `PlanTableScanResponse`、`FetchPlanningResultResponse`、`FetchScanTasksResponse`；
- `components/examples` 新增 `NoSuchPlanIdError`、`NoSuchPlanTaskError`，配套 404 响应使用。

#### 顺手修正：`snapshots` 参数描述的尾随空格

`loadTable` 的 `snapshots` 查询参数 description 末尾原本有一处尾随空格被同步修正为普通空格，属格式微调。

### `open-api/rest-catalog-open-api.py`

与 YAML 等价的 Pydantic 模型同步追加：

- `TrueExpression`、`FalseExpression`：仅含 `type: ExpressionType`；
- `ExpressionType` 的 example 列表追加 `'true'`、`'false'`；
- `Expression.__root__` 的 `Union` 在最前追加 `TrueExpression`、`FalseExpression`；
- `PlanStatus`：`Literal['completed', 'submitted', 'cancelled', 'failed']`；
- `FieldName`、`PlanTask`：均为 `__root__: str` 的包装类型，附详细 description；
- `FailedPlanningResult(IcebergErrorResponse)`、`AsyncPlanningResult`、`EmptyPlanningResult`：分别承载各 status 分支；
- `ScanTasks`：`delete_files`/`file_scan_tasks`/`plan_tasks` 三可选字段，对应 YAML `delete-files`/`file-scan-tasks`/`plan-tasks`；
- `CompletedPlanningResult(ScanTasks)` + `status: Literal['completed']`；
- `CompletedPlanningWithIDResult(CompletedPlanningResult)` + `plan_id` + `status: Literal['completed']`（重复声明 status 是为了在子类中收窄 Literal）；
- `FetchPlanningResult` / `PlanTableScanResult`：`__root__: Union[...]` 加 `discriminator='status'`；
- `DeleteFile`：`Union[PositionDeleteFile, EqualityDeleteFile]` 加 `discriminator='content'`；
- `FetchScanTasksRequest`：`{ plan_task: PlanTask }`；
- `PlanTableScanRequest`：完整字段，含 `snapshot_id`、`select`、`filter`、`case_sensitive`、`use_snapshot_schema`、`start_snapshot_id`、`end_snapshot_id`、`stats_fields`；
- `FileScanTask`：`data_file`、`delete_file_references`、`residual_filter`；
- `FetchScanTasksResult(ScanTasks)`：空扩展；
- `ContentFile.partition` 由 `Optional[List[PrimitiveTypeValue]] = Field(None, ...)` 改为 `List[PrimitiveTypeValue] = Field(...)`（必填），与 YAML 一致；
- 末尾 `update_forward_refs()` 列表追加 `ScanTasks`、`FetchPlanningResult`、`PlanTableScanResult`、`CompletedPlanningResult`、`FetchScanTasksResult`、`CompletedPlanningWithIDResult`，解决前向引用导致的模型解析顺序问题。

## 小结

- **成效**：REST Catalog 规范新增了一整套服务端扫描规划端点（4 个 endpoint + ~20 个 schema + 2 个 example + 1 个 path parameter），允许将扫描规划从客户端下沉到服务端，支持异步、分页、取消三种交互模式；并补全了 `True`/`False` 字面量表达式与 `ContentFile.partition` 必填化两处既有规范缺陷。
- **影响范围**：仅 `open-api/rest-catalog-open-api.yaml`（+577/-5）与 `open-api/rest-catalog-open-api.py`（+195/-5）两个规范文件，无任何 Java/构建代码变更。规范本身是"接口契约"，不影响已有实现的行为；但任何 REST Catalog 实现一旦声明支持这些端点，就需要按此契约实现。
- **回迁到 1.4.x 的注意事项**：
  - 这是**新增规范端点**，不修改既有端点行为（除 `ContentFile.partition` 由可选改必填这一处契约变更）。
  - 1.4.x 作为已发布的维护分支，其 REST 规范通常已冻结。回迁此提交意味着 1.4.x 的 REST Catalog 规范会新增 4 个端点——若 1.4.x 的服务端实现并不打算支持服务端扫描规划，回迁规范反而会让客户端误以为可用，**不建议盲目回迁**。
  - `ContentFile.partition` 改为必填这一处是**潜在的兼容性破坏**：若 1.4.x 的客户端曾依赖 partition 可选（例如对 unpartitioned 表省略该字段），回迁后客户端模型校验可能失败。回迁前需评估 1.4.x 客户端代码对 `partition` 缺省值的处理。
  - 若 1.4.x 确需支持服务端 scan planning（例如对接的引擎要求），则建议**整笔回迁** yaml + py 两份文件，保持契约一致；同时需在服务端实现层补齐对应的 endpoint handler，否则规范与实现脱节。
  - 注意：后续提交（如 #11119，见提交 1151）会对本提交引入的 YAML 做格式修复，回迁时应一并考虑。
