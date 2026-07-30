# 提交 0553：为 PreplanTable/PlanTable API 向 REST 规范添加 ContentFile 类型定义

## 提交信息

- **序号**：0553 / 4088
- **哈希**：bb53c3d4e0e27ac6706803c2371793ad2476ae04
- **短哈希**：bb53c3d4e
- **日期**：2024-02-28（Wed Feb 28 08:57:38 2024 -0800）
- **作者**：Drew Gallardo <dru@amazon.com>
- **提交说明**：REST spec: Add ContentFile types to spec for the PreplanTable and PlanTable API (#9717)
- **PR/Issue**：#9717

## 总体目的

Iceberg REST Catalog 规范此前只对 `TableMetadata`、`Snapshot`、`ManifestFile` 等元数据做了完整的 schema 描述，但对 `ContentFile`（即数据文件、位置删除文件、等值删除文件的统一抽象）这一核心类型，规范中却没有任何对应定义。这导致两个问题：

1. **未来计划中的 `PreplanTable` 与 `PlanTable` API 缺乏返回值类型支撑**：这两个端点用于在服务端完成扫描计划，把"哪些文件需要读取"返回给客户端，返回的正是 `ContentFile` 列表。本提交正是为这些端点的引入做类型层的准备。
2. **客户端代码生成器（如 datamodel-code-generator 生成的 `rest-catalog-open-api.py`）无法生成对应的 Python 类型**：在 Iceberg Python 客户端中，对文件列表的处理要么需要手写类型，要么完全无类型，影响可维护性和与 Java 端的一致性。

本提交的目的：将 Iceberg Java 中已有的 `ContentFile`/`DataFile`/`PositionDeleteFile`/`EqualityDeleteFile` 体系完整地建模到 OpenAPI 规范与对应的 Pydantic 模型中，使 REST 规范具备描述文件级清单的能力，并为后续引入 `PreplanTable`/`PlanTable` 端点打好类型基础。

## 如何达成设计目的

整体设计思路是**类型分层 + 判别字段（discriminator）+ 双语言同步建模**：

### 1. 类型分层

设计者把 Iceberg 中"原始类型值 → 基础类型值 → 字段值集合 → 文件 → 具体文件类型"自底向上分成五层：

- **PrimitiveTypeValue 层**：先为 Iceberg 支持的所有 16 种原始类型（boolean、integer、long、float、double、decimal、string、uuid、date、time、timestamp、timestamptz、timestamp_ns、timestamptz_ns、fixed、binary）各自定义独立的 `XxxTypeValue` schema，再用 `oneOf` 聚合为 `PrimitiveTypeValue`。
- **聚合容器层**：
  - `CountMap`：键为列 ID（`IntegerTypeValue`），值为 long 计数（`LongTypeValue`），用于列大小、值计数、null 计数、NaN 计数。
  - `ValueMap`：键为列 ID，值为 `PrimitiveTypeValue`，用于 lower/upper bounds 等异构值集合。
- **FileFormat**：枚举 `avro`/`orc`/`parquet`。
- **ContentFile 基类**：定义所有文件共有的字段（`spec-id`、`content`、`file-path`、`file-format`、`file-size-in-bytes`、`record-count`、`partition`、`key-metadata`、`split-offsets`、`sort-order-id`），并通过 `discriminator` 字段 `content` 区分子类型。
- **DataFile / PositionDeleteFile / EqualityDeleteFile 子类**：通过 `allOf` 继承 `ContentFile`，并把 `content` 字段约束为各自的字面量枚举值（`"data"`、`"position-deletes"`、`"equality-deletes"`）；`DataFile` 额外携带统计字段，`EqualityDeleteFile` 额外携带 `equality-ids`。

### 2. 判别字段（discriminator）

OpenAPI 的 `discriminator` 机制让客户端在反序列化时根据 `content` 字段的值自动选择正确的子类型，避免在客户端手写类型分发逻辑。映射规则：
- `content: "data"` → `DataFile`
- `content: "position-deletes"` → `PositionDeleteFile`
- `content: "equality-deletes"` → `EqualityDeleteFile`

这与 Iceberg Java 端的 `ContentFile.content()` 字段语义完全一致，确保协议层与 Java 模型一致。

### 3. 双语言同步建模

Iceberg 的 REST 规范同时维护两份：
- `rest-catalog-open-api.yaml`：规范权威源（YAML）。
- `rest-catalog-open-api.py`：由 YAML 通过 datamodel-code-generator 自动生成的 Pydantic v1 模型，用于 Python 客户端类型校验。

本提交同时在两份文件中新增对应 schema，保持二者同步。Python 端用 `__root__` + `Union[...]` 表达 `oneOf`，用继承（`class DataFile(ContentFile)`）+ `Literal[...]` 表达 `allOf` + discriminator 枚举约束。

### 4. 序列化约定

设计者在 schema description 中明确编码了 Iceberg 已有的序列化约定：
- decimal 用字符串，负 scale 用科学计数法（`2E+20`）；
- uuid 用 36 字符小写连字符格式，附 RFC-4122 正则；
- date/time/timestamp 用 ISO-8601 字符串；
- fixed 与 binary 用大写十六进制串。

这些都是 Iceberg Java 端 `ContentFile` JSON 序列化器的既有行为，规范化到 OpenAPI 后，跨语言客户端可以无歧义地序列化/反序列化。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`

**修改目的**：在 `components.schemas` 中新增 275 行，定义 `ContentFile` 及其全部依赖类型。

**工作逻辑**：

1. **16 个原始类型值 schema**（`BooleanTypeValue` … `BinaryTypeValue`）：
   - 每个 schema 都是一个标量类型，带 `example` 用于文档展示。
   - `DecimalTypeValue` 用 `type: string` + 描述说明序列化规则（正/零/负 scale 的不同表示）。
   - `UUIDTypeValue` 加 `format: uuid`、正则、`minLength`/`maxLength: 36`，强约束格式。
   - `FixedTypeValue` 与 `BinaryTypeValue` 都是大写十六进制字符串；前者保留固定长度。

2. **`PrimitiveTypeValue`**：用 `oneOf` 列出 16 个原始类型值，表达"任一原始类型"的联合类型。

3. **`CountMap`**：
   - 字段 `keys: array<IntegerTypeValue>` 与 `values: array<LongTypeValue>`，通过下标一一对应组成稀疏 map。
   - 选择"两个并行数组"而非 `additionalProperties` 的 map 形式，是因为 OpenAPI 3.0 对 `additionalProperties` 的类型约束较弱，而 Iceberg Java 端实际序列化时也用了这种 keys/values 数组形式（参见 `MapType`/`CountMap` 的 wire format）。example 给出 `{"keys":[1,2],"values":[100,200]}` 明确这一点。

4. **`ValueMap`**：结构同 `CountMap`，但 `values` 是 `array<PrimitiveTypeValue>`，支持异构原始类型值，用于 lower/upper bounds。

5. **`FileFormat`**：枚举 `avro`/`orc`/`parquet`。

6. **`ContentFile`**（基类）：
   - `discriminator.propertyName: content` + `mapping` 三条，把 `content` 字符串映射到子 schema。
   - `required` 列出关键字段 `spec-id`、`content`、`file-path`、`file-format`、`file-size-in-bytes`、`record-count`。
   - `partition` 是 `array<PrimitiveTypeValue>`，按 partition spec 字段顺序排列，可包含异构类型（如 `[1, "bar"]`）。
   - `key-metadata` 用 `allOf` 包装 `BinaryTypeValue`，使其既能复用 binary 的十六进制约束，又能加自己的描述。
   - `split-offsets` 是 `array<int64>`，对应 Iceberg 中可切分文件的 split offset 列表。
   - `sort-order-id` 是可选整数。

7. **`DataFile`**：
   - `allOf: [$ref: ContentFile]` 表示继承。
   - 把 `content` 限制为 `enum: ["data"]`。
   - 新增 6 个统计字段：`column-sizes`、`value-counts`、`null-value-counts`、`nan-value-counts`（都用 `CountMap`），以及 `lower-bounds`、`upper-bounds`（用 `ValueMap`）。
   - 每个 `allOf` 包装让字段描述可以被加在子类层级，而不修改 `CountMap`/`ValueMap` 自身。

8. **`PositionDeleteFile`**：继承 `ContentFile`，仅把 `content` 限制为 `"position-deletes"`，无额外字段。

9. **`EqualityDeleteFile`**：继承 `ContentFile`，`content` 限制为 `"equality-deletes"`，并增加 `equality-ids: array<integer>` 表示等值删除的字段 ID 列表。

这些 schema 严格反映了 Iceberg Java 中 `ContentFile`、`DataFile`、`PositionDeleteFile`、`EqualityDeleteFile` 接口及其 JSON 序列化形态（参见 `core` 模块中的 `ContentFileParser`、`DataFileParser` 等）。

### `open-api/rest-catalog-open-api.py`

**修改目的**：在 Pydantic 模型中同步生成上述 schema 对应的 Python 类，供 Python 客户端使用。

**工作逻辑**：

1. **导入扩展**：新增 `from datetime import date` 与 `from uuid import UUID`，因为 `DateTypeValue` 用 Python `date`、`UUIDTypeValue` 用 `UUID` 类型。

2. **16 个原始类型值类**：每个类继承 `BaseModel` 并定义 `__root__` 字段（Pydantic v1 表示标量根类型的方式），带上 `example`、`description`、`regex`、`max_length` 等元数据，与 YAML 一一对应。例如：
   ```python
   class UUIDTypeValue(BaseModel):
       __root__: UUID = Field(
           ...,
           regex='^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
           ...
       )
   ```

3. **`CountMap`**：用 `Optional[List[IntegerTypeValue]]` 与 `Optional[List[LongTypeValue]]` 表达两个并行数组。

4. **`PrimitiveTypeValue`**：用 `__root__: Union[...]` 把 16 个原始类型值组合成联合类型，等价于 YAML 中的 `oneOf`。

5. **`FileFormat`**：用 `__root__: Literal['avro', 'orc', 'parquet']`。

6. **`ContentFile`**：直接定义字段，并通过 `Field(..., alias='file-path')` 等使用 kebab-case 别名以匹配 JSON wire format；`key_metadata`、`split_offsets`、`sort_order_id` 为 `Optional`。

7. **`PositionDeleteFile(ContentFile)`**：继承并约束 `content: Literal['position-deletes']`。

8. **`EqualityDeleteFile(ContentFile)`**：继承并约束 `content`，新增 `equality_ids: Optional[List[int]]`。

9. **`DataFile(ContentFile)`**：定义在 `StatisticsFile` 之后（因 Python 中 `ValueMap` 需在 `DataFile` 之前定义，但 `ValueMap` 又需要 `PrimitiveTypeValue`，作者按依赖顺序安排：先原始类型 → `CountMap` → `PrimitiveTypeValue` → `ContentFile`/`PositionDeleteFile`/`EqualityDeleteFile` → `ValueMap` → `DataFile`，因此 `DataFile` 被放在文件后段）。`DataFile` 新增 6 个 `Optional[CountMap]`/`Optional[ValueMap]` 字段，与 YAML 中的 6 个统计字段一一对应。

## 小结

本提交是 REST 规范向"完整描述 Iceberg 文件清单"迈出的关键一步，把 Java 端早已存在但规范层缺失的 `ContentFile` 体系补齐，且严格保留了 Iceberg 既有的 JSON 序列化约定（decimal 字符串、uuid 正则、十六进制 binary 等）。它本身不引入任何 API 端点，是为后续 `PreplanTable`/`PlanTable` 端点（以及任何需要返回文件列表的端点）做的类型基础设施。

**影响范围**：
- 仅影响 `open-api/` 目录，不动 Java/Spark/Flink 代码。
- 对 REST 客户端代码生成器（OpenAPI Generator、datamodel-code-generator 等）生成的代码会有增量，但不会破坏现有 schema 的兼容性——所有新增 schema 都是独立的新名字，不修改任何已有 schema。
- 对运行时无任何影响。

**回迁到 1.4.x 的注意事项**：
- 回迁非常安全，本提交纯增 schema，不修改任何已有定义。
- 由于 1.4.x 通常不需要 `PreplanTable`/`PlanTable` 端点（这两个是后续 main 分支才完整引入的），即便只回迁本提交的 schema、不回迁后续端点，也不会引起冲突；schema 可以"先就位"等待未来端点。
- 注意 Pydantic 模型使用 v1 风格（`__root__`、`Extra`、`regex=` 等），若 1.4.x 已升级到 Pydantic v2，需相应改写（`__root__` → `RootModel`、`regex` → `pattern`、`Extra` → `model_config`）。这是回迁时唯一需要核对的运行环境差异。
- YAML 中 `oneOf` 与 `discriminator` 组合在不同 OpenAPI 工具链中的解析行为略有差异；若 1.4.x 使用的生成器对 discriminator 支持有限，可能需要在客户端手动分发类型，但 spec 本身无需调整。
