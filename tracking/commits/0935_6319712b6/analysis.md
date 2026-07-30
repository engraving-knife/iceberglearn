# 提交 0935：OpenAPI: Fix property names for stats/partition stats (#10662)

## 提交信息

- **序号**：0935 / 4088
- **哈希**：6319712b612b724fedbc5bed41942ac3426ffe48
- **短哈希**：6319712b6
- **日期**：2024-07-15（Mon Jul 15 11:13:47 2024 +0200）
- **作者**：Eduard Tudenhoefner \<etudenhoefner@gmail.com\>
- **提交说明**：OpenAPI: Fix property names for stats/partition stats (#10662)
- **PR/Issue**：#10662

## 总体目的

本提交修复 Iceberg REST Catalog OpenAPI 规范中 `TableMetadata` schema 下统计相关字段的属性名错误。此前在引入分区统计（partition statistics）能力时（参见序号 0495 的提交），OpenAPI 规范把 `TableMetadata` 中承载统计文件列表的字段命名为 `statistics-files` 和 `partition-statistics-files`。然而根据 Iceberg 核心表格式规范（`format/spec.md`），表元数据中这两个字段的正式名称是 `statistics`（参见 spec.md 第 673 行）与 `partition-statistics`，并不带 `-files` 后缀。属性名不一致会导致 REST Catalog 客户端按 OpenAPI 规范生成的模型与实际服务端返回的 JSON 字段名不匹配，进而无法正确反序列化统计文件信息。

此外，本提交还顺带修正了 `BlobMetadata.properties` 字段的类型声明：将其从 `Dict[str, Any]`（任意值类型）收紧为 `Dict[str, str]`（字符串到字符串映射），以与 spec.md 第 702 行中 "Additional properties associated with the statistic... `map<string, string>`" 的定义保持一致。

综上，本次修复的动机是保证 OpenAPI 规范与核心表格式规范在字段命名与类型上的一致性，使依据 OpenAPI 自动生成的客户端代码（包括 Pydantic Python 模型）能正确地序列化/反序列化表元数据中的统计信息。

## 如何达成设计目的

实现方式是在 OpenAPI 规范的两份等价表示中同步修改字段名与类型：

1. **YAML 规范 `open-api/rest-catalog-open-api.yaml`**：在 `TableMetadata` schema 中把属性名 `statistics-files` 改为 `statistics`、`partition-statistics-files` 改为 `partition-statistics`；并在 `BlobMetadata` schema 的 `properties` 字段上增加 `additionalProperties: type: string` 约束，明确值为字符串类型。

2. **Python 模型 `open-api/rest-catalog-open-api.py`**：在 `TableMetadata` Pydantic 模型中把字段 `statistics_files`（alias `statistics-files`）改为 `statistics`（无 alias），把 `partition_statistics_files`（alias `partition-statistics-files`）改为 `partition_statistics`（alias `partition-statistics`）；并把 `BlobMetadata.properties` 的类型注解从 `Optional[Dict[str, Any]]` 改为 `Optional[Dict[str, str]]`。

两份文件保持同步，使规范的自定义 Python 表示与 YAML 定义一致。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：将 `TableMetadata` schema 中统计相关属性名对齐到核心规范，并收紧 `BlobMetadata.properties` 的值类型。

**工作逻辑**：

1. 在 `TableMetadata` schema 中重命名两个属性（注释 `# statistics` 之后）：

```diff
-        statistics-files:
+        statistics:
           type: array
           items:
             $ref: '#/components/schemas/StatisticsFile'
-        partition-statistics-files:
+        partition-statistics:
           type: array
           items:
             $ref: '#/components/schemas/PartitionStatisticsFile'
```

将 `statistics-files` 改为 `statistics`、`partition-statistics-files` 改为 `partition-statistics`，与 `format/spec.md` 中表元数据字段名一致。

2. 在 `BlobMetadata` schema 的 `properties` 字段上增加值类型约束：

```diff
         properties:
           type: object
+          additionalProperties:
+            type: string
```

明确 `properties` 是一个 `map<string, string>`，与 spec.md 中 `map<string, string>` 的定义对齐，避免客户端把值误解析为任意 JSON 类型。

### `open-api/rest-catalog-open-api.py`

**修改目的**：使 Pydantic Python 模型与 YAML 规范的修改保持同步。

**工作逻辑**：

1. `BlobMetadata.properties` 类型收紧：

```diff
-    properties: Optional[Dict[str, Any]] = None
+    properties: Optional[Dict[str, str]] = None
```

把值类型从 `Any` 改为 `str`，对应 YAML 中新增的 `additionalProperties: type: string`。

2. `TableMetadata` 中两个字段重命名并调整 alias：

```diff
-    statistics_files: Optional[List[StatisticsFile]] = Field(
-        None, alias='statistics-files'
-    )
-    partition_statistics_files: Optional[List[PartitionStatisticsFile]] = Field(
-        None, alias='partition-statistics-files'
+    statistics: Optional[List[StatisticsFile]] = None
+    partition_statistics: Optional[List[PartitionStatisticsFile]] = Field(
+        None, alias='partition-statistics'
     )
```

注意 `statistics` 字段不再需要 alias（Python 字段名 `statistics` 与 JSON 字段名 `statistics` 一致），而 `partition_statistics` 仍保留 alias `'partition-statistics'` 以对应 JSON 中的连字符形式。这样 Pydantic 模型在解析 JSON 时会按正确的字段名 `statistics` / `partition-statistics` 取值。

## 小结

- **成效**：修正了 OpenAPI 规范中 `TableMetadata` 的统计字段属性名（`statistics-files` → `statistics`、`partition-statistics-files` → `partition-statistics`），并收紧 `BlobMetadata.properties` 为 `map<string, string>`，使规范与核心表格式 spec 一致，依据 OpenAPI 生成的客户端能正确反序列化统计文件列表。
- **影响范围**：仅 `open-api/rest-catalog-open-api.yaml` 与 `open-api/rest-catalog-open-api.py` 两个文件，共 8 行增删；不影响 Java 核心代码逻辑，仅影响 REST Catalog 规范的契约描述与据此生成的客户端模型。
- **回迁到 1.4.x 的注意事项**：该提交属于规范字段名修正，回迁价值取决于 1.4.x 分支的 OpenAPI 规范是否已包含 `statistics-files` / `partition-statistics-files` 这些错误命名。如果 1.4.x 已引入分区统计的 OpenAPI 定义且字段名错误，则应回迁以保持规范一致性；回迁风险很低，因为仅改规范描述文件，不涉及运行时 Java 代码。需注意 Pydantic 模型的 alias 调整要与 YAML 完全对应，避免客户端反序列化失败。若 1.4.x 尚未引入分区统计能力，则无需回迁。
