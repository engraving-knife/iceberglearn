# 提交 3750：Spec: Add v4 content stats representation (#14234)

## 提交信息

- **序号**：3750 / 4088
- **哈希**：f825d09fea36ed8d3889c1718ca230a1033f5686
- **短哈希**：f825d09fe
- **日期**：2026-05-20 08:30:19 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spec: Add v4 content stats representation (#14234)
- **PR/Issue**：#14234

## 总体目的

本提交为 Iceberg 规范的 v4 版本新增了 `content_stats` 内容统计表示方式，将原先基于 map 的字段级指标（metrics）改为基于类型化 struct 的表示，以解决 v3 map 表示方式的固有局限。

在 v3 及更早版本中，数据文件的字段级指标（`value_counts`、`null_value_counts`、`nan_value_counts`、`lower_bounds`、`upper_bounds`）存储在以字段 ID 为键的 map 中。这种表示方式存在以下问题：
1. **类型信息丢失**：map 的值类型必须是统一的（如 `map<int, long>`），无法为不同字段存储不同类型的指标（如 `lower_bounds` 中 int 字段存 int、string 字段存 string）。实际上 v3 的 `lower_bounds`/`upper_bounds` 是 `map<int, binary>`，需要序列化/反序列化，效率低且类型不安全。
2. **缺乏结构化**：无法表达嵌套的统计结构（如 geometry 的 bounding box 需要多个坐标分量）。
3. **schema 演进困难**：map 的 value 类型固定，难以随表 schema 演进。

v4 的 `content_stats` 引入了一个容器 struct，其中每个表字段对应一个类型化的 stats struct，字段类型与表字段类型一致。这种设计：
- 让 lower/upper bounds 直接以字段类型存储（int 字段的 bound 就是 int），无需序列化。
- 支持 geo 类型的 `geo_lower`/`geo_upper` 嵌套 struct 表示 bounding box。
- 类型基于表元数据（如 schema），类似 partition struct，同一 manifest 中所有文件使用相同类型。
- 通过 ID 范围分配机制（`10_000 + 200 * field-id`）为每个字段的 stats struct 分配唯一的 field ID 范围。

## 如何达成设计目的

通过在 `format/spec.md` 中新增大量规范内容实现：
1. 在 `data_file` struct 中新增 field 146 `content_stats` 字段（v4 专有）。
2. 新增"Field-level Metrics and Statistics"章节，说明 v3 map 与 v4 struct 两种表示的等价关系。
3. 重组"Bounds for Geometry and Geography"和"Bounds for Variant"章节，区分 v3（binary 序列化）和 v4（typed struct）表示。
4. 新增"Content Stats"章节，详细定义：
   - 字段 stats struct 的 ID 分配规则（`10_000 + 200 * field-id`，每个字段 200 个 ID）。
   - 保留的元数据字段（`_last_updated_sequence_number`、`_row_id`）的 stats ID 范围。
   - 每个 stats struct 可包含的指标（lower_bound、upper_bound、tight_bounds、value_count、null_value_count、nan_value_count、avg_value_size_in_bytes）及其偏移量、类型、适用字段类型。
   - `geo_lower`/`geo_upper` struct 的定义（x、y、z、m 坐标分量）。
   - 完整的 int 字段、geometry 字段、manifest 中 content_stats 的示例。
   - schema 演进时 writer 如何适配 content_stats（丢弃移除字段的 stats、新增字段设 null、类型提升时 promote bounds 类型）。

## 修改详情

### `format/spec.md` (+151/-6 lines)

**修改目的**：为 v4 新增 content_stats 类型化统计表示规范。

**工作逻辑**：
修改可分为以下几个部分：

1. **data_file struct 新增字段**：在 field 104（`file_size_in_bytes`）之后新增 field 146 `content_stats`，类型为 `content_stats` struct，仅 v4 使用，作为每字段指标的容器。

2. **新增 v4 content_stats 说明**：在 partition struct 说明后新增一段，说明 v4 的 `content_stats` 容器 struct 存储字段级指标，类型基于表元数据（如 schema），类似 partition struct，同一 manifest 中所有文件使用相同类型。

3. **重组字段级指标章节**：将原先散落在各处的指标说明重组为"Field-level Metrics and Statistics"章节，明确：
   - v3 及更早：指标存储在以 field id 为键的 map 中（`value_counts` 等）。
   - v4：指标存储在 `content_stats` 的类型化 struct 中。
   - 两种表示等价：v3 中缺失的 map/键等价于 v4 中的 null/缺失字段。
   - delete 文件的指标必须覆盖所有被删除行或完全省略。

4. **Bounds for Geometry and Geography 章节**：说明 geometry/geography 的 lower/upper bounds 是 bounding box 的点（X、Y、Z 可选、M 可选），geography 的 xmin 可能大于 xmax（跨越国际日期变更线），计算 bounds 时跳过 null/NaN 值。区分 v3（binary 序列化，见附录 D）和 v4（`geo_lower`/`geo_upper` struct）。

5. **Bounds for Variant 章节**：说明 Variant 的 lower/upper bounds 存储按规范化 JSON 路径键的字段边界，上例区分 v3（map）和 v4（variant 类型）。

6. **新增 Content Stats 章节**：详细定义 v4 的 content_stats 结构：
   - **ID 分配**：字段 stats struct 使用 `10_000 + 200 * field-id` 起始的 200 个 ID 范围，首 ID 为 base-id（content_stats 中的字段 ID），struct 内字段通过 offset 分配。例如 field 2 的 stats struct 使用 ID 范围 `[10_400, 10_599]`，base-id 为 10_400，`lower_bound`（offset 1）为 10_401。
   - **保留元数据字段**：`_last_updated_sequence_number`（base-id 9000，范围 [9000, 9199]）、`_row_id`（base-id 9200，范围 [9200, 9399]）。
   - **ID 保留范围**：`[10_000, 200_000_000)` 保留给 content_stats 的字段 stats struct。
   - **stats struct 指标表**：定义 7 个可选指标（lower_bound、upper_bound、tight_bounds、value_count、null_value_count、nan_value_count、avg_value_size_in_bytes）的 offset、类型、适用字段类型和描述。
   - **geo_lower/geo_upper struct 定义**：各 4 个坐标分量（x、y required；z、m optional），类型 double，带范围约束。
   - **示例**：给出 int 字段、geometry 字段、manifest 中 content_stats 的完整 struct 示例。
   - **schema 演进**：writer 通过写新 manifest 适配表变更——丢弃移除字段的旧 stats、新增字段 stats 设 null、类型提升时 promote bounds 类型。推荐方式是用当前 content_stats 类型读取旧 manifest 并应用 schema 演进规则。

## 总结

本提交为 Iceberg 规范 v4 版本新增了 `content_stats` 类型化统计表示，将字段级指标从 v3 的 map 表示改为基于表 schema 的类型化 struct 表示。新设计解决了 map 表示的类型信息丢失、缺乏结构化、schema 演进困难等问题，让 lower/upper bounds 直接以字段类型存储，支持 geo 类型的嵌套 bounding box struct，并通过 ID 范围分配机制为每个字段的 stats struct 分配唯一 ID 空间。规范详细定义了 ID 分配规则、指标字段、geo struct、schema 演进策略，并附完整示例。这是 Iceberg 规范的重要演进，为 v4 的性能优化和类型安全奠定基础。
