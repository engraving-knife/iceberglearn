# 提交 0750：Update doap.rdf for 1.5.2 release (#10297)

## 提交信息

- **序号**：0750 / 4088
- **哈希**：b187b17f3faa15d9fb6f3f0acde7aad7a6213cd8
- **短哈希**：b187b17f3
- **日期**：2024-05-09 11:43:35 -0600
- **作者**：Amogh Jahagirdar <amogh@tabular.io>
- **提交说明**：Update doap.rdf for 1.5.2 release (#10297)
- **PR/Issue**：#10297

## 总体目的

这是 1.5.2 版本发布配套的 DOAP（Description of a Project）文件更新提交。Apache 项目在仓库根目录维护 `doap.rdf` 文件，用于向 Apache 项目基础设施（如 [projects.apache.org](https://projects.apache.org)）声明项目的最新发布版本信息。每次发版后需要更新该文件中的 `<release>` 段，将版本号和发布日期更新为新版本。本提交将 DOAP 文件中的发布版本从 1.5.1（2024-04-25）更新为 1.5.2（2024-05-09）。

## 如何达成设计目的

直接修改 `doap.rdf` 中 `<release>` 段的三个字段：`<name>`、`<created>`、`<revision>`，分别对应版本名称、发布日期、版本号，将它们从 1.5.1 的值替换为 1.5.2 的值。

## 修改详情

### `doap.rdf`

**修改目的**：将 DOAP 文件声明的最新发布版本从 1.5.1 更新为 1.5.2。

**工作逻辑**：`<release>` 段内的 `<Version>` 元素修改如下：

- `<name>1.5.1</name>` → `<name>1.5.2</name>`
- `<created>2024-04-25</created>` → `<created>2024-05-09</created>`
- `<revision>1.5.1</revision>` → `<revision>1.5.2</revision>`

注意此文件只维护"最新发布版本"一条记录（非历史版本列表），因此是替换而非新增。

## 小结

- **成效**：完成 1.5.2 发版的 DOAP 文件更新，使 Apache 项目基础设施能正确反映 Iceberg 的最新发布版本为 1.5.2。
- **影响范围**：仅影响仓库根目录的 `doap.rdf` 一个文件，不涉及任何代码变更。
- **回迁到 1.4.x 的注意事项**：这是 1.5.2 发版配套的元数据维护变更，与 1.4.x 分支无关，无需回迁。
