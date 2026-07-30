# 提交 1659 5da8f5faf 分析

## 提交信息
- 哈希：5da8f5faf7609c7bbd01ef662ca9b61750b2e4b4
- 日期：2025-01-29 16:49:38 -0600
- 作者：Aihua Xu
- 消息：Spec: Add variant type (#10831)

## 总体目的

本提交是 Iceberg 规范（spec）层面引入 Variant（半结构化）类型的关键提交。Variant 类型用于存储半结构化数据（类似 JSON），其结构和数据类型在不同行之间无需一致。这是 Iceberg v3 规范引入的新类型之一，与 Snowflake、Databricks 等系统中的 Variant 概念一致。

Variant 类型的设计动机：
- 现代数据湖中越来越多的半结构化数据（如日志、事件、配置）需要灵活存储，传统做法是用 JSON 字符串列，但缺乏类型信息和高效访问能力。
- Variant 提供了一种自描述的二进制格式，由 metadata（字段名字典）和 value（实际值）两部分组成，支持嵌套数组、对象以及丰富的原始类型（date、timestamp、timestamptz、binary、decimal 等），比 JSON 表达能力更强。
- Variant 的二进制编码由 Parquet 项目的 VariantEncoding.md 和 VariantShredding.md 定义，Iceberg 复用该标准以保证跨引擎互操作性。
- 支持 Shredding（剥离）优化：将热门字段从 Variant 值中"剥离"出来单独存储为独立列，兼顾灵活性与查询性能。

本提交仅修改规范文档 `format/spec.md`，将 Variant 类型正式纳入 Iceberg 类型系统，定义其在分区、文件格式映射、JSON 序列化等方面的规范行为，为后续在各引擎和文件格式中实现 Variant 读写奠定规范基础。

## 如何达成设计目的

规范文档需要在一个类型引入时同时明确以下方面：
1. 类型定义与语义：Variant 是什么，能存什么。
2. 与现有类型的关系：与 list/struct 的异同。
3. 分区支持：是否可作为分区字段、是否可哈希。
4. 文件格式映射：在 Avro、Parquet、ORC 中如何表示。
5. JSON 序列化：在元数据 JSON 中如何表示 variant 类型。
6. 单值序列化：是否支持单值二进制序列化。
7. 规范版本：在哪个版本引入，前后兼容性如何。

本提交通过在 spec.md 的多个章节补充 Variant 相关条目来完成上述定义。

### 修改详情

#### format/spec.md - 新增 "Semi-structured Types" 章节
在 list 和 map 类型定义之后、Primitive Types 之前，新增"半结构化类型"小节，定义 Variant：
- Variant 存储半结构化数据，结构和类型在行间无需一致。
- 编码定义在 Parquet 项目的 VariantEncoding.md，目前支持 V1。
- Variant 在 Iceberg v3 中加入。
- Variant 类似 JSON 但原语类型更丰富（含 date、timestamp、timestamptz、binary、decimal）。
- Variant 可包含嵌套类型：array（有序 variant 值集合）、object（字符串键到 variant 值的集合）。
- 与 Iceberg 现有类型的区别：
  - Variant array 类似 list，但元素可为任意 variant 值而非固定元素类型。
  - Variant object 类似 struct，但字段可变（按名标识）、字段值可为任意 variant 值而非固定字段类型。

#### format/spec.md - 分区 transform 表
将 `identity` transform 的 "Source types" 从 "Any" 修改为 "Any except for `variant`"，明确 Variant 不能作为 identity 分区字段。其他 transform（bucket、truncate、year 等）的源类型列表本就不包含 variant，无需修改。

#### format/spec.md - Avro 类型映射表
新增行：
- Iceberg `variant` 映射为 Avro `record`，含 `metadata` 和 `value` 两个字段。
- `metadata` 和 `value` 字段不得分配 field ID，通过字段名访问。
- 备注：Avro 不支持 Shredding。

#### format/spec.md - Parquet 类型映射表
新增行：
- Iceberg `variant` 映射为 Parquet `group`，含 `metadata` 和 `value` 字段，同样不分配 field ID，按名访问。
- 逻辑类型注解为 `VARIANT`。
- 备注链接到 Parquet 项目的 Variant encoding 和 Variant shredding encoding 文档。

#### format/spec.md - ORC 类型映射表
新增行：
- Iceberg `variant` 映射为 ORC `struct`，含 `metadata` 和 `value` 字段，不分配 field ID。
- ORC 列属性 `iceberg.struct-type`=`VARIANT`。
- 备注：ORC 不支持 Shredding。

#### format/spec.md - 哈希分区说明
在 32 位哈希值表后新增说明："A 32-bit hash is not defined for `variant` because there are multiple representations for equivalent values." 即 Variant 不定义 32 位哈希，因为等价值可能有多种表示（导致哈希不一致）。

#### format/spec.md - 类型 JSON 序列化表
新增行：
- Iceberg `variant` 序列化为 JSON 字符串 `"variant"`。
- 示例：`"variant"`。
- 这意味着在表元数据 JSON 中，variant 类型的字段其 type 字段值就是字符串 "variant"，无需额外的嵌套对象（与 struct/list/map 不同）。

#### format/spec.md - 单值二进制序列化表
新增行：
- `variant` 的单值二进制序列化为 "Not supported"（不支持）。
- 与 struct/list/map 一致，这些复杂类型都不支持单值二进制序列化。

#### format/spec.md - JSON 单值序列化表
注意：本提交未在 JSON single-value serialization 表中新增 variant 行（可能在后续提交补充）。

#### format/spec.md - Appendix E: Format version changes
将 v3 新增类型列表从 `Types unknown, timestamp_ns, and timestamptz_ns are added in v3.` 修改为 `Types variant, unknown, timestamp_ns, and timestamptz_ns are added in v3.`，正式将 variant 纳入 v3 规范版本。

## 小结

本次规范提交成效：
- 正式定义了 Iceberg Variant 类型，使其成为 v3 规范的一等类型。
- 明确了 Variant 在各文件格式（Avro/Parquet/ORC）中的映射规则，保证跨引擎互操作性。
- 明确了 Variant 的限制：不能作为 identity 分区字段、不定义 32 位哈希、不支持单值二进制序列化、Avro/ORC 不支持 Shredding。
- 为 Variant 的 metadata+value 二字段表示和按名访问（不分配 field ID）确立了统一规范。

影响范围：仅规范文档 `format/spec.md`，不影响代码实现。但这是后续所有 Variant 实现（如提交 1653 的 variants 包、Parquet/Spark 读取器等）的规范依据。

回迁到 1.4.x 注意事项：
- Variant 是 v3 规范的特性，1.4.x 基于 v2/v3 规范。若 1.4.x 已支持 v3 表格式，则本规范文档变更应回迁以保持规范一致性。
- 若 1.4.x 的 spec.md 已包含 v3 相关章节（如 timestamp_ns、unknown 类型），则回迁本提交只需在相应位置补充 variant 条目。
- 由于这是纯规范文档变更，回迁无代码冲突风险，但需注意与 1.4.x 已有的 spec.md 内容合并（各章节位置可能不同）。
- 注意：本提交是 Variant 规范的第一步，后续还有 Variant 的 Java API、读取器实现等提交（如 1653 的 variants 包可见性更新），若要完整支持 Variant 需一并回迁相关提交。
