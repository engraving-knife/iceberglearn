# 提交 3246：docs: Fix uuid type formatting in schemas.md (#15309)

## 提交信息

- **序号**：3246 / 4088
- **哈希**：e95739e34c4e7227d51c7cd1466f0b4c049ac0ea
- **短哈希**：e95739e34
- **日期**：2026-02-13
- **作者**：MJY
- **提交说明**：docs: Fix uuid type formatting in schemas.md (#15309)
- **PR/Issue**：#15309（关联 #14874）

## 总体目的

Iceberg 的官方文档 `docs/docs/schemas.md` 以 Markdown 表格形式列出了 Iceberg 表支持的全部数据类型，每个类型包含三列：类型名、描述和备注。该表格在之前的状态中存在两个问题。

首先是 `uuid` 类型的行存在结构性缺陷。原始行 `| **\`uuid\`** | Universally unique identifiers` 缺少第三列（Notes）的内容和结尾的管道符 `|`，导致 Markdown 表格解析器无法正确识别该行，进而可能破坏整个表格的渲染效果——后续行的列对齐也会受到牵连。其次是表格中多行（如 `timestamp_ns`、`timestamptz_ns`、`variant`、`geometry(C)`、`geography(C, A)`、`unknown` 等）的列宽不一致，各列之间的空格数量参差不齐，虽然不影响 Markdown 语义，但降低了源文件的可读性和可维护性。

本次提交的动机正是修复 `uuid` 行的格式错误，同时对整个表格进行规范化重排，使所有行的列宽统一对齐。关联的 Issue #14874 可能是关于 v3 新增类型（variant、geometry、geography、unknown 等）文档的后续完善工作。

## 如何达成设计目的

通过重写 `docs/docs/schemas.md` 中的类型表格，统一调整每一行的管道符位置和列内空格填充，使三列在所有行中保持一致的宽度。同时对 `uuid` 行补全缺失的第三列空内容和结尾管道符。改动仅涉及文档，不涉及任何代码逻辑。

## 修改详情

### `docs/docs/schemas.md` (+25/-25 lines)

**修改目的**：修复 uuid 行的表格格式缺陷并统一整个类型表格的列对齐。

**工作逻辑**：
该文件的全部 50 行改动（25 增 25 删）集中在类型表格区域。核心修复点是将 `uuid` 行从缺失列的状态：

```
| **`uuid`**             | Universally unique identifiers
```

修正为完整的三列格式：

```
| **`uuid`**            | Universally unique identifiers                                           |                                                 |
```

补全了第三列（Notes，此处为空）和结尾的 `|`。此外，表格分隔行从紧凑的 `|---|---|---|` 风格改为带空格的 `| --- | --- | --- |` 风格，所有行的各列宽度也统一按照最宽内容对齐填充空格。例如 `geography(C, A)` 等较长的类型名现在与所有其他行保持相同的列宽。这种规范化虽然不改变渲染结果（Markdown 表格不依赖空格对齐），但显著提升了源码可读性，使后续编辑不易出错。

## 总结

本次提交修复了 schemas.md 文档中 `uuid` 类型行缺少表格列的格式缺陷，同时将整个数据类型表格规范化为统一列宽。这是一项纯文档质量改进，确保用户在阅读 Iceberg 类型文档时表格能正确渲染，并提升了源文件的可维护性。
