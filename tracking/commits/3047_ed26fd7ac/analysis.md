# 提交 3047：DOAP: add release 1.10.1 (#14917)

## 提交信息

- **序号**：3047 / 4088
- **哈希**：ed26fd7acae713d4197f4823e86b0da4e0cec8e5
- **短哈希**：ed26fd7ac
- **日期**：2025-12-22
- **作者**：Huaxin Gao
- **提交说明**：DOAP: add release 1.10.1 (#14917)
- **PR/Issue**：#14917

## 总体目的

Apache 项目在仓库根目录维护一个 `doap.rdf` 文件（DOAP = Description of a Project，一种 RDF 词汇表），用于向 Apache 基金会的项目基础设施（projects.apache.org）声明本项目的最新发布版本。每当 Iceberg 发布一个新版本，都需要更新该文件中的 `<release>` 条目，使 Apache 项目页面与发布体系能展示最新的发布信息。

此提交对应 Iceberg 1.10.1 的发布（创建日期 `2025-12-22`）。在 1.10.0（2025-09-11 发布）之后，社区推出了 1.10.1 维护版本，因此需要把 `doap.rdf` 中 `<Version>` 块的 `name`/`created`/`revision` 三个字段从 1.10.0 替换为 1.10.1。提交说明里的 "change name to 1.10.1" 也表明这是一个两步小改动：先加 release 再修正名称字段，属于发布流程的收尾元数据维护。

## 如何达成设计目的

直接编辑 `doap.rdf`，将原有 1.10.0 的发布条目替换为 1.10.1 的对应值，不新增额外的 release 块（即只保留最新一个 release，符合该文件一贯的维护方式）。

## 修改详情

### `doap.rdf` (+3/-3 lines)

**修改目的**：把 DOAP 声明的最新发布版本更新为 1.10.1。

**工作逻辑**：`<release>` 下的 `<Version>` 中三处字段同步替换：`<name>1.10.0</name>` → `<name>1.10.1</name>`；`<created>2025-09-11</created>` → `<created>2025-12-22</created>`；`<revision>1.10.0</revision>` → `<revision>1.10.1</revision>`。`name` 用于人类可读的版本标题，`revision` 用于机器读取的版本号，`created` 用于发布日期。Apache 基础设施会定期抓取该文件来更新项目发布历史，因此这三者必须一致且指向最新发布。

## 总结

这是一个发布流程配套的元数据维护提交，价值在于让 Iceberg 1.10.1 在 Apache 项目页面与发布跟踪体系中正确登记，属于版本发布后的标准收尾动作，无功能性代码影响。
