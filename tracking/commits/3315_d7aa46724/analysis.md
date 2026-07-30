# 提交 3315：Docs: Add informational properties section for table comment (#15367)

## 提交信息

- **序号**：3315 / 4088
- **哈希**：d7aa46724c25e732e8f1bac20649ce11207475d7
- **短哈希**：d7aa46724
- **日期**：2026-02-25
- **作者**：yguy-ryft
- **提交说明**：Docs: Add informational properties section for table comment (#15367)
- **PR/Issue**：#15367

## 总体目的

Iceberg 表的配置属性文档（`configuration.md`）中已有"表属性"和"兼容性标志"等分类，但缺少对"信息属性"（informational properties）的说明。信息属性是一类不影响读写行为和查询语义、仅用于提供表额外上下文的属性，如表的 `comment` 属性用于记录表的业务含义和使用场景。

此前用户如果想在表上设置 `comment`，需要了解这是一个信息属性而非行为配置，但文档中没有明确区分这一类别，导致用户可能误解 `comment` 的作用范围或不知道该属性的用途。本提交在配置文档中新增"Informational properties"小节，明确说明信息属性的作用（文档化、发现、与外部工具集成）和 `comment` 属性的语义，帮助用户正确理解和使用。

## 如何达成设计目的

在 `docs/docs/configuration.md` 文件的"表属性"表格之后、"兼容性标志"表格之前插入一个新的 `### Informational properties` 小节，包含一段说明文字和一个属性表格。

## 修改详情

### `docs/docs/configuration.md` (+8 lines)

**修改目的**：新增信息属性文档小节。

**工作逻辑**：
在 `format-version` 属性表格之后插入 `### Informational properties` 小节。说明文字指出："信息属性可用于提供表的额外上下文，对文档化、发现和与外部工具集成很有用，不影响读写行为或查询语义。"随后是一个属性表格，列出 `comment` 属性，默认值为 `(not set)`，描述为"A table-level description that documents the business meaning and usage context."（表级描述，记录业务含义和使用上下文）。该小节位于"表属性"和"兼容性标志"之间，逻辑分类清晰。

## 总结

本提交在 Iceberg 配置文档中新增了"信息属性"小节，明确 `comment` 作为不影响读写语义、仅用于文档化和外部集成的表级描述属性的定位。这填补了文档中对信息属性类别说明的空白，帮助用户正确理解和使用 `comment` 属性。
