# 提交 1923：Docs: Update block spacing guideline in contribute.md (#12641)

## 提交信息

- **序号**：1923 / 4088
- **哈希**：af99da6c2daa8e53876638c839b0f729f2f3a86e
- **短哈希**：af99da6c2
- **日期**：2025-03-26 07:43:42 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Docs: Update block spacing guideline in contribute.md (#12641)
- **PR/Issue**：#12641

## 总体目的

`site/docs/contribute.md` 是 Iceberg 的代码贡献规范文档，其中 "Code Style" 章节定义了 Java 编码风格约定。本提交在该章节新增一条 "Block Spacing"（代码块间距）规范：要求在控制块（`if`、`for`、`while`、`switch` 等）之后总是留一个空行，以分隔逻辑段落、提升可读性与一致性。

此前文档没有明确该约定，贡献者代码风格不统一（有人块后留空行，有人不留），评审时也缺少依据。本次把该约定写入文档并给出 BAD/GOOD 对照示例，作为后续评审与代码风格检查的参考。

## 如何达成设计目的

在 `contribute.md` 的代码风格小节（紧跟配置键命名规范之后、Testing 之前）插入 "#### Block Spacing" 子节：
- 文字说明：为提升可读性与一致性，控制块后总是留一个空行。
- 给出两组 Java 示例：
  - `if` 块：BAD（无空行）vs GOOD（`if` 块后空行再 `return`）。
  - `for` 块：BAD（无空行）vs GOOD（`for` 块后空行再 `return`）。

## 修改详情

### `site/docs/contribute.md` (修改, +43 lines)

**修改目的**：新增 Block Spacing 代码风格规范。

**工作逻辑**：插入子节包含规范说明与两段 BAD/GOOD 对照 Java 代码示例。示例一为 `if` 块后 `return`，示例二为 `for` 块后 `return`，分别展示无空行（BAD）与有空行（GOOD）的写法。

## 总结

本提交在贡献规范文档 `contribute.md` 中新增 "Block Spacing" 风格指南，要求控制块（if/for/while/switch 等）之后留一个空行以分隔逻辑段落，并附带 BAD/GOOD Java 示例，统一贡献者代码风格、为评审提供依据。
