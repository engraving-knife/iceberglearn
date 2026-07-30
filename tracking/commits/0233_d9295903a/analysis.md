# 提交 0233：Docs: Update default format version to 2. (#9239)

## 提交信息

- **序号**：0233 / 4088
- **哈希**：d9295903abbabbbed77e12476aef62ae5260663c
- **短哈希**：d9295903a
- **日期**：2023-12-07 14:21:35 +0100
- **作者**：Yujiang Zhong
- **提交说明**：Docs: Update default format version to 2. (#9239)
- **PR/Issue**：#9239

## 总体目的

这个提交是纯文档变更，用于将 Iceberg 配置文档中 `format-version` 属性的默认值从 `1` 更新为 `2`，并补充"自 1.4.0 版本起默认为 2"的说明。

Iceberg 表格式有 v1 和 v2 两个版本。v2 引入了序列号（sequence number）、行级删除（row-level deletes，通过 delete file 实现）、默认排序顺序持久化等增强能力。随着 Iceberg 自身演进到 1.4.0，新创建表的默认格式版本已在代码层面调整为 2（见 [`TableProperties.java`](../../../../core/src/main/java/org/apache/iceberg/TableProperties.java) 中的 `FORMAT_VERSION_DEFAULT`），但配置文档 [`docs/configuration.md`](../../../../docs/configuration.md) 中仍记录默认值为 1，导致文档与实际行为不一致。

本提交修正了这一文档滞后：在 `configuration.md` 的表属性表格中，将 `format-version` 的 "Default" 列从 `1` 改为 `2`，并在 "Description" 列追加 "Defaults to 2 since version 1.4.0."。这使用户文档准确反映 1.4.0 起 v2 成为默认格式版本这一事实，避免用户误以为新表仍以 v1 创建，对 Iceberg 格式版本的推广与用户认知一致性有积极意义。

## 如何达成设计目的

改动极为聚焦：仅修改 `docs/configuration.md` 表属性表格中 `format-version` 一行的两个单元格——Default 列与 Description 列。不涉及任何代码逻辑变更。

## 修改详情

### `docs/configuration.md`

**修改目的**：将 `format-version` 表属性的文档默认值从 1 更新为 2，并标注自 1.4.0 起生效。

**工作逻辑**：

修改位于 "Table properties" 章节的属性表格中，`format-version` 行：

- Default 列：`1` → `2`
- Description 列：`Table's format version (can be 1 or 2) as defined in the [Spec](../../../spec/#format-versioning).` → `Table's format version (can be 1 or 2) as defined in the [Spec](../../../spec/#format-versioning). Defaults to 2 since version 1.4.0.`

其余属性行（位于 "Compatibility flags" 小节之前）未改动。

## 小结

这个提交修正了配置文档中 `format-version` 默认值的滞后记录，将其从 1 更新为 2 并标注自 1.4.0 起生效，使文档与代码实际行为保持一致。
