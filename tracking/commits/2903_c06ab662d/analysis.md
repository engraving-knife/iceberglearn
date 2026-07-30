# 提交 2903：OpenAPI: Add min-rows-requested field to PlanTableScanRequest (#14565)

## 提交信息

- **序号**：2903 / 4088
- **哈希**：c06ab662d2652aa900f66f9e7b5592a6d7ab693e
- **短哈希**：c06ab662d
- **日期**：2025-11-21 03:55:45 -0800
- **作者**：ajreid21
- **提交说明**：OpenAPI: Add min-rows-requested field to PlanTableScanRequest (#14565)
- **PR/Issue**：#14565

## 总体目的

本提交是 REST Catalog OpenAPI 规范侧的改动，为 `PlanTableScanRequest` 请求体新增一个可选字段 `min-rows-requested`。该字段属于配对提交中的"规范先行"一半——先在 OpenAPI 契约里定义新字段，再由后续 Java 实现提交（见 2904）在服务端/客户端落地对该字段的读取与处理。

动机在于：远程扫描规划（`PlanTableScan`）场景下，客户端在向服务端请求规划任务时，往往并不需要服务端返回全部的扫描任务，而只需要"至少 N 行数据"对应的扫描任务即可。如果客户端能够把这个最小行数需求作为 hint 传给服务端，服务端就可以在规划时提前停止，不必返回超出需要的 `FileScanTask`，从而减少网络传输、客户端缓存压力以及后续不必要的任务调度。该字段明确被定义为"提示"而非"硬性约束"：服务端可以因为扫描本身产出行数不足而返回少于该值的行，也可以出于实现便利返回更多行；这保证了字段引入不会破坏现有的规划语义。

## 如何达成设计目的

本提交仅修改 OpenAPI 规范文件，不涉及任何 Java 代码。在 `rest-catalog-open-api.yaml` 的 `PlanTableScanRequest` schema 中新增 `min-rows-requested` 属性（`type: integer`、`format: int64`、带描述），并在 `rest-catalog-open-api.py`（用 Python pydantic 模型描述的等价 OpenAPI 定义）中同步新增 `min_rows_requested` 字段（`Optional[int]`、`alias='min-rows-requested'`、同样描述）。两份文件保持一致。字段位于 `filter` 之后、`case-sensitive` 之前，未设置默认值（即缺省为 `None`/不传），保持向后兼容。

## 修改详情

### `open-api/rest-catalog-open-api.yaml` (+8/-0 lines)

**修改目的**：在 `PlanTableScanRequest` 的 YAML schema 中声明新字段 `min-rows-requested`。

**工作逻辑**：
在 `PlanTableScanRequest` 组件的 `filter`（`$ref: '#/components/schemas/Expression'`）之后新增：
```yaml
min-rows-requested:
  description:
    The minimum number of rows requested for the scan. This is used as a hint
    to the server to not have to return more rows than necessary. It is not required
    for the server to return that many rows since the scan may not produce that
    many rows. The server can also return more rows than requested.
  type: integer
  format: int64
```
字段类型为 64 位整数，描述明确其"提示"语义：用于让服务端不必返回超出必要的行；不强制服务端必须返回这么多行（扫描本身可能不足），也允许服务端返回更多行。未指定 `default`，因此该字段可选，向后兼容。

### `open-api/rest-catalog-open-api.py` (+5/-0 lines)

**修改目的**：在 pydantic 描述的等价 OpenAPI 模型 `PlanTableScanRequest` 中同步新增 `min_rows_requested` 字段，保证 `.py` 与 `.yaml` 两份规范一致。

**工作逻辑**：
在 `filter` 字段之后新增：
```python
min_rows_requested: Optional[int] = Field(
    None,
    alias='min-rows-requested',
    description='The minimum number of rows requested for the scan. ...',
)
```
使用 `Optional[int]` 默认 `None`，`alias='min-rows-requested'` 与 YAML 中的连字符命名保持一致；描述文案与 YAML 完全相同。pydantic 模型会被用来生成/校验 OpenAPI 文档，所以两份文件必须同步——本提交同时改了两处。

## 总结

本提交作为规范先行的一半，在 REST Catalog OpenAPI 契约（`.yaml` 与 `.py` 两份等价定义）中为 `PlanTableScanRequest` 新增可选的 `min-rows-requested` 字段，将其定义为客户端给服务端的"最少行数提示"，语义上既不强制下限也不强制上限，从而为后续 Java 实现（提交 2904）中服务端据此提前停止规划、减少返回任务量提供了契约依据，同时保持对旧客户端的向后兼容。
