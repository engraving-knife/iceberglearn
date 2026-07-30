# 提交 0088：Infra: Add 1.4.1 to Bug template (#8886)

## 提交信息

- **序号**：0088 / 4088
- **哈希**：fce19e1200bb0b59f3897dcbc4a6b804eeb67cc1
- **短哈希**：fce19e120
- **日期**：2023-10-23 17:16:56 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Infra: Add 1.4.1 to Bug template (#8886)
- **PR/Issue**：#8886

## 总体目的

这个提交要解决的是 issue 模板版本选项滞后于实际发版的问题。Iceberg 仓库的 bug 报告模板 `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml` 里有一个下拉选项，让报告者选择自己使用的 Iceberg 版本。在 1.4.1 发布后，模板里仍把 "1.4.0 (latest release)" 标为最新版本，导致报告者无法在模板里准确选到自己实际使用的 1.4.1，影响 issue 分类与版本回归定位。

本提交把 1.4.1 加为新的"latest release"选项，并把原来的 1.4.0 降级为普通历史版本选项保留在列表中。这是一个典型的"基础设施维护"类改动，属于发版后的配套模板同步工作。对 Iceberg 演进的意义在于：保持 issue 模板与实际发版节奏一致，确保 bug 报告里采集到的版本信息准确，方便维护者按版本分流和处理问题。

## 如何达成设计目的

设计思路很简单：在 bug 报告模板 YAML 的版本下拉 `options` 列表最上方，把原来的 `"1.4.0 (latest release)"` 替换为 `"1.4.1 (latest release)"`，并在其下一行新增 `"1.4.0"` 作为历史版本选项，使 1.4.0 仍可被选择。整体只改一个文件、增 2 行删 1 行。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`

**修改目的**：把 bug 报告模板的版本下拉选项更新为以 1.4.1 为最新版本，同时保留 1.4.0 作为历史选项。

**工作逻辑**：在该模板 `body` 下版本选择字段（`description: What Apache Iceberg version are you using?`，`multiple: false`）的 `options` 列表中，将原第一项 `"1.4.0 (latest release)"` 改为 `"1.4.1 (latest release)"`，并紧随其后新增 `"1.4.0"`。其余历史版本（1.3.1、1.3.0、1.2.1 等）保持不变。这样报告者打开 bug 模板时默认看到 1.4.1 为最新版本，且仍可选 1.4.0。

## 小结

通过把 bug 报告模板的版本下拉更新为以 1.4.1 为最新版本并保留 1.4.0，本提交让 issue 模板与实际发版节奏保持同步，确保采集到的版本信息准确可用。
