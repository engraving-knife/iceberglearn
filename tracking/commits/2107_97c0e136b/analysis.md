# 提交 2107：Spec: Clarify behavior of special geo objects for lower/upper bounds (#12956)

## 提交信息

- **序号**：2107 / 4088
- **哈希**：97c0e136b8021058897cab7539e3ef89ce5a0341
- **短哈希**：97c0e136
- **日期**：2025-05-09 15:29:04 -0700
- **作者**：Szehon Ho <szehon.apache@gmail.com>
- **提交说明**：Spec: Clarify behavior of special geo objects for lower/upper bounds (#12956)
- **PR/Issue**：#12956

## 总体目的

Iceberg 格式规范在 `format/spec.md` 中规定了 `geometry` 与 `geography` 类型数据文件 lower/upper bounds 的计算方式：bounds 是由 X、Y、Z、M 四个坐标维度的最小/最大值构成的点。但原文档没有说明当坐标维度出现 `null` 或 `NaN` 时应如何处理，也没有说明当某个维度全为 null/NaN 或 X/Y 维度缺失时 bounding box 是否还应生成。这导致不同实现可能做出不同选择，产生不一致的统计值与过滤行为。

本次提交在规范中新增一段说明，明确：计算 bounds 时跳过 null/NaN 坐标值；若某维度全部为 null/NaN，则该维度从 bounding box 中省略；若 X 或 Y 维度缺失，则整个 bounding box 不生成。这统一了各实现面对特殊 geo 对象（含 null/NaN 坐标）时的行为契约。

## 如何达成设计目的

在 `format/spec.md` 现有 geo bounds 段落之后新增一段文字，分三点说明：跳过 null/NaN、维度全空则省略该维度、X 或 Y 缺失则不生成 bounding box。

## 修改详情

### `format/spec.md` (修改, +2/-0 lines)

**修改目的**：明确 geo 类型 bounds 计算对 null/NaN 坐标的处理。

**工作逻辑**：
在描述 `geometry`/`geography` 的 `lower_bounds`/`upper_bounds` 段落之后新增一段：

> When calculating upper and lower bounds for `geometry` and `geography`, null or NaN values in a coordinate dimension are skipped; for example, POINT (1 NaN) contributes a value to X but no values to Y, Z, or M dimension bounds. If a dimension has only null or NaN values, that dimension is omitted from the bounding box. If either the X or Y dimension is missing then the bounding box itself is not produced.

要点：
- 跳过 null/NaN：`POINT (1 NaN)` 只贡献 X，不贡献 Y/Z/M。
- 维度全空则省略：某维度若只有 null/NaN，该维度不出现在 bounding box。
- X 或 Y 缺失则不生成 bounding box：保证核心二维坐标必须存在，否则该文件无 bounds。

## 总结

本次提交是 Iceberg 格式规范的澄清性补充：明确 `geometry`/`geography` 类型在计算 lower/upper bounds 时如何处理 null/NaN 坐标（跳过）、维度全空时的省略、以及 X/Y 维度缺失时不生成 bounding box。改动仅 2 行 markdown，但消除了实现间的歧义，保证各语言实现面对含特殊坐标值的 geo 对象时行为一致。
