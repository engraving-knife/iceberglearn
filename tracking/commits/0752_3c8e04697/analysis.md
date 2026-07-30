# 提交 0752：Spec: Fix markdown for struct evolution default value rules (#10290)

## 提交信息

- **序号**：0752 / 4088
- **哈希**：3c8e046978b1cc27080ff8fc1d626cdc92066562
- **短哈希**：3c8e04697
- **日期**：2024-05-09 17:11:54 -0700
- **作者**：Dustin Metzgar
- **提交说明**：Spec: Fix markdown for struct evolution default value rules (#10290)
- **PR/Issue**：#10290

## 总体目的

本提交修复 Iceberg 表格式规范（spec）文档中关于 struct（结构体）演进默认值规则的 Markdown 渲染问题。在 `format/spec.md` 中，"Struct evolution requires the following rules for default values:"这句话之后紧跟着一个列表项（`* The initial-default must be set...`），但中间缺少一个空行。根据 Markdown 规范，一个以句号结尾的段落行后若直接跟以 `*` 开头的列表项，许多 Markdown 渲染器不会将其识别为列表，而是把 `*` 当作普通文本显示，导致三条默认值规则无法以列表形式正确渲染。本提交通过在该句与列表之间插入一个空行，使三条规则被正确渲染为无序列表。

## 如何达成设计目的

规范的"struct evolution"（结构体演进）一节定义了结构体在演进（增删字段、改名等）时对默认值（`initial-default` 与 `write-default`）的约束规则。这些规则以三条无序列表项的形式给出。修复方式非常直接：在引导句"Struct evolution requires the following rules for default values:"与其后的第一个列表项之间插入一个空行，从而满足 Markdown 列表语法对前导空行的要求（即列表前的段落需以空行结束）。这是一个纯粹的文档格式修复，不改变规范本身的任何语义。

## 修改详情

### `format/spec.md`

**修改目的**：修复 struct 演进默认值规则列表的 Markdown 渲染。

**工作逻辑**：在 `format/spec.md` 第 230 行附近，原内容为：

```
Struct evolution requires the following rules for default values:
* The `initial-default` must be set when a field is added and cannot change
```

修改后在引导句与第一个列表项之间插入一个空行：

```
Struct evolution requires the following rules for default values:

* The `initial-default` must be set when a field is added and cannot change
```

该空行使后续三条规则（`initial-default`、`write-default`、required 字段必须设置非空默认值）被 Markdown 渲染器正确识别为无序列表项，而非被折叠为普通段落文本。三条规则本身的文字内容未做任何改动。

## 小结

- **成效**：修复了规范文档中 struct 演进默认值规则的列表渲染问题，使三条规则在 HTML/PDF 等渲染输出中能以列表形式清晰呈现，提升了规范文档的可读性。
- **影响范围**：仅规范文档 `format/spec.md` 的格式变更（新增 1 个空行），不涉及任何代码、不改变表格式规范的语义约定。
- **回迁注意事项**：此为纯文档格式修复，无兼容性风险，可直接 cherry-pick 到任何分支。回迁到 1.4.x 分支时，仅需确认目标分支的 `format/spec.md` 在对应位置存在相同的引导句与列表项结构即可。
