# 提交 1381：Docs: Fix level of Deletion Vectors (#11547)

## 提交信息

- **序号**：1381 / 4088
- **哈希**：307593ffd99752b2d62cc91f4928285fc0c62b75
- **短哈希**：307593ffd
- **日期**：2024-11-15（Fri Nov 15 18:19:52 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Fix level of Deletion Vectors (#11547)
- **PR/Issue**：#11547

## 总体目的

`format/spec.md` 是 Iceberg 表格式规范的核心文档，其标题层级（`#`/`##`/`###`/`####`）决定了文档目录结构与渲染时的层级关系。在"Delete Formats"（`### Delete Formats`）这一节下，本应有四个并列的子节描述不同类型的行级删除：Deletion Vectors、Position Delete Files、Equality Delete Files、Delete File Stats，它们都应是 `####`（h4）级别作为 Delete Formats 的子节。

然而 "Deletion Vectors" 这一节的标题被错误地写成了 `### Deletion Vectors`（h3），与它的父节 "Delete Formats" 同级，而不是作为子节。这导致：
1. 文档目录中 "Deletion Vectors" 与 "Delete Formats" 平级，层级错乱；
2. "Deletion Vectors" 与其同属的 "Position Delete Files"（`####`）、"Equality Delete Files"（`####`）、"Delete File Stats"（`####`）不在同一层级，逻辑关系混乱。

本提交将 `### Deletion Vectors` 改为 `#### Deletion Vectors`，使其与兄弟节一致，恢复正确的文档层级。

## 如何达成设计目的

直接将 `format/spec.md` 中 `### Deletion Vectors` 这一行的前缀从 `###` 改为 `####`，即减少一个 `#`，使其从 h3 降为 h4，与同节的 Position Delete Files、Equality Delete Files、Delete File Stats 保持同级。改动仅 1 行（1 insertion, 1 deletion）。

## 修改详情

### `format/spec.md`

修改目的：修正 Deletion Vectors 节的标题层级。

工作逻辑：将第 1019 行（相对位置）的 `### Deletion Vectors` 改为 `#### Deletion Vectors`。修改后该节的层级关系为：
- `### Delete Formats`（h3，行级删除格式总节）
  - `#### Deletion Vectors`（h4，DV 子节，本提交修复）
  - `#### Position Delete Files`（h4，位置删除子节）
  - `#### Equality Delete Files`（h4，等值删除子节）
  - `#### Delete File Stats`（h4，删除文件统计子节）

无任何正文内容变更。

## 小结

- 成效：Deletion Vectors 节标题从 h3 降为 h4，与 Delete Formats 下的其它删除类型子节同级，文档目录与渲染层级恢复正常。
- 影响范围：仅 `format/spec.md` 1 行标题层级修改，无代码、无构建、无运行时影响。
- 回迁到 1.4.x 的注意事项：**视 1.4.x 是否包含 DV 章节而定**。Deletion Vectors 是 v3 特性。若 1.4.x 的 `format/spec.md` 已包含 Deletion Vectors 章节且存在同样的 `###` 错误层级，则应回迁此修复（1 行改动，零风险）。若 1.4.x 尚未引入 v3/DV 相关规范章节，则无需回迁。文档类修复不阻塞发布。
