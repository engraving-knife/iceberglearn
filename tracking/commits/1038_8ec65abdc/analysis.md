# 提交 1038：Spec: Fix rendering of unified partition struct (#10896)

## 提交信息

- **序号**：1038 / 4088
- **哈希**：8ec65abdc5916ed19fffc6af6b58a2ee92d71c28
- **短哈希**：8ec65abdc
- **日期**：2024-08-07 13:05:44 +0200
- **作者**：Piotr Findeisen <piotr.findeisen@gmail.com>
- **提交说明**：Spec: Fix rendering of unified partition struct (#10896)
- **PR/Issue**：#10896

## 总体目的

Iceberg 规范文档 `format/spec.md` 中有一段描述"统一分区类型（unified partition struct）"的示例，原文中使用了未转义的尖括号 `Struct<field#1, field#2, field#3>`。由于该文档以 Markdown 编写并通过静态站点生成器渲染为 HTML，尖括号 `<...>` 会被渲染器当作 HTML 标签处理。`Struct<...>` 中的 `Struct` 不是合法 HTML 标签名，渲染器会将其静默丢弃，导致最终网页上显示为不完整的句子：

> The unified partition type looks like Struct.

（`<field#1, field#2, field#3>` 部分被当作未知 HTML 标签整体消失。）读者无法理解统一分区类型的实际结构。

本提交的目的是修复这一渲染缺陷：将尖括号及其包含的类型表达式放入反引号（inline code）中，使 Markdown 渲染器将其作为代码文本输出而非 HTML 标签解析。同时为保持一致性，将示例中的 `spec#0`、`spec#1`、`field#1`、`field#2`、`field#3`、`{field#1, field#2}` 等标识符也统一放入反引号。

## 如何达成设计目的

在 `format/spec.md` 的"unified partition type"段落中，将所有包含尖括号的表达式（`Struct<...>`）和裸写的标识符（`spec#0`、`field#1` 等）用反引号包裹，使其在 Markdown 渲染时以等宽代码字体原样显示，不再被解释为 HTML。这是 Markdown 中处理特殊字符的标准做法。

## 修改详情

### `format/spec.md`

**修改目的**：修复统一分区类型示例的 HTML 渲染问题，使 `Struct<...>` 不再被吞掉。

**工作逻辑**：对两段示例（1 和 2）做相同的反引号包裹处理。改动前后对比（以示例 1 为例）：

```diff
-1) spec#0 has two fields {field#1, field#2}
-and then the table has evolved into spec#1 which has three fields {field#1, field#2, field#3}.
-The unified partition type looks like Struct<field#1, field#2, field#3>.
+1) `spec#0` has two fields `{field#1, field#2}`
+and then the table has evolved into `spec#1` which has three fields `{field#1, field#2, field#3}`.
+The unified partition type looks like `Struct<field#1, field#2, field#3>`.
```

示例 2 同理：
- `spec#0`、`spec#1` 加反引号
- `{field#1, field#2}`、`{field#2}` 加反引号
- `Struct<field#1, field#2>` 加反引号

共 6 行修改（每段 3 行，两段共 6 行），纯文本格式调整，不改变技术含义。

## 小结

- **成效**：修复了规范文档网站上一处因尖括号被当作 HTML 标签而导致内容缺失的渲染 bug，使"统一分区类型"示例的 `Struct<field#1, field#2, field#3>` 能正确显示为代码文本。
- **影响范围**：仅 `format/spec.md` 一个文件、6 行格式调整，无规范语义变更。
- **回迁到 1.4.x 的注意事项**：可以回迁，风险极低。纯文档格式修复，不涉及任何代码或规范语义。1.4.x 分支的 spec.md 若存在相同段落（大概率存在），建议直接回迁以保持文档一致性。若 1.4.x 的 spec.md 该段落措辞已不同，则按需手动调整。
