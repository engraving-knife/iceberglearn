# 提交 2907：REST: Mark plan-id as required on CompletedPlanningWithIDResult result

## 提交信息

- **序号**：2907 / 4088
- **哈希**：fb52fdef4fdcaa3cb5779e4d5b1b9f1ea7522e73
- **短哈希**：fb52fdef4
- **日期**：2025-11-19 08:53:12 -0800
- **作者**：Prashant Singh
- **提交说明**：REST: Mark plan-id as required on CompletedPlanningWithIDResult result
- **PR/Issue**：无（提交说明中未含 #编号）

## 总体目的

本提交与提交 2906 同属一组 OpenAPI 契约收紧改动，二者由同一作者在同一天提交，共同把"涉及后续追踪的规划响应中的 `plan-id`"从可选改为必填。2906 处理的是 `AsyncPlanningResult`（`status: submitted` 的异步响应），本提交（2907）处理的是 `CompletedPlanningWithIDResult`（`status: completed` 且带 `plan-id` 的完成响应）。

要理解本提交的动机，需要先厘清 REST Catalog 规划响应的两条链路与两个"completed" schema 的区别：

1. `PlanTableScanResult`（`PlanTableScan` 接口的响应）通过 `status` 判别器把 `completed` 映射到 `CompletedPlanningWithIDResult`。也就是说，当客户端发起首次 `PlanTableScan` 且服务端同步完成规划时，返回的就是 `CompletedPlanningWithIDResult`——它除了携带首批 `FileScanTask`，还会带一个 `plan-id`，客户端用这个 `plan-id` 去调用 `FetchScanTasks` 拉取后续的分页规划任务。
2. `FetchPlanningResult`（轮询异步规划结果的接口）把 `completed` 映射到的是 `CompletedPlanningResult`（不带 `plan-id`），因为这是对已提交异步规划的最终取回，不需要再给一个新的 `plan-id`。

由此可见，`CompletedPlanningWithIDResult` 中的 `plan-id` 是客户端继续拉取分页任务的凭据，与 `AsyncPlanningResult` 中的 `plan-id` 一样不可或缺。但此前契约里它却是可选的（pydantic `Optional[str] = None`，YAML 未列入 `required`），这与"completed 且分页"的语义矛盾：一个 `CompletedPlanningWithIDResult` 若不带 `plan-id`，客户端无法继续 `FetchScanTasks`，首批之后的任务就丢了。本提交把 `plan-id` 在该 schema 中标记为必填，使契约与语义一致。

值得指出的是，这与提交 2902 的 Java 实现互相印证：2902 在 `CatalogHandlers` 的同步完成分支里补回了 `.withPlanId(planId)`，确保同步完成响应一定带 `plan-id`；本提交则在规范层面把这一行为固化为契约要求。两组改动（2906 + 2907 的规范收紧，与 2902 的 Java 行为）共同保证了无论同步完成还是异步提交，规划响应都一定携带 `plan-id`。

## 如何达成设计目的

本提交只改 OpenAPI 规范文件，不涉及 Java 代码。在 `rest-catalog-open-api.yaml` 的 `CompletedPlanningWithIDResult` schema 的 `allOf` 第二个子对象中新增 `required: [plan-id]`；在 `rest-catalog-open-api.py` 的 pydantic 模型 `CompletedPlanningWithIDResult`（继承自 `CompletedPlanningResult`）中把 `plan_id` 从 `Optional[str] = Field(None, ...)` 改为 `str = Field(..., ...)`。两处改动语义一致，与 2906 对 `AsyncPlanningResult` 的处理方式完全对称。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+2/-0 lines)

**修改目的**：在 `CompletedPlanningWithIDResult` schema 中把 `plan-id` 加入 `required` 列表。

**工作逻辑**：
`CompletedPlanningWithIDResult` 通过 `allOf` 组合 `CompletedPlanningResult`（提供 `status: completed` 及首批任务字段）与一个内联对象（提供 `plan-id`）。改动前该内联对象只有 `properties.plan-id`，无 `required`；改动后：
```yaml
CompletedPlanningWithIDResult:
  type: object
  allOf:
    - $ref: '#/components/schemas/CompletedPlanningResult'
    - type: object
      required:
        - plan-id
      properties:
        plan-id:
          description: ID used to track a planning request
          type: string
```
`plan-id` 现为必填。需要与 `CompletedPlanningResult`（用于 `FetchPlanningResult` 的 completed 分支，不带 plan-id）区分：本提交只收紧 `CompletedPlanningWithIDResult`（用于 `PlanTableScan` 的 completed 分支），不影响 `CompletedPlanningResult`。`PlanTableScanResult` 判别器把 `completed` 映射到 `CompletedPlanningWithIDResult`，因此所有 `PlanTableScan` 的完成响应现在都必须携带 `plan-id`，与客户端需要用它继续 `FetchScanTasks` 的流程对齐。

### `open-api/rest-catalog-open-api.py` (+2/-2 lines)

**修改目的**：在 pydantic 等价模型中同步把 `plan_id` 改为必填，保持 `.py` 与 `.yaml` 一致。

**工作逻辑**：
`CompletedPlanningWithIDResult` 继承自 `CompletedPlanningResult` 并覆盖 `status` 为 `Literal['completed']`、新增 `plan_id`。改动前：
```python
class CompletedPlanningWithIDResult(CompletedPlanningResult):
    plan_id: Optional[str] = Field(
        None, alias='plan-id', description='ID used to track a planning request'
    )
    status: Literal['completed']
```
改动后：
```python
class CompletedPlanningWithIDResult(CompletedPlanningResult):
    plan_id: str = Field(
        ..., alias='plan-id', description='ID used to track a planning request'
    )
    status: Literal['completed']
```
类型从 `Optional[str]` 改为 `str`；`Field` 第一参数从默认值 `None` 改为 `...`（pydantic 必填哨兵）。`alias` 与描述不变。这与 2906 中 `AsyncPlanningResult.plan_id` 的改法完全一致，两处对称收紧。

## 总结

本提交作为 2906 的姊妹改动，把 `CompletedPlanningWithIDResult`（`PlanTableScan` 同步完成且带分页凭据的响应）中的 `plan-id` 从可选收紧为必填，修正了"completed 分页响应却不带可追踪 plan-id"的契约缺陷，使规范与客户端必须凭 `plan-id` 调用 `FetchScanTasks` 拉取后续任务的实际流程一致。本提交与 2906 一起覆盖了所有需要 `plan-id` 进行后续追踪的规划响应（submitted 与 completed-with-id），并与提交 2902 的 Java 实现互相印证，形成规范与实现的闭环。
