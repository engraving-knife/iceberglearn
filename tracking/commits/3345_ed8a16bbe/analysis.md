# 提交 3345：docs: udf spec, add newline to properly render lists (#15516)

## 提交信息

- **序号**：3345 / 4088
- **哈希**：ed8a16bbeb549b0286d3c229beb5a0cf165f2f4b
- **短哈希**：ed8a16bbe
- **日期**：2026-03-04 15:38:12 -0800
- **作者**：Kevin Liu
- **提交说明**：docs: udf spec, add newline to properly render lists (#15516)
- **PR/Issue**：#15516

## 总体目的

本提交修复 UDF 规范文档（`format/udf-spec.md`）在文档网站上列表无法正确渲染的问题，方法是在每处"引导文字 + 列表"之间补上缺失的空行。

背景是：在 Markdown（尤其是 CommonMark 与 MkDocs 使用的渲染器）中，一个列表要被正确解析为独立的列表块，其前一行与列表首项之间必须有一个空行。若列表紧接在一段文字之后（中间无空行），许多渲染器会把列表项"懒加载"地并入前一段文字，导致本应显示为编号列表或项目符号列表的内容被合并成普通段落，丢失列表格式。`format/udf-spec.md` 中有多处"Notes:"、"…如下："等引导语后紧跟有序或无序列表却未留空行的情况。

这一问题在 UDF 规范接入文档网站后尤为突出——序号 3339 的提交刚通过软链接把 `format/udf-spec.md` 接入 `site/docs/` 并加入 MkDocs 导航，网站渲染时会严格按 CommonMark 解析，这些列表若不补空行就会显示错乱。GitHub 上的渲染相对宽容，但为保网站正确显示需统一修正。本提交逐一定位这些位置并补上空行。

## 如何达成设计目的

通读 `format/udf-spec.md`，在所有"引导段落/行紧跟列表"且中间缺空行的位置插入一个空行，共 7 处。改动不涉及任何文字内容变更，仅是排版空行调整，使每个列表前都有空行与上文分隔，从而被 Markdown 渲染器正确识别为列表块。

## 修改详情

### `format/udf-spec.md` (+7 lines)

**修改目的**：在 7 处列表前补空行，使其在 MkDocs 网站与 GitHub 上正确渲染为列表。

**工作逻辑**：
共 7 处改动，每处在一段引导文字与紧随其后的列表之间插入一个空行：

1. UDF 元数据字段表后的 `Notes:` 与有序列表（`1. When secure is set to true...`）之间——补空行后 Notes 下的编号列表正确渲染。

2. 参数表后的 `Notes:` 与有序列表（`1. Variadic (vararg) parameters are not supported...`）之间——同上。

3. 嵌套类型说明段 `...Any other fields must be ignored.` 与项目符号列表（`* list requires...`、`* map requires...`、`* struct requires...`）之间——补空行后三个嵌套类型说明作为列表项渲染。

4. `Examples of complete definition-id signatures:` 与项目符号列表（`* int`、`* int,string`、`* int,list<int>,...`）之间——补空行后签名示例作为列表项渲染。

5. `#### Null Input Handling` 小节中 `` `on-null-input` provides an optimization hint for query engines:`` 与有序列表（`1. If set to return-null...`、`2. If set to call...`）之间——补空行后两种 null 处理行为作为编号列表渲染。

6. `### Representation` 小节中 `...one of the following:` 与项目符号列表（`* sql: a SQL expression...`）之间——补空行后表示类型列表正确渲染。

7. SQL 表示元数据表后的 `Notes:` 与有序列表（`1. The sql must reference parameters...`）之间——补空行后 Notes 列表正确渲染。

这些改动遵循 CommonMark 规范：列表块前需有空行分隔上文。补空行后，MkDocs（Pygments/Markdeep 等解析器）与 GitHub 均能将这些内容正确渲染为有序或无序列表，而非被并入前段文字。

## 总结

本提交在 UDF 规范文档的 7 处"引导文字 + 列表"之间补上缺失的空行，修复了因缺少空行导致列表在 MkDocs 文档网站（及严格 CommonMark 渲染器）上被错误并入前段段落、丢失列表格式的问题。该修正与序号 3339 将 UDF spec 接入网站导航的改动相配合，确保 UDF 规范在网站上正确显示。
