# 提交 1662：OpenAPI: add initial/write defaults to schema (#12094)

## 提交信息

- **序号**：1662 / 4088
- **哈希**：bdcd9c3379379a3acf06b059ae403e2107a6c242
- **短哈希**：bdcd9c337
- **日期**：2025-01-31（Fri Jan 31 09:54:54 2025 -0800）
- **作者**：Daniel Weeks <dweeks@apache.org>
- **提交说明**：OpenAPI: add initial/write defaults to schema (#12094)
- **PR/Issue**：#12094

## 总体目的

Iceberg 的 schema 字段（`NestedField`）支持两种默认值：

- `initial-default`：表创建时该字段的初始默认值；
- `write-default`：写入时未提供该字段值时使用的默认值。

这两个默认值在 Iceberg 的 Java API、表元数据 JSON（`metadata.json`）与规范（`format/spec.md`）中早已定义并支持，但 REST Catalog 的 OpenAPI 规范（`rest-catalog-open-api.yaml`）中的 `StructField` schema 却**遗漏了这两个字段**——只定义了 `name`/`type`/`required`/`doc`，没有 `initial-default`/`write-default`。

这导致两个问题：

1. **规范不完整**：通过 REST Catalog 创建表（`CreateTableRequest`）或加载表（`LoadTableResponse`）时，schema 中字段的默认值无法在 OpenAPI 规范中表达，外部 REST 客户端（基于规范生成的代码）无法序列化/反序列化这两个字段，造成默认值丢失；
2. **测试覆盖不足**：现有测试没有验证 V3 表元数据（含默认值）的 REST 往返序列化。

本提交在 OpenAPI 规范的 `StructField` 中补齐 `initial-default` 与 `write-default` 两个可选字段（引用 `PrimitiveTypeValue`，与字段类型对应的原始值），同步更新生成的 Python 模型，并补充测试覆盖带默认值的 schema 序列化与 V3 表元数据往返。

## 如何达成设计目的

1. **OpenAPI 规范扩展**：在 `rest-catalog-open-api.yaml` 的 `StructField` schema 的 properties 中新增 `initial-default` 与 `write-default`，均 `$ref` 到既有的 `PrimitiveTypeValue` schema（一个 oneOf 联合类型，覆盖 boolean/integer/long/float/double/decimal/string/uuid/date/time/timestamp/fixed/binary 等所有原始类型值），这样默认值类型与字段类型一一对应；
2. **Python 模型同步**：`rest-catalog-open-api.py` 的 `StructField` 类新增 `initial_default` 与 `write_default` 两个 `Optional[PrimitiveTypeValue]` 字段（alias 分别为 `initial-default`/`write-default`）；
3. **测试补强**：
   - `TestCreateTableRequest`：把 SAMPLE_SCHEMA 的 `id` 字段改为带 `withWriteDefault(1)`，验证创建表请求的 JSON 序列化包含 `"write-default":1`；
   - `TestLoadTableResponse`：新增 `testRoundTripSerdeWithV3TableMetadata`，用新增的 `TableMetadataV3ValidMinimal.json`（含 `initial-default` 与 `write-default`）做 LoadTableResponse 往返序列化测试；
   - 新增测试资源 `TableMetadataV3ValidMinimal.json`：一个最小化的 V3（实际 format-version 2）表元数据，其中字段 `x` 带 `initial-default: 1` 与 `write-default: 1`。

## 修改详情

### `open-api/rest-catalog-open-api.yaml`（修改，+4）

**修改目的**：在 `StructField` schema 中补齐默认值字段。

**工作逻辑**：在 `StructField` 的 `properties` 中，`doc`（string）之后新增：

```yaml
initial-default:
  $ref: "#/components/schemas/PrimitiveTypeValue"
write-default:
  $ref: "#/components/schemas/PrimitiveTypeValue"
```

两个字段均为 optional（未列入 `required`）。`PrimitiveTypeValue` 是已有的 oneOf 联合类型，覆盖所有 Iceberg 原始类型对应的值类型（`BooleanTypeValue`/`IntegerTypeValue`/.../`BinaryTypeValue`），保证默认值与字段类型匹配。

### `open-api/rest-catalog-open-api.py`（修改，+2）

**修改目的**：同步生成的 Python 模型。

**工作逻辑**：`StructField` 类新增两个字段：

```python
initial_default: Optional[PrimitiveTypeValue] = Field(None, alias='initial-default')
write_default: Optional[PrimitiveTypeValue] = Field(None, alias='write-default')
```

均默认 `None`，alias 对应 JSON 中的 kebab-case 键名。

### `core/src/test/resources/TableMetadataV3ValidMinimal.json`（新增，73 行）

**修改目的**：提供带默认值的最小 V3 表元数据测试夹具。

**工作逻辑**：一个 `format-version: 2` 的表元数据 JSON，含 3 个字段（`x`/`y`/`z`，均为 long），其中 `x` 字段带 `"initial-default": 1` 与 `"write-default": 1`；含一个 identity 分区规格（按 `x`）与一个 sort order（`y` asc + `z` bucket[4] desc）。文件末尾无换行（`\ No newline at end of file`）。命名为 V3 但 format-version 写 2，用于测试默认值字段的解析。

### `core/src/test/java/org/apache/iceberg/rest/requests/TestCreateTableRequest.java`（修改，+2 / -2）

**修改目的**：验证 CreateTable 请求序列化包含 write-default。

**工作逻辑**：

- `SAMPLE_SCHEMA` 中 `id` 字段从 `required(1, "id", Types.IntegerType.get())` 改为用 builder 链式构造 `required("id").withId(1).ofType(Types.IntegerType.get()).withWriteDefault(1).build()`，使该字段带 write 默认值 1；`data` 字段也改用 builder 风格（不带默认值）；
- `testRoundTripSerDe` 的期望 JSON 中 `id` 字段对象新增 `"write-default":1`：`{"id":1,"name":"id","required":true,"type":"int","write-default":1}`。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestLoadTableResponse.java`（修改，+17 / -2）

**修改目的**：验证带默认值的 V3 表元数据 LoadTableResponse 往返。

**工作逻辑**：

- 顺手清理一处注释换行（"missing fields / are filled in" 合并为一行）；
- 新增 `@Test testRoundTripSerdeWithV3TableMetadata`：读取 `TableMetadataV3ValidMinimal.json`，用 `TableMetadataParser.fromJson` 解析为 `TableMetadata`，再 `TableMetadataParser.toJson` 转回 JSON（填充默认值），构造 `LoadTableResponse`（含 metadata-location、metadata、config），用 `assertRoundTripSerializesEquallyFrom(json, resp)` 验证序列化往返等价。这确保 REST 协议能正确传输含 `initial-default`/`write-default` 的 schema。

## 小结

- **成效**：补齐 OpenAPI 规范中 `StructField` 缺失的 `initial-default`/`write-default` 字段，使 REST Catalog 协议能完整表达字段默认值，外部客户端（含基于规范生成代码的客户端）可正确序列化默认值；同步补强测试覆盖。
- **影响范围**：OpenAPI 规范新增两个 optional 字段，向后兼容（旧客户端忽略未识别字段）。Python 模型同步。Java 测试增强，不改生产代码（Java 侧 `NestedField` 早已支持默认值，本提交只是规范与测试补齐）。
- **回迁到 1.4.x 的注意事项**：规范与测试补齐，回迁安全。需确认 1.4.x 的 `TableMetadataParser` 与 `SchemaParser` 已支持 `initial-default`/`write-default`（这些是 Iceberg 早期就有的特性，通常已支持）。测试夹具 `TableMetadataV3ValidMinimal.json` 中 `format-version: 2` 但命名为 V3，回迁时注意命名与实际格式版本的理解。`PrimitiveTypeValue` schema 需与 1.4.x 的 OpenAPI 规范版本一致。生成的 Python 模型变更需与 1.4.x 是否维护 Python 客户端协调。
