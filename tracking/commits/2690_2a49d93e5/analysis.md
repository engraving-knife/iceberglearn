# 提交 2690：REST: Add missing "added-rows" field to snapshot metadata (#14177)

## 提交信息

- **序号**：2690 / 4088
- **哈希**：2a49d93e53145906f8c6a6cadb91a331bdb1071d
- **短哈希**：2a49d93e5
- **日期**：2025-09-26 13:41:51 -0700
- **作者**：Christian
- **提交说明**：REST: Add missing "added-rows" field to snapshot metadata (#14177)
- **PR/Issue**：#14177

## 总体目的

本提交向 REST Catalog 的 OpenAPI 规范中补充了一个此前遗漏的字段 `added-rows`。该字段属于 `Snapshot`（快照）模型，用于记录快照中已分配行 ID 的行数上限（upper bound）。

Iceberg 的行 ID（row ID）机制是表格式中用于稳定行标识的重要特性。当一个快照写入新数据时，每行都会被分配一个唯一的 `_row_id`。`first-row-id` 字段已经记录了该快照第一个数据文件中第一行所分配的第一个 `_row_id`，但是用来描述该快照所分配行 ID 行数上界的 `added-rows` 字段在 REST OpenAPI 规范中缺失，导致 REST 客户端无法通过规范获取这一信息。

本次修复属于规范对齐（spec alignment）类工作：将 REST OpenAPI 规范与 Iceberg 核心规范中定义的快照元数据保持一致，避免不同实现之间出现规范层面的分歧。

## 如何达成设计目的

修改非常聚焦，仅在两个 OpenAPI 相关文件中向 `Snapshot` 模型新增 `added-rows` 字段：

1. 在 Python 生成的模型 `open-api/rest-catalog-open-api.py` 中，向 `Snapshot` 类新增 `added_rows: Optional[int]` 字段，使用别名 `added-rows`，与已有的 `first-row-id` 字段并列。
2. 在 YAML 规范 `open-api/rest-catalog-open-api.yaml` 中，对应地在 `Snapshot` schema 下新增 `added-rows` 属性，类型为 `int64`，与 `first-row-id` 的定义形式一致。

两处修改相互对应，保持 Python 模型与 YAML 规范的一致性。

## 修改详情

### `open-api/rest-catalog-open-api.py` (+5/-0 lines)

**修改目的**：在 Python Pydantic 模型中补充 `added-rows` 字段定义。

**工作逻辑**：在 `Snapshot` 类中，紧跟在 `first-row-id` 字段后新增 `added_rows` 字段，类型为 `Optional[int]`，默认值 `None`，别名 `added-rows`，描述为 "The upper bound of the number of rows with assigned row IDs"。这与现有 `first-row-id` 字段定义风格完全一致，确保 JSON 序列化/反序列化时使用连字符形式 `added-rows`。

### `open-api/rest-catalog-open-api.yaml` (+4/-0 lines)

**修改目的**：在 OpenAPI YAML 规范中补充 `added-rows` 属性。

**工作逻辑**：在 `Snapshot` 组件 schema 的 `properties` 下，紧跟 `first-row-id` 后新增 `added-rows`，类型 `integer`，格式 `int64`，描述与 Python 模型保持一致。该字段为可选（未列入 `required`），与 Python 端 `Optional` 对应。

## 总结

本提交是一个小而重要的规范修复，将 Iceberg 快照元数据中的 `added-rows` 字段补入 REST Catalog OpenAPI 规范，使 REST 客户端能够正确获取快照中已分配行 ID 的行数上界信息。修改保持 Python 模型与 YAML 规范的一致性，是行 ID 特性在 REST 协议层完整可用的重要一步。
