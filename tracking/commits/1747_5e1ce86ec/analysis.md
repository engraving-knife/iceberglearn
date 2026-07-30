# 提交 1747：Docs: Refactor site navigation bar (#12289)

## 提交信息

- **序号**：1747 / 4088
- **哈希**：5e1ce86ecaeeb7bf44c440bfe0000ccb80dc9c85
- **短哈希**：5e1ce86ec
- **日期**：2025-02-18 14:21:17 -0800
- **作者**：Manu Zhang
- **提交说明**：Docs: Refactor site navigation bar (#12289)
- **PR/Issue**：#12289

## 总体目的

Iceberg 官方网站的导航栏此前将规范（Spec）相关的链接（如 REST Catalog Spec、Table Spec、View Spec 等）和 Terms 页面放在"Project"导航分组下，将 Catalog 概念文档放在"Concepts"导航分组下。这种组织方式不够清晰——规范文档是 Iceberg 的核心技术文档，不应混在项目信息（如社区、贡献指南等）中；同时"Concepts"分组下只有一个 Catalogs 页面，显得过于单薄。

本提交的目标是重组网站导航结构，将规范相关内容独立为一个新的"Specification"导航分组，将 Catalog 文档合并到 Terms 页面中，使网站信息架构更加清晰合理。

## 如何达成设计目的

提交通过以下修改完成导航重组：

1. **删除独立的 Catalog 文档页**：删除 `site/docs/concepts/catalog.md` 文件，将其内容合并到 Terms 页面中。
2. **合并 Catalog 内容到 Terms 页面**：将 Catalog 的完整文档内容添加到 `site/docs/terms.md` 的开头，作为 Terms 页面的第一个主要章节。同时将 Terms 页面中各术语条目的标题级别从 `###`（h3）提升为 `##`（h2），使其与新增的 Catalog 章节保持一致的层级结构。
3. **重组导航配置**：在 `site/nav.yml` 中创建新的"Specification"导航分组，将规范相关链接（Terms、REST Catalog Spec、Table Spec、View Spec、Puffin Spec、AES GCM Stream Spec、Implementation status）从"Project"分组移到新分组中。删除"Concepts"分组。

## 修改详情

### `site/docs/concepts/catalog.md`（删除, -49 lines）

**修改目的**：移除独立的 Catalog 文档页面，其内容将合并到 Terms 页面。

**工作逻辑**：该文件被整体删除。原文件包含 Iceberg Catalog 的概述、实现类型（REST、Hive Metastore、JDBC、Nessie）和 REST Catalog 解耦等内容。

### `site/docs/terms.md`（修改, +38/-6 lines）

**修改目的**：将 Catalog 文档内容合并到 Terms 页面，并统一标题层级。

**工作逻辑**：
- 在 Terms 页面开头新增 `## Catalog` 章节，包含原 `catalog.md` 的全部内容（Overview、Catalog Implementations、Decoupling Using the REST Catalog 三个子章节）。
- 将原有术语条目的标题级别从 `###`（h3）提升为 `##`（h2），包括：Snapshot、Manifest list、Manifest file、Partition spec、Partition tuple、Snapshot log (history table)。这使所有术语条目与新增的 Catalog 章节处于相同的标题层级。

### `site/nav.yml`（修改, +11/-8 lines）

**修改目的**：重组网站导航栏结构。

**工作逻辑**：
- 从"Project"分组中移除以下条目：REST Catalog Spec、Table Spec、View spec、Puffin spec、AES GCM Stream spec、Implementation status、Terms。
- 删除"Concepts"分组及其下的"Catalogs"条目。
- 新增"Specification"导航分组，包含以下条目（按顺序）：
  - Terms: `terms.md`
  - REST Catalog Spec（外部链接）
  - Table Spec: `spec.md`
  - View spec: `view-spec.md`
  - Puffin spec: `puffin-spec.md`
  - AES GCM Stream spec: `gcm-stream-spec.md`
  - Implementation status: `status.md`

## 小结

- **成效**：成功重组了网站导航栏，将规范相关文档独立为"Specification"分组，将 Catalog 文档合并到 Terms 页面，使网站信息架构更加清晰。删除了单薄的"Concepts"分组。
- **影响范围**：仅涉及网站文档结构和导航配置，不影响任何代码功能。Catalog 文档的 URL 从 `/concepts/catalog/` 变为 `/terms/#catalog`，可能影响外部链接。
- **回迁到 1.4.x 的注意事项**：此提交为文档结构重组，回迁风险低。需注意 1.4.x 分支的网站文档结构是否与 main 一致。如果有外部链接指向旧的 `/concepts/catalog/` 路径，回迁后可能需要设置重定向。建议回迁以保持文档结构一致。
