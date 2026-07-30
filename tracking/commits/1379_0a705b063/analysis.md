# 提交 1379：Docs: 4 Spaces are Requried for Sublists (#11549)

## 提交信息

- **序号**：1379 / 4088
- **哈希**：0a705b0637db484730eb4eece69ae6c4d52fd9da
- **短哈希**：0a705b063
- **日期**：2024-11-14（Thu Nov 14 16:04:01 2024 -0600）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：Docs: 4 Spaces are Requried for Sublists (#11549)（原文 Requried 为拼写错误，应为 Required）
- **PR/Issue**：#11549

## 总体目的

`site/docs/releases.md` 是 Iceberg 文档站点上的发布说明页，按版本列出每次发布的特性、修复与依赖更新。该文件大量使用嵌套列表（sublist）结构：一级条目用 `*` 标记（如 `* Core`、`* API`、`* AWS`），其下的二级条目用 `-` 标记。

原文件中二级条目使用 **2 个空格**的缩进（`  - item`）。然而 Iceberg 文档站点使用的 Markdown 渲染器要求嵌套列表必须使用 **4 个空格**的缩进才能被识别为上级列表的子项。2 空格缩进下，渲染器不会把 `- item` 当作 `* Core` 的子列表，而是错误地渲染为同级或普通段落，导致发布说明的层级结构丢失、可读性下降。

本提交将 `releases.md` 中所有二级列表项的缩进从 2 空格改为 4 空格，使子列表在文档站点上正确嵌套渲染。

## 如何达成设计目的

逐行将形如 `  - xxx`（2 空格 + 短横线）的二级列表项改为 `    - xxx`（4 空格 + 短横线），即在每个二级列表项行首增加 2 个空格。涉及 1.7.0、1.6.1 等多个发布版本段落的子列表。共 65 行被修改（每行只是缩进调整，内容不变），呈现为 65 insertions / 65 deletions。

## 修改详情

### `site/docs/releases.md`

修改目的：修复发布说明中嵌套列表的缩进，使子列表正确渲染。

工作逻辑：将文件中所有 2 空格缩进的二级列表项（`  - ` 开头）改为 4 空格缩进（`    - ` 开头）。受影响的段落包括 1.7.0 release 的 Deprecation/API/AWS/Build/Dependencies/Core/Flink/Spec/Spark 等分类下的子项，以及 1.6.1 release 的 Core/Dependencies 子项等。例如：

修改前：
```
* Core
  - Limit ParallelIterable memory consumption by yielding in tasks ([\#10787]...)
  - Drop ParallelIterable's queue low water mark ([\#10979]...)
* Dependencies
  - ORC 1.9.4
```

修改后：
```
* Core
    - Limit ParallelIterable memory consumption by yielding in tasks ([\#10787]...)
    - Drop ParallelIterable's queue low water mark ([\#10979]...)
* Dependencies
    - ORC 1.9.4
```

无任何文字内容变更，纯缩进调整。

## 小结

- 成效：发布说明页的嵌套列表现可在文档站点上正确渲染为子列表，恢复"版本分类 → 具体条目"的层级展示。
- 影响范围：仅 `site/docs/releases.md` 一个文件，65 行缩进调整，无代码、无构建、无运行时影响。
- 回迁到 1.4.x 的注意事项：**可选回迁**。这是纯文档渲染修复，对 1.4.x 运行时无影响。若 1.4.x 分支的 `releases.md` 存在同样的 2 空格子列表缩进问题，可回迁以改善文档展示。但需注意 1.4.x 的 releases.md 内容可能与 main 不同（版本段落不同），应按 1.4.x 实际文件内容应用相同的 4 空格规则，而非直接 cherry-pick。文档类修复不阻塞发布。
