# 提交 3273：Docs: Add documentation pointers in README files and fix typos in Spark quickstart (#15350)

## 提交信息

- **序号**：3273 / 4088
- **哈希**：d06e6c22fecf00be81f2eb399f090fae320560a4
- **短哈希**：d06e6c22f
- **日期**：2026-02-17
- **作者**：Robin Moffatt
- **提交说明**：Docs: Add documentation pointers in README files and fix typos in Spark quickstart (#15350)
- **PR/Issue**：#15350

## 总体目的

本提交是一次纯文档改进，解决两个文档可用性问题。第一，仓库根 `README.md` 与新建的 `docs/README.md` 缺少指向"如何构建文档站点"的入口：贡献者克隆仓库后想本地构建文档时，往往不知道文档源码与构建说明位于 `site/` 目录下的 `README.md`，需要在仓库里自行摸索。第二，`site/docs/spark-quickstart.md` 这份 Spark 快速上手文档存在两处英文拼写/语法错误，影响新手阅读体验——一处是 "download the runtime by visiting to the Releases page"（多了 "to"，且 "visiting to" 搭配不当），另一处是 "Now that you're up an running"（应为 "up and running"，"an" 是 "and" 的笔误）。

具体改动：(1) 在根 `README.md` 的"从源码构建"小节之后、"Engine Compatibility"之前新增一个 "Documentation" 小节，提供指向 `site/README.md` 的相对链接；(2) 新建 `docs/README.md`，作为 `docs/` 目录的占位说明，再指回 `../site/README.md`——这是为了照顾那些习惯先翻 `docs/` 目录的读者，避免他们看到一个空目录或无说明的目录而迷路；(3) 修正 Spark quickstart 中的两处文字错误。这些改动不涉及任何代码逻辑，仅提升文档可发现性与专业度。

## 如何达成设计目的

整体思路是"补入口 + 修笔误"。涉及三个文件：根 `README.md` 加一个文档指引小节；新建 `docs/README.md` 作为中转说明；`site/docs/spark-quickstart.md` 改两处文字。文档构建说明本身已经在 `site/README.md` 中存在，本次只是把入口指过去。

## 修改详情

### `README.md` (+4/-0 lines)

**修改目的**：在根 README 中增加指向文档构建说明的入口。

**工作逻辑**：在 SELinux 说明块（`---` 分隔线）之后、"### Engine Compatibility"之前，新增一个小节 `#### Documentation`，正文为 "For information about building the documentation, see [here](site/README.md)."。链接使用相对路径指向仓库内 `site/README.md`，让贡献者能从根 README 一跳到达文档构建说明。

### `docs/README.md` (+18/-0 lines，新文件)

**修改目的**：为 `docs/` 目录提供说明中转，引导读者到真正的文档说明位置。

**工作逻辑**：新建文件，开头是 Apache License 2.0 的标准 SPDX 注释块（与仓库其它文档文件保持一致的版权头），正文为一行 "For information about the documentation, please see [this README.md](../site/README.md)"。用相对路径 `../site/README.md` 指回上级目录的真实文档说明。这个中转文件主要解决"用户进了 `docs/` 目录却不知道文档怎么构建"的导航缺失。

### `site/docs/spark-quickstart.md` (+2/-2 lines)

**修改目的**：修正 Spark 快速上手文档中的两处英文错误。

**工作逻辑**：
- "You can download the runtime by visiting to the [Releases](releases.md) page." 改为 "You can download the runtime from the [Releases](releases.md) page."——去掉冗余的 "by visiting to"（"visiting to" 搭配不当），改为更自然的 "from"。
- "Now that you're up an running with Iceberg and Spark, ..." 改为 "Now that you're up and running with Iceberg and Spark, ..."——把笔误 "an" 修正为 "and"。

## 总结

本提交通过在根 README 增加 "Documentation" 小节、新建 `docs/README.md` 中转文件并修正 Spark quickstart 中的两处文字错误，改善了文档的可发现性与专业度。改动不涉及代码，对功能无任何影响，纯属降低新手与贡献者的上手摩擦。
