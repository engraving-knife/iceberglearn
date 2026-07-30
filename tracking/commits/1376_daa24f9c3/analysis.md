# 提交 1376：Docs: Fix rendering lists (#11546)

## 提交信息

- **序号**：1376 / 4088
- **哈希**：daa24f9c3a56e18d188097deb2dd79cc991c9a78
- **短哈希**：daa24f9c3
- **日期**：2024-11-14（Thu Nov 14 18:48:48 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Fix rendering lists (#11546)
- **PR/Issue**：#11546

## 总体目的

Iceberg 的格式规范文档（`format/` 目录下的 `spec.md`、`puffin-spec.md`、`view-spec.md`）使用 Markdown 编写，并通过静态站点生成器渲染到文档网站。在标准 Markdown 与多数渲染器（包括 CommonMark 及基于它的站点生成器）中，**一个列表要被正确识别为列表块，其前必须有一个空行**；如果列表紧贴在上一段正文之后（中间无空行），渲染器会把列表项当作上一段的延续文本，导致列表项丢失项目符号、合并成一坨文字，严重影响规范文档的可读性。

本提交在 3 个规范文档中共 5 处"正文段紧接列表"的位置补上空行，使列表能被正确渲染。

## 如何达成设计目的

逐文件检查 Markdown 源码，找到"正文行 + 紧接列表项（`*` 或数字）"且中间无空行的位置，在正文与列表之间插入一个空行。这是纯文档格式修复，无任何代码或逻辑变更。

## 修改详情

### `format/puffin-spec.md`

修改目的：修复"序列化 blob 内容"列表的渲染。

工作逻辑：在 "The serialized blob contains:" 这一行之后、`* Combined length of the vector...` 列表之前，插入一个空行。这样该无序列表（4 项）才能正确渲染为带项目符号的列表。

### `format/spec.md`（2 处）

修改目的：修复两处列表渲染。

工作逻辑：
1. 在类型提升说明段 "...This may happen for the following type promotion cases:" 之后、`* date to timestamp or timestamp_ns` 之前插入空行。
2. 在行级删除说明段 "There are three types of row-level deletes:" 之后、`* Deletion vectors (DVs)...` 之前插入空行。该列表含 3 项（DV、position delete、equality delete），是 v3 规范的重要说明，渲染错误会严重影响理解。

### `format/view-spec.md`（2 处）

修改目的：修复视图规范中两处列表渲染。

工作逻辑：
1. 在 "Notes:" 之后、`1. The number of versions to retain...` 有序列表之前插入空行。
2. 在 "Each representation is an object with at least one common field, `type`, that is one of the following:" 之后、`* sql: a SQL SELECT statement...` 之前插入空行。

## 小结

- 成效：修复了 3 个格式规范文档共 5 处列表因缺少前置空行而渲染异常的问题，使文档网站上的列表正确显示项目符号/编号。
- 影响范围：仅文档格式，5 个空行新增，无代码、无构建、无运行时影响。
- 回迁到 1.4.x 的注意事项：**可选回迁**。这是纯文档渲染修复，对 1.4.x 的运行时无任何影响。若 1.4.x 分支的对应文档存在同样的"正文紧接列表无空行"问题，可回迁以改善文档展示；若 1.4.x 的文档内容与 main 差异较大（例如 v3/DV 相关章节在 1.4.x 中可能不存在或不同），则按需局部应用即可，无需整体 cherry-pick。文档类修复不阻塞发布。
