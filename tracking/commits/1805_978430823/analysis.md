# 提交 1805：Spec: Fix geo type example (#12421)

## 提交信息

- **序号**：1805 / 4088
- **哈希**：978430823ca0cd823544e668e12f84ce91551cbd
- **短哈希**：978430823
- **日期**：2025-03-01 13:40:51 -0800
- **作者**：Jia Yu
- **提交说明**：Spec: Fix geo type example (#12421)
- **PR/Issue**：#12421

## 总体目的

Iceberg 规范文档 `format/spec.md` 在类型序列化表格中展示了 `geometry(C)` 和 `geography(C, A)` 两种地理类型的 JSON 序列化示例。此前示例中使用的 CRS（坐标参考系统）值为 `"OGC:CRS84"`，但 Iceberg 规范实际采用的 CRS 表示方式应为 `"srid:4326"`（即以 SRID 形式引用 EPSG:4326 坐标系）。

示例值与规范约定不一致会误导实现者和用户，使其在序列化几何类型时使用错误的 CRS 格式。本提交将示例中的 `"OGC:CRS84"` 修正为 `"srid:4326"`，使示例与规范实际约定一致。

## 如何达成设计目的

通过修改 `format/spec.md` 中类型序列化表格里 `geometry(C)` 和 `geography(C, A)` 两行的示例列，将 `"crs": "OGC:CRS84"` 改为 `"crs": "srid:4326"`。改动仅限示例值，不涉及序列化格式说明本身。

## 修改详情

### `format/spec.md`（修改, +2 -2 lines）

**修改目的**：修正几何类型示例中的 CRS 值。

**工作逻辑**：在类型序列化表格中：
- `geometry(C)` 行的示例从 `{"type": "geometry", "crs": "OGC:CRS84"}` 改为 `{"type": "geometry", "crs": "srid:4326"}`。
- `geography(C, A)` 行的示例从 `{"type": "geography", "crs": "OGC:CRS84", "algorithm": "spherical"}` 改为 `{"type": "geography", "crs": "srid:4326", "algorithm": "spherical"}`。

`srid:4326` 表示使用 SRID（Spatial Reference System Identifier）格式引用 EPSG:4326（WGS 84 地理坐标系），这是 Iceberg 规范约定的 CRS 表示方式。

## 小结

- **成效**：修正了规范文档中几何类型示例的 CRS 值，使示例与规范约定一致，避免误导。
- **影响范围**：仅影响规范文档 `format/spec.md` 的两个示例值，不涉及代码改动。
- **回迁到 1.4.x 的注意事项**：纯文档改动，无风险，无前置依赖。但需确认 1.4.x 分支的规范文档中是否已包含 geometry/geography 类型定义；若 1.4.x 尚未引入地理类型，则无需回迁。若已引入且存在相同错误，则可直接回迁。
