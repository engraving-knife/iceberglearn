# 提交 3205：REST Spec: Include SetPartitionStatisticsUpdate and RemovePartitionStatisticsUpdate in TableUpdate union (#15115)

## 提交信息

- **序号**：3205 / 4088
- **哈希**：f03595376978fb6c3fad4c374a76507ca0bb87e9
- **短哈希**：f03595376
- **日期**：2026-02-04
- **作者**：Logesh R
- **提交说明**：REST Spec: Include SetPartitionStatisticsUpdate and RemovePartitionStatisticsUpdate in TableUpdate union (#15115)
- **PR/Issue**：#15115

## 总体目的

该提交修复了 Iceberg REST Catalog OpenAPI 规范中 `TableUpdate` 联合类型（union）的遗漏。Iceberg 的提交通过一组 `TableUpdate` 描述对表元数据的变更（如加快照、改 schema、设置统计信息等）。规范中已定义了 `SetPartitionStatisticsUpdate`（设置分区统计）与 `RemovePartitionStatisticsUpdate`（移除分区统计）两个 schema，但它们未被加入 `TableUpdate` 的 `oneOf` 联合类型。

这导致从 OpenAPI 规范自动生成的客户端（如 Python 客户端）及其他规范消费者无法识别这两种更新是合法的 `TableUpdate`。当服务端在提交响应或 `UpdateTableRequest` 中返回/接收分区统计更新时，生成的客户端会因联合类型校验而拒绝这些更新，无法正常处理分区统计相关的元数据变更流程。分区统计（partition statistics）用于优化查询计划，是 Iceberg 表维护的重要能力，该遗漏直接影响基于生成客户端的交互。

本次修复把这两个 schema 通过 `$ref` 加入 `TableUpdate` 的 `oneOf` 列表，并同步重新生成 `rest-catalog-open-api.py`（Python 客户端模型），使生成客户端与其他消费者能正确识别这两种更新。

## 如何达成设计目的

在 `open-api/rest-catalog-open-api.yaml` 的 `TableUpdate` 定义中，于 `RemoveStatisticsUpdate` 之后追加 `SetPartitionStatisticsUpdate` 与 `RemovePartitionStatisticsUpdate` 两个 `$ref` 引用；同时在自动生成的 `open-api/rest-catalog-open-api.py` 的 `TableUpdate` 联合类型中追加对应的两项。两文件改动一致，均为 +2 行。

## 修改详情

### `open-api/rest-catalog-open-api.py` (+2/-0 lines)

**修改目的**：在生成的 Python 模型中把分区统计更新纳入 TableUpdate 联合。

**工作逻辑**：
`TableUpdate` 是一个 `BaseModel` 的 Union 类型（用 `|` 连接各具体 Update 类型）。原联合包含 `RemovePropertiesUpdate | SetStatisticsUpdate | RemoveStatisticsUpdate | RemovePartitionSpecsUpdate | ...`。本次在 `RemoveStatisticsUpdate` 之后追加：

```
| SetPartitionStatisticsUpdate
| RemovePartitionStatisticsUpdate
```

使 Python 生成客户端在反序列化 `TableUpdate` 时能接受这两种分区统计更新对象。这与 yaml 规范保持同步（该文件由规范自动生成）。

### `open-api/rest-catalog-open-api.yaml` (+2/-0 lines)

**修改目的**：在 OpenAPI 规范源文件中将两个分区统计更新加入 TableUpdate 联合。

**工作逻辑**：
`TableUpdate` 的 `oneOf` 数组原本依次列出各 `Update` 的 `$ref`。本次在 `- $ref: '#/components/schemas/RemoveStatisticsUpdate'` 之后追加：

```yaml
- $ref: '#/components/schemas/SetPartitionStatisticsUpdate'
- $ref: '#/components/schemas/RemovePartitionStatisticsUpdate'
```

这两个 schema 此前已在规范中定义（只是未被联合引用），因此无需新增 schema 定义，仅补全联合成员。修改后，规范消费者（代码生成器、校验器）会把这些更新视作合法的 `TableUpdate`，从而正确处理分区统计的设置与移除。

## 总结

该修复补全了 REST Catalog OpenAPI 规范中 `TableUpdate` 联合类型遗漏的两个分区统计更新成员，使生成客户端（Python 等）与其他规范消费者能识别 `SetPartitionStatisticsUpdate` 与 `RemovePartitionStatisticsUpdate` 为合法表更新。改动极小（两文件各 +2 行），但解决了生成客户端无法处理分区统计更新的功能性问题，对基于 REST 规范的跨语言互操作性有实际价值。
