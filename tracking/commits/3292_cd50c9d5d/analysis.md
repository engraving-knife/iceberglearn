# 提交 3292：Docs: Fix typos in contribute.md (#15383)

## 提交信息

- **序号**：3292 / 4088
- **哈希**：cd50c9d5df0d9ab421104a43f42ea29175f87dd5
- **短哈希**：cd50c9d5d
- **日期**：2026-02-20
- **作者**：Moshe Blumberg
- **提交说明**：Docs: Fix typos in contribute.md (#15383)
- **PR/Issue**：#15383

## 总体目的

`site/docs/contribute.md` 是 Iceberg 项目面向贡献者的核心指南文档，涵盖 API/ABI 兼容性、废弃流程、RevAPI 校验、配置命名规范等内容。本提交修正了该文档中三处影响理解或正确性的笔误，提升贡献者文档的准确性：

1. 在"废弃（Deprecation）"小节，将 javadoc 标签 `@depreceted` 更正为 `@deprecated`。这是一个会被贡献者照抄到自己代码里的标签名，原写法是错误拼写，直接照抄会导致 javadoc 工具无法识别该标签、废弃说明失效，因此属于有实际指导价值的修正。
2. 在"添加默认实现以避免破坏 API"小节，移除句末多余的反引号字符（`UnsupportedOperationException`:\`` 末尾多了一个反引号）。该多余字符破坏了 markdown 行内代码的闭合，可能在渲染时产生异常格式。
3. 在"配置命名（Config naming）"小节，将 `preferred convection` 更正为 `preferred convention`。`convection`（对流）与 `convention`（约定/惯例）是形近且发音相近但语义无关的词，此处显然应为"惯例/约定"，属于语义性笔误。

这三处都是文档类小修，背景在于贡献者文档是新人上手与遵循规范的依据，拼写错误不仅影响阅读体验，像 `@deprecated` 这类标签错误还会被直接复制进代码造成实际问题，因此值得单独修正。

## 如何达成设计目的

仅对 `site/docs/contribute.md` 的三行做最小化文本替换，不改任何结构与逻辑。改动集中在 deprecation 示例说明、默认实现说明、配置命名说明三个小节。

## 修改详情

### `site/docs/contribute.md` (+3/-3 lines)

**修改目的**：修正三处笔误。

**工作逻辑**：
- 第 191 行附近：`@depreceted` javadoc 注释 → `@deprecated` javadoc 注释。修正废弃标签拼写，确保贡献者照抄时 javadoc 标签可被正确识别。
- 第 279 行附近：`...throws an `UnsupportedOperationException`:\`` 末尾多余反引号 → 去掉该多余反引号，使行内代码闭合正确、避免 markdown 渲染异常。
- 第 429 行附近：`preferred convection` → `preferred convention`。修正用词，表达"首选约定"而非"首选对流"。

## 总结

本提交对贡献者指南 `contribute.md` 做了三处笔误修正，其中 `@deprecated` 标签拼写与多余反引号具备实际正确性意义（影响代码示例可用性与 markdown 渲染），`convention` 用词修正则消除语义歧义，整体提升了贡献者文档的准确性与专业度。
