# 提交 0495：OpenAPI: Spec updates for statistics (#9690)

## 提交信息

| 字段 | 内容 |
|------|------|
| 序号 | 0495 |
| 完整哈希 | 5f577f1b9902ffe6181897a439686a31fc81b89a |
| 短哈希 | 5f577f1b9 |
| 日期 | 2024-02-09 02:33:12 -0600 |
| 作者 | Marc Cenac <547446+mrcnc@users.noreply.github.com> |
| 说明 | OpenAPI: Spec updates for statistics (#9690) |
| PR | #9690 |

文件统计：2 个文件，78 行新增 / 0 行删除。
- open-api/rest-catalog-open-api.py：24 行新增
- open-api/rest-catalog-open-api.yaml：54 行新增

## 总体目的

本提交为 Iceberg REST Catalog 的 OpenAPI 规范引入"分区统计（partition statistics）"这一新的统计类型，使规范能够表达针对单个快照（snapshot）的分区级统计文件，并通过提交更新（commit update）操作对其进行设置与移除。

在 Iceberg 中，统计信息（statistics）用于加速查询规划。此前规范已支持表级统计：`StatisticsFile` 描述一个统计文件（关联快照、统计文件路径、文件大小等），并通过 `SetStatisticsUpdate` / `RemoveStatisticsUpdate` 两种 `TableUpdate` 操作来设置或移除表级统计。随着 Iceberg 演进，出现了"分区统计"的概念——针对每个分区而非整表的统计文件，用于更细粒度的查询优化。为使 REST Catalog 规范覆盖这一能力，本次提交在规范中新增 `PartitionStatisticsFile` schema，并新增 `SetPartitionStatisticsUpdate` 与 `RemovePartitionStatisticsUpdate` 两种更新操作，同时把 `statistics-files` 与 `partition-statistics-files` 两个数组字段加入 `TableMetadata`，使加载表元数据时能返回这两类统计文件列表。

这一改动属于规范的增量扩展，不破坏既有定义，而是与已有统计机制保持平行的对称结构：分区统计的 schema 与操作完全镜像表级统计的设计（同样的字段、同样的 `allOf` 继承 `BaseUpdate`、同样的 `action` 枚举模式），便于实现者以一致的方式扩展。

## 如何达成设计目的

实现路径分两步且 YAML 与 Python 模型同步修改：第一步在 YAML 规范的 `components.schemas` 中新增 `PartitionStatisticsFile`、`SetPartitionStatisticsUpdate`、`RemovePartitionStatisticsUpdate` 三个 schema，并在 `TableMetadata` 中加入 `statistics-files` 与 `partition-statistics-files` 两个字段，同时在 `TableUpdate` 的 discriminator 映射中加入 `set-partition-statistics` 与 `remove-partition-statistics` 两个映射；第二步在由规范生成的 Pydantic 模型 `rest-catalog-open-api.py` 中新增对应的 `PartitionStatisticsFile`、`SetPartitionStatisticsUpdate`、`RemovePartitionStatisticsUpdate` 三个模型类，并在 `TableMetadata` 模型中加入 `statistics_files` 与 `partition_statistics_files` 两个可选字段。两份文件保持同步，使规范的自定义 Python 表示与 YAML 定义一致。

## 修改详情

### open-api/rest-catalog-open-api.yaml

**修改目的**：在 OpenAPI 规范中定义分区统计的 schema、更新操作及元数据字段。

**工作逻辑**：改动分布在四处：

1. 在 `TableMetadata` schema 中新增 `statistics-files` 与 `partition-statistics-files` 两个数组字段（位于 `snapshot-log` / `metadata-log` 之后）：
```yaml
        # statistics
        statistics-files:
          type: array
          items:
            $ref: '#/components/schemas/StatisticsFile'
        partition-statistics-files:
          type: array
          items:
            $ref: '#/components/schemas/PartitionStatisticsFile'
```
注意此处不仅新增了 `partition-statistics-files`，还为已有的 `statistics-files` 补上了在 `TableMetadata` 中的字段声明（此前表级统计数组可能未在此处显式列出），使 `TableMetadata` 同时承载两类统计文件列表。`items` 分别引用 `StatisticsFile`（已有）与 `PartitionStatisticsFile`（本次新增）。

2. 在 `TableUpdate` 的 discriminator `mapping` 中新增两个映射：
```yaml
          set-partition-statistics: '#/components/schemas/SetPartitionStatisticsUpdate'
          remove-partition-statistics: '#/components/schemas/RemovePartitionStatisticsUpdate'
```
这使得 `TableUpdate` 的 `anyOf` 能根据 `action` 字段值 `set-partition-statistics` / `remove-partition-statistics` 正确解析到对应 schema，与已有的 `set-statistics` / `remove-statistics` 映射并列。

3. 新增 `SetPartitionStatisticsUpdate` schema（镜像 `SetStatisticsUpdate`）：
```yaml
    SetPartitionStatisticsUpdate:
      allOf:
        - $ref: '#/components/schemas/BaseUpdate'
      required:
        - action
        - partition-statistics
      properties:
        action:
          type: string
          enum: [ "set-partition-statistics" ]
        partition-statistics:
          $ref: '#/components/schemas/PartitionStatisticsFile'
```
通过 `allOf` 继承 `BaseUpdate`，要求 `action` 固定为 `set-partition-statistics`，并要求一个 `partition-statistics` 字段引用 `PartitionStatisticsFile`，用于在提交时设置某快照的分区统计文件。

4. 新增 `RemovePartitionStatisticsUpdate` schema（镜像 `RemoveStatisticsUpdate`）：
```yaml
    RemovePartitionStatisticsUpdate:
      allOf:
        - $ref: '#/components/schemas/BaseUpdate'
      required:
        - action
        - snapshot-id
      properties:
        action:
          type: string
          enum: [ "remove-partition-statistics" ]
        snapshot-id:
          type: integer
          format: int64
```
同样继承 `BaseUpdate`，`action` 固定为 `remove-partition-statistics`，通过 `snapshot-id` 指定要移除哪个快照的分区统计。

5. 新增 `PartitionStatisticsFile` schema：
```yaml
    PartitionStatisticsFile:
      type: object
      required:
        - snapshot-id
        - statistics-path
        - file-size-in-bytes
      properties:
        snapshot-id:
          type: integer
          format: int64
        statistics-path:
          type: string
        file-size-in-bytes:
          type: integer
          format: int64
```
该 schema 描述一个分区统计文件：`snapshot-id` 标识关联快照，`statistics-path` 是统计文件的存储路径，`file-size-in-bytes` 是文件字节大小。其字段结构与已有的 `StatisticsFile` 相似（区别在于 `StatisticsFile` 还包含 `file-footer-size-in-bytes`、`blob-metadata` 等表级统计专属字段，而分区统计更精简）。

### open-api/rest-catalog-open-api.py

**修改目的**：在规范对应的 Pydantic 模型中同步新增分区统计相关类与字段，保持 Python 表示与 YAML 一致。

**工作逻辑**：改动分布在三处，均与 YAML 新增内容对应：

1. 新增 `RemovePartitionStatisticsUpdate`（紧随已有的 `RemoveStatisticsUpdate` 之后）：
```python
class RemovePartitionStatisticsUpdate(BaseUpdate):
    action: Literal['remove-partition-statistics']
    snapshot_id: int = Field(..., alias='snapshot-id')
```
继承 `BaseUpdate`，`action` 为字面量 `'remove-partition-statistics'`，`snapshot_id` 通过别名 `'snapshot-id'` 对应 YAML 中的 `snapshot-id`。

2. 新增 `PartitionStatisticsFile`（位于 `BlobMetadata` 之后）：
```python
class PartitionStatisticsFile(BaseModel):
    snapshot_id: int = Field(..., alias='snapshot-id')
    statistics_path: str = Field(..., alias='statistics-path')
    file_size_in_bytes: int = Field(..., alias='file-size-in-bytes')
```
三个必填字段分别对应 YAML schema 中的 `snapshot-id`、`statistics-path`、`file-size-in-bytes`，通过 `alias` 保持连字符命名的 JSON 字段与 Python 下划线命名的属性名之间的映射。

3. 新增 `SetPartitionStatisticsUpdate`（位于 `TransformTerm` 之后）：
```python
class SetPartitionStatisticsUpdate(BaseUpdate):
    action: Literal['set-partition-statistics']
    partition_statistics: PartitionStatisticsFile = Field(
        ..., alias='partition-statistics'
    )
```
`action` 为字面量 `'set-partition-statistics'`，`partition_statistics` 字段类型为 `PartitionStatisticsFile`，通过别名 `'partition-statistics'` 对应 YAML。

4. 在 `TableMetadata` 模型中新增两个可选字段（位于 `snapshot_log` / `metadata_log` 之后）：
```python
    statistics_files: Optional[List[StatisticsFile]] = Field(
        None, alias='statistics-files'
    )
    partition_statistics_files: Optional[List[PartitionStatisticsFile]] = Field(
        None, alias='partition-statistics-files'
    )
```
与 YAML 中 `TableMetadata` 新增的数组字段对应，`statistics_files` 引用已有的 `StatisticsFile`，`partition_statistics_files` 引用本次新增的 `PartitionStatisticsFile`，两者均为可选（`Optional`，默认 `None`），保证向后兼容。

## 小结

本次提交是 Iceberg 1.4.x 周期内对 REST Catalog OpenAPI 规范的扩展性改动，新增"分区统计（partition statistics）"能力：定义 `PartitionStatisticsFile` schema 描述分区统计文件，并新增 `SetPartitionStatisticsUpdate` / `RemovePartitionStatisticsUpdate` 两种提交更新操作以设置和移除分区统计，同时把 `statistics-files` 与 `partition-statistics-files` 两个数组字段加入 `TableMetadata` 使加载表时能返回统计文件列表。所有新增结构均镜像已有表级统计（`StatisticsFile` / `SetStatisticsUpdate` / `RemoveStatisticsUpdate`）的设计模式，YAML 规范与生成的 Python Pydantic 模型同步修改，保持一致。该改动为纯增量、不破坏既有定义，扩展了规范对查询优化所需统计信息的覆盖范围，回溯风险低。
