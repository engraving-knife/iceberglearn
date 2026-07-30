# 提交 2906：SPEC: Mark plan-id as required for submitted status

## 提交信息

- **序号**：2906 / 4088
- **哈希**：9d7213db4875688dcc582729d0aeea61e4b08e73
- **短哈希**：9d7213db4
- **日期**：2025-11-19 02:53:58 -0800
- **作者**：Prashant Singh
- **提交说明**：SPEC: Mark plan-id as required for submitted status
- **PR/Issue**：无（提交说明中未含 #编号）

## 总体目的

本提交是 REST Catalog OpenAPI 规范侧的改动，是另一组"规范先行 / 实现配对"提交的规范一半，配对的 Java 实现为提交 2907。它针对 `AsyncPlanningResult` 这个 schema 做一处契约收紧：把 `plan-id` 从可选改为必填。

背景在于 REST Catalog 的异步扫描规划流程：当客户端发起 `PlanTableScan` 请求、服务端选择异步处理时，会返回一个状态为 `submitted` 的 `AsyncPlanningResult`，其中携带 `plan-id`；客户端随后必须用这个 `plan-id` 去轮询 `FetchPlanningResult` 才能拿到最终的规划任务。也就是说，`plan-id` 是异步流程能够继续下去的唯一凭据——没有它，客户端根本无法取回结果，这条异步规划就等于丢了。

然而在之前的 OpenAPI 契约里，`AsyncPlanningResult.plan-id` 却被声明为可选（pydantic 中为 `Optional[str] = None`，YAML 中未列入 `required`）。这与该 schema 的语义自相矛盾：一个 `submitted` 状态的异步响应若不带 `plan-id` 对客户端毫无用处。本提交把 `plan-id` 在 `AsyncPlanningResult` 中标记为必填，使契约与实际语义一致，并为 Java 侧（2907）把该字段从可空改为非空、并强制序列化提供规范依据。需要区分的是，同文件中的 `EmptyPlanningResult`（用于 `cancelled` 等状态）不需要 `plan-id`，本提交只影响 `AsyncPlanningResult`（即 `submitted` 分支）。

## 如何达成设计目的

本提交只改 OpenAPI 规范文件，不涉及 Java 代码。在 `rest-catalog-open-api.yaml` 的 `AsyncPlanningResult` schema 的 `required` 列表中追加 `plan-id`；在 `rest-catalog-open-api.py` 的等价 pydantic 模型 `AsyncPlanningResult` 中把 `plan_id` 从 `Optional[str] = Field(None, ...)` 改为 `str = Field(..., ...)`（即从"可选、默认 None"改为"必填、无默认值"）。两处改动语义一致，与 `status`（已是必填、const 为 `submitted`）保持同一必填级别。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+1/-0 lines)

**修改目的**：在 `AsyncPlanningResult` schema 中把 `plan-id` 加入 `required` 列表。

**工作逻辑**：
`AsyncPlanningResult` 定义为 `status` 固定为 `"submitted"` 的对象。改动前其 `required` 仅含 `status`，`plan-id` 虽在 `properties` 中声明（`type: string`，描述为"ID used to track a planning request"）但非必填。改动后：
```yaml
AsyncPlanningResult:
  type: object
  required:
    - status
    - plan-id
  properties:
    status:
      $ref: '#/components/schemas/PlanStatus'
      const: "submitted"
    plan-id:
      description: ID used to track a planning request
      type: string
```
`plan-id` 现在与 `status` 同为必填项。这意味着任何 `status: submitted` 的 `AsyncPlanningResult` 响应必须携带 `plan-id`，否则按规范视为非法响应。相邻的 `EmptyPlanningResult`（`submitted`/`cancelled` 时可用的空结果）不受影响，仍只要求 `status`。

### `open-api/rest-catalog-open-api.py` (+2/-2 lines)

**修改目的**：在 pydantic 等价模型中同步把 `plan_id` 改为必填，保持 `.py` 与 `.yaml` 一致。

**工作逻辑**：
改动前：
```python
class AsyncPlanningResult(BaseModel):
    status: Literal['submitted'] = Field(..., const=True)
    plan_id: Optional[str] = Field(
        None, alias='plan-id', description='ID used to track a planning request'
    )
```
改动后：
```python
class AsyncPlanningResult(BaseModel):
    status: Literal['submitted'] = Field(..., const=True)
    plan_id: str = Field(
        ..., alias='plan-id', description='ID used to track a planning request'
    )
```
两处变化：类型从 `Optional[str]` 改为 `str`；`Field` 第一参数从默认值 `None` 改为 `...`（pydantic 的"必填"哨兵值，表示无默认值且必须提供）。`alias='plan-id'` 与描述保持不变。这样 pydantic 在校验/生成文档时会要求 `plan-id` 必须出现，与 YAML 的 `required` 一致。

## 总结

本提交作为规范先行的一半，把 `AsyncPlanningResult`（即 `submitted` 状态的异步规划响应）中的 `plan-id` 从可选收紧为必填，修正了"异步响应却不带可追踪的 plan-id"这一与语义矛盾的设计，使 OpenAPI 契约与客户端必须凭 `plan-id` 轮询 `FetchPlanningResult` 的实际流程保持一致，并为配对的 Java 实现（提交 2907）把对应字段改为非空并强制序列化提供了契约依据。
