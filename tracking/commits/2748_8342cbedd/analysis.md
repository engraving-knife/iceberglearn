# 提交 2748：Spec: Clarify restrictions for geometry types in V3

## 提交信息

- **序号**：2748 / 4088
- **哈希**：8342cbedd576142f55cbdfac33c4d339471b7832
- **短哈希**：8342cbedd
- **日期**：2025-10-14 11:27:42 -0700
- **作者**：Szehon Ho
- **提交说明**：Spec: Clarify restrictions for geometry types in V3
- **PR/Issue**：#14250

## 总体目的

这是 Iceberg 规范（spec）的文档澄清提交，针对 V3 规范中 `geometry` 和 `geography` 类型的定义进行两处重要澄清。Iceberg V3 引入了对地理空间数据类型（geometry 和 geography）的支持，这些类型涉及坐标参考系统（CRS）、边界框（bounding box）等概念。

此次澄清解决两个规范模糊点：
1. **geometry 类型的 CRS 与计算关系**：明确 geometry 类型的 CRS 不影响几何计算，计算始终是笛卡尔（Cartesian）的。这意味着即使 geometry 指定了 CRS，几何运算（如距离、面积）不使用该 CRS 进行球面计算，而是在平面笛卡尔坐标系中进行。
2. **边界框跨越行为仅适用于 geography**：原先规范将 `xmin > xmax`（跨越日期变更线）的边界框匹配行为同时应用于 geometry 和 geography，但实际上这种行为只对 geography 有意义（因为地理坐标 X 在 [-180, 180] 范围环绕），对 geometry 不适用。此次澄清将此行为限制为仅 geography。

## 如何达成设计目的

通过修改 `format/spec.md` 文件中的两处描述完成规范澄清：
1. 在 CRS 描述部分新增一句关于 geometry 类型计算行为的说明
2. 重写边界框描述，将跨越行为和范围限制明确限定为 geography only

## 修改详情

### `format/spec.md` (+5/-1 lines)

**修改目的**：澄清 geometry 类型的限制和行为。

**工作逻辑**：

**修改 1（CRS 部分）**：在 "For `geometry` and `geography` types, the parameter C refers to the CRS..." 段落之后新增：
> "For `geometry` type, the CRS does not affect geometric calculations, which are always Cartesian."

这明确 geometry 的几何计算始终是笛卡尔的，CRS 仅作为元数据标识，不参与计算。这对 geography 不同——geography 的 CRS 会影响计算（如球面距离）。

**修改 2（边界框部分）**：重写原先同时涵盖 geometry 和 geography 的边界框描述：
- 原文将 `lower_bounds`/`upper_bounds` 的坐标定义和 `xmin > xmax` 的跨越匹配行为混在一起描述，且范围限制同时应用于 geometry 和 geography
- 新描述拆分为两段：
  - 第一段：仅描述 `lower_bounds`/`upper_bounds` 的坐标定义（X, Y, Z, M），适用于 geometry 和 geography
  - 第二段：明确 "For `geography` only" 的跨越行为（`xmin > xmax` 时匹配条件）和范围限制（X: [-180..180], Y: [-90..90]）

这一修改确保 geometry 类型不受地理坐标环绕行为的影响，避免实现者对 geometry 类型错误地实现跨越匹配逻辑。

## 总结

本提交澄清了 Iceberg V3 规范中 geometry 和 geography 类型的两个关键行为：geometry 的几何计算始终是笛卡尔坐标的（不受 CRS 影响），以及边界框跨越日期变更线的行为仅适用于 geography 而非 geometry。这些澄清对实现 Iceberg V3 地理空间类型的引擎有重要指导意义，避免实现歧义。
