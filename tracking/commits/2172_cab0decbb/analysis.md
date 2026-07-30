# 提交 2172：REST Spec: Add row lineage fields (#13010)

## 提交信息

- **序号**：2172 / 4088
- **哈希**：cab0decbb0e32bf314039e30807eb033c50665d5
- **短哈希**：cab0decbb
- **日期**：2025-05-27 14:50:22 -0700
- **作者**：Prashant Singh
- **提交说明**：REST Spec: Add row lineage fields (#13010)
- **PR/Issue**：#13010

## 总体目的

此提交为 Iceberg REST Catalog OpenAPI 规范添加行级血缘（row lineage）相关字段。行级血缘是 Iceberg 中追踪数据行来源和变化的能力，通过为每行数据分配唯一的 row ID 来实现。此提交在 REST API 规范中正式定义了 `first-row-id` 和 `next-row-id` 字段，分别在 Snapshot、DataFile 和 TableMetadata 三个层面描述行 ID 的分配情况。这是 Iceberg 规范层面对行级血缘功能的支持，为后续引擎实现行级追踪能力奠定基础。

## 如何达成设计目的

- 在 `DataFile.java` API 接口中更新 `first_row_id` 字段的描述，使其更准确
- 在 OpenAPI YAML 规范中为 Snapshot 添加 `first-row-id` 字段
- 在 OpenAPI YAML 规范中为 DataFile 添加 `first-row-id` 字段
- 在 OpenAPI YAML 规范中为 TableMetadata 添加 `next-row-id` 字段
- 同步更新 Python 模型定义文件（rest-catalog-open-api.py）

## 修改详情

### `api/src/main/java/org/apache/iceberg/DataFile.java` (修改, +5/-1 lines)

**修改目的**：更新 `first_row_id` 字段的描述说明。

**工作逻辑**：将 `FIRST_ROW_ID` 字段的描述从 "Starting row ID to assign to new rows" 改为 "The first row ID assigned to the first row in the data file"，使描述更准确地反映字段语义——它是数据文件中第一行被分配的 row ID，而不是将要分配的。

### `open-api/rest-catalog-open-api.py` (修改, +15/-0 lines)

**修改目的**：在 Python 模型定义中添加行血缘相关字段。

**工作逻辑**：
- 在 `Snapshot` 模型中添加 `first_row_id` 字段，描述为 "The first _row_id assigned to the first row in the first data file in the first manifest"
- 在 `DataFile` 模型中添加 `first_row_id` 字段，描述为 "The first row ID assigned to the first row in the data file"
- 在 `TableMetadata` 模型中添加 `next_row_id` 字段，描述为 "A long higher than all assigned row IDs; the next snapshot's first-row-id"

### `open-api/rest-catalog-open-api.yaml` (修改, +12/-0 lines)

**修改目的**：在 OpenAPI YAML 规范中正式定义行血缘字段。

**工作逻辑**：
- 在 `Snapshot` schema 中添加 `first-row-id`（integer/int64），表示第一个 manifest 中第一个数据文件的第一行的 _row_id
- 在 `TableMetadata` schema 中添加 `next-row-id`（integer/int64），表示高于所有已分配 row ID 的值，作为下一个快照的 first-row-id
- 在 `DataFile` schema 中添加 `first-row-id`（integer/int64），表示数据文件中第一行的 row ID

## 总结

此提交为 Iceberg REST Catalog OpenAPI 规范添加了行级血缘（row lineage）的三个关键字段：Snapshot 级别的 `first-row-id`、DataFile 级别的 `first-row-id` 和 TableMetadata 级别的 `next-row-id`。这些字段共同描述了行 ID 在快照和数据文件层面的分配机制，为 Iceberg 的行级数据追踪能力提供了规范层面的定义。
