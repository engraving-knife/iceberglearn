# 提交 0126：Remove outdated `tox` command from doc (#8961)

## 提交信息

- **序号**：0126 / 4088
- **哈希**：b95330a257b544be976a8e5bb1ea02ab8c8fcb7e
- **短哈希**：b95330a25
- **日期**：2023-11-02 12:51:32 +0100
- **作者**：Hussein Awala
- **提交说明**：Remove outdated `tox` command from doc (#8961)
- **PR/Issue**：#8961

## 总体目的

这个提交是一个文档维护性修复，目标是移除贡献指南（contribute.md）中已经过时的 Python 代码格式化说明。

在 Iceberg 项目的贡献文档中，"Style"（代码风格）一节原本建议贡献者使用 `tox -e format` 命令对 Python 代码进行自动格式化。然而随着项目构建与代码风格工具链的演进，`tox` 已不再是 Iceberg 推荐或维护的 Python 格式化入口，这一说明变得过时且具有误导性。保留它会让新贡献者执行一个不再有效（或不再被官方支持）的命令，轻则困惑，重则产生不符合预期的格式化结果。

本提交将该行删除，使文档仅保留当前真实有效的风格指引：Java 代码遵循 Google style，通过 `./gradlew spotlessCheck` 校验、`./gradlew spotlessApply` 自动修复。这样文档与实际构建工具链保持一致，避免误导贡献者。

## 如何达成设计目的

设计思路非常直接：定位到 `site/docs/contribute.md` 中 "Style" 小节里关于 Python `tox` 的那一行，将其删除，仅保留 Java 相关的有效说明。改动不涉及任何代码逻辑，纯属文档清理。

## 修改详情

### `site/docs/contribute.md`

**修改目的**：移除已过时的 Python `tox` 格式化命令说明。

**工作逻辑**：在 "### Style" 小节下，删除了 `For Python, please use the tox command `tox -e format` to apply autoformatting to the project.` 这一行。删除后，该小节直接以 Java 代码风格的说明开头（Google style + `./gradlew spotlessCheck` / `spotlessApply`）。此举使文档与当前实际使用的构建/格式化工具链对齐，消除对贡献者的误导。

## 小结

本提交通过删除贡献文档中已失效的 `tox -e format` 说明，保持项目文档与实际工具链一致，避免误导新贡献者。
