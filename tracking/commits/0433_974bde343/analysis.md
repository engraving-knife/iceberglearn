# 提交 0433：Open-API: Add table updates for statistics

## 提交信息

- **序号**：0433
- **哈希**：974bde343f324988c5d9095b9f65d7a101d9f7c7
- **短哈希**：974bde343
- **日期**：2024-02-01 01:46:49 -0600
- **作者**：Marc Cenac <547446+mrcnc@users.noreply.github.com>
- **提交说明**：Open-API: Add table updates for statistics (#9564)
- **PR/Issue**：#9564

## 总体目的

Iceberg 的 REST Catalog 规范定义了一组 `TableUpdate` 子类型（如 `assign-uuid`、`upgrade-format-version`、`add-snapshot`、`set-properties` 等），客户端通过 `UpdateTableRequest` 提交一组更新操作来原子地修改表元数据。但在本提交之前，REST Catalog 规范缺失了一个重要能力：**通过 REST 接口写入或删除统计文件（statistics files）**。统计文件是 Iceberg 表元数据中存储列级统计 blob（如 NDV、min/max、值计数等）的外部文件，被查询引擎用来做成本估算和计划优化。原有规范允许 manifest 文件中嵌入统计信息，但对于独立的 statistics 文件（通过 `StatisticsFile` 对象表示，包含统计文件路径、大小、footer 大小，以及一组 `BlobMetadata`），缺乏标准的 REST 更新协议，导致 REST Catalog 实现无法统一地接收客户端提交的统计文件元数据变更。

这个提交向 REST Catalog OpenAPI 规范补齐了两种新的 `TableUpdate` 类型：`SetStatisticsUpdate`（设置/替换某 snapshot 的统计文件）和 `RemoveStatisticsUpdate`（删除某 snapshot 的统计文件），同时新增了 `StatisticsFile` 和 `BlobMetadata` 两个 schema 定义，使 REST Catalog 协议能够完整覆盖表统计文件的生命周期管理。这一改动让第三方引擎（Spark、Trino、Flink 等）可以通过标准 REST 接口向 Iceberg catalog 提交自己计算出的统计文件，而不必依赖引擎特定的内部 API，对多引擎共享同一 catalog 的场景尤为重要。

值得注意的是，这只是协议层（OpenAPI YAML + 对应的 Python Pydantic 模型）的补全，并未触及 Java 服务端或客户端实现——典型的"先规范后实现"演进模式，让规范先稳定下来再驱动各语言 SDK 的对齐。

## 如何达成设计目的

实现路径是纯规范层的扩展。在 YAML 中：在 `TableUpdate` 的 `anyOf` 联合类型里加入两个新成员 `SetStatisticsUpdate`、`RemoveStatisticsUpdate`，并在 `BaseUpdate` 的 `discriminator mapping` 里加上对应的 `set-statistics`/`remove-statistics` action 映射，确保反序列化时能根据 `action` 字段路由到正确的子类。同时定义 `StatisticsFile` 和 `BlobMetadata` 两个新 schema，分别描述统计文件本身的元数据和文件内每个 blob 的元数据。Python 端的 `rest-catalog-open-api.py` 是从 YAML 自动生成（或手写对齐）的 Pydantic 模型，同步新增了对应的 4 个类（`SetStatisticsUpdate`、`RemoveStatisticsUpdate`、`StatisticsFile`、`BlobMetadata`）以及 `TableUpdate` 的 Union 加入新成员。所有字段名用 `alias` 在 snake_case 和 camelCase 之间桥接，保持与 JSON 序列化约定一致。

## 修改详情

### open-api/rest-catalog-open-api.py

**修改目的**：在自动生成的 Pydantic 模型中同步新增两种表更新类型和两个元数据 schema，使 Python SDK 能正确序列化/反序列化统计文件相关的 REST 请求和响应。

**工作逻辑**：
- 新增 `RemoveStatisticsUpdate(BaseUpdate)`：action 字面量 `"remove-statistics"`，必填字段 `snapshot_id`（alias `snapshot-id`）。用于删除指定 snapshot 关联的统计文件。
- 新增 `BlobMetadata(BaseModel)`：包含 `type`（blob 类型字符串）、`snapshot_id`（alias `snapshot-id`）、`sequence_number`（alias `sequence-number`）、`fields`（`List[int]`，统计 blob 覆盖的字段 id 列表）、`properties`（可选的附加属性字典）。描述单个统计 blob 的元信息。
- 新增 `StatisticsFile(BaseModel)`：包含 `snapshot_id`、`statistics_path`（alias `statistics-path`，统计文件路径）、`file_size_in_bytes`（alias `file-size-in-bytes`）、`file_footer_size_in_bytes`（alias `file-footer-size-in-bytes`）、`blob_metadata`（alias `blob-metadata`，`List[BlobMetadata]`）。描述一个完整的统计文件及其内含的所有 blob。
- 新增 `SetStatisticsUpdate(BaseUpdate)`：action 字面量 `"set-statistics"`，必填字段 `snapshot_id` 和 `statistics`（`StatisticsFile`）。用于为指定 snapshot 设置/替换统计文件。
- 修改 `TableUpdate` 的 Union：把 `SetStatisticsUpdate`、`RemoveStatisticsUpdate` 加入 Union 成员列表，使这两种更新能出现在 `UpdateTableRequest` 的 `updates` 数组中。

### open-api/rest-catalog-open-api.yaml

**修改目的**：在 REST Catalog OpenAPI 规范中正式定义统计文件相关的两种更新操作和两个 schema，作为跨语言 SDK 实现的权威契约。

**工作逻辑**：
- 在 `BaseUpdate` 的 `discriminator.mapping` 中加入 `set-statistics: '#/components/schemas/SetStatisticsUpdate'` 和 `remove-statistics: '#/components/schemas/RemoveStatisticsUpdate'`，让 OpenAPI 客户端代码生成器能根据 `action` 字段正确路由反序列化。
- 新增 `SetStatisticsUpdate` schema：`allOf` 引用 `BaseUpdate`，必填 `action`/`snapshot-id`/`statistics`，`action` 枚举为 `["set-statistics"]`，`snapshot-id` 为 int64，`statistics` 引用 `StatisticsFile`。
- 新增 `RemoveStatisticsUpdate` schema：`allOf` 引用 `BaseUpdate`，必填 `action`/`snapshot-id`，`action` 枚举为 `["remove-statistics"]`，`snapshot-id` 为 int64。比 `SetStatisticsUpdate` 简单，因为删除只需指定 snapshot id 即可定位要删除的统计文件。
- 在 `TableUpdate` 的 `anyOf` 中追加 `SetStatisticsUpdate` 和 `RemoveStatisticsUpdate` 两个引用。
- 新增 `StatisticsFile` schema：对象类型，必填 `snapshot-id`/`statistics-path`/`file-size-in-bytes`/`file-footer-size-in-bytes`/`blob-metadata`，分别描述统计文件所属 snapshot、文件路径、文件总大小、footer 大小（用于快速定位 blob 索引）、以及 blob 元数据列表。
- 新增 `BlobMetadata` schema：对象类型，必填 `type`/`snapshot-id`/`sequence-number`/`fields`，可选 `properties`。`type` 标识 blob 的统计类型（如 NDV、min/max 等），`sequence-number` 是 blob 的序列号（用于版本化），`fields` 是该 blob 覆盖的字段 id 列表。

## 小结

这是一个纯协议层的扩展提交，向 Iceberg REST Catalog OpenAPI 规范补齐了统计文件管理的两种更新操作（`set-statistics`、`remove-statistics`）和两个元数据 schema（`StatisticsFile`、`BlobMetadata`）。改动同时落地在 YAML 规范文件和 Python Pydantic 模型两处，保持二者同步。这一扩展让第三方查询引擎能通过标准 REST 接口向 Iceberg catalog 提交和删除统计文件元数据，对多引擎共享 catalog 的场景具有重要意义——引擎各自计算的列级统计可以统一存储到 catalog 元数据中供其它引擎复用。该提交体现的"先规范后实现"模式也是 Iceberg 演进的常见路径：先在 OpenAPI 层稳定接口契约，再驱动各语言 SDK 和服务端实现跟进。后续 Java 服务端和客户端实现对这两种更新的支持将基于此规范展开。
