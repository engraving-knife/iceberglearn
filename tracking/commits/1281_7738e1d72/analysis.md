# 提交 1281：Spec: Fix table of content generation (#11067)

## 提交信息

- **序号**：1281 / 4088
- **哈希**：7738e1d7228474e36f661cfa1a15a2e8f8410bcd
- **短哈希**：7738e1d72
- **日期**：2024-10-25（Sat Oct 26 02:07:05 2024 +0530）
- **作者**：Ajantha Bhat <ajanthabhat@gmail.com>
- **提交说明**：Spec: Fix table of content generation (#11067)
- **PR/Issue**：#11067

## 总体目的

`format/spec.md` 是 Iceberg 表格式规范的唯一权威文档，在文档站点（基于 Hugo 的 `iceberg-docs`）上以单页形式渲染，并通过 front matter 中 `toc: true` 自动生成"目录（Table of Contents）"侧边栏。

但该文档此前的 Markdown 标题层级（heading hierarchy）混乱，导致 TOC 生成不正确：

1. **多个 H1**：文档同时存在 `# Iceberg Table Spec`（页面顶部）和 `# Specification`（中段）两个 H1。Hugo/Docsy 的 TOC 一般假设页面只有 1 个 H1（即页面 title），多 H1 会破坏目录的根节点；
2. **跳级**：多处从 H2 直接跳到 H4（如 `## Format Versioning` 后紧跟 `#### Version 1`、`#### Version 2`、`#### Version 3`），跳过 H3；也有从 H1 跳到 H3（`# Specification` → `### Terms`）的情况。跳级会让 TOC 树出现"缺失中间层"的孤儿节点；
3. **同层级的兄弟节实际不属于同一逻辑父节**：例如 `## Schemas and Data Types`、`## Partitioning`、`## Sorting`、`## Manifests`、`## Snapshots`、`## Table Metadata` 等都是 H2，但语义上它们应该统属于 `## Specification` 之下，而非和 `## Format Versioning`/`## Goals`/`## Overview` 同级。

结果：渲染出的 TOC 既不完整也不正确，读者难以在长文档中导航。本提交通过调整 ~47 处标题的层级（`#`/`##`/`###`/`####`/`#####` 之间的升迁或降级），让整篇 spec.md 形成单一 H1 下的、不跳级的、符合逻辑嵌套的标题树，从而生成正确的目录。

## 如何达成设计目的

按"单一 H1 + 不跳级 + 逻辑嵌套"的原则重新编排标题层级：

1. **保留唯一 H1**：`# Iceberg Table Spec` 仍是页面唯一 H1（页面 title）。把原来的 `# Specification` 降为 `## Specification`，使其成为 H1 下的一个章节。
2. **修正跳级**：把原 H4 直接挂在 H2 下面的子标题（如 `#### Version 1/2/3`、`#### Optimistic Concurrency` 等）升为 H3，使其成为 `## Format Versioning`/`## Overview` 的直接子节；
3. **修正逻辑嵌套**：把原 `## Schemas and Data Types`、`## Partitioning`、`## Sorting`、`## Manifests`、`## Snapshots`、`## Table Metadata`、`## Delete Formats` 等降为 H3，作为 `## Specification` 的子节；它们的下级子标题相应从 H3 降为 H4、H4 降为 H5；
4. 全篇保持每级只差 1 的层级关系，让 Docsy/Hugo 的 TOC 生成器能正确递归构造目录树。

整个修改仅调整标题层级（94 处 `#` 数量变化：47 行 `+`、47 行 `-`），不改动正文内容。

## 修改详情

### `format/spec.md`（修改，+47 -47 行）

**修改目的**：修正 spec.md 的标题层级，使 TOC 正确生成。

**改动模式**（按层级调整方向分类，共 ~47 处标题）：

1. **H1 → H2**（1 处）：
   - `# Specification` → `## Specification`
   - 使整篇只有 `# Iceberg Table Spec` 一个 H1。

2. **H2 → H3**（多处，逻辑大章节降级，归入 `## Specification` 之下）：
   - `## Schemas and Data Types` → `### Schemas and Data Types`
   - `## Partitioning` → `### Partitioning`
   - `## Sorting` → `### Sorting`
   - `## Manifests` → `### Manifests`
   - `## Snapshots` → `### Snapshots`
   - `## Table Metadata` → `### Table Metadata`
   - `## Delete Formats` → `### Delete Formats`
   - 等等

3. **H4 → H3**（多处，原"跳级子节"升回 H3，作为 H2 章节的直接子节）：
   - `#### Version 1: Analytic Data Tables` → `### Version 1: Analytic Data Tables`
   - `#### Version 2: Row-level Deletes` → `### Version 2: Row-level Deletes`
   - `#### Version 3: Extended Types and Capabilities` → `### Version 3: Extended Types and Capabilities`
   - `#### Optimistic Concurrency` → `### Optimistic Concurrency`
   - `#### Sequence Numbers` → `### Sequence Numbers`
   - `#### Row-level Deletes` → `### Row-level Deletes`
   - `#### File System Operations` → `### File System Operations`
   - 等等

4. **H3 → H4**（多处，原 H3 子节随父节降级，或修正跳级）：
   - `### Terms` → `#### Terms`
   - `### Writer requirements` → `#### Writer requirements`
   - `### Writing data files` → `#### Writing data files`
   - `### Nested Types` → `#### Nested Types`
   - `### Primitive Types` → `#### Primitive Types`
   - `### Default values` → `#### Default values`
   - `### Schema Evolution` → `#### Schema Evolution`
   - `### Identifier Field IDs` → `#### Identifier Field IDs`
   - `### Reserved Field IDs` → `#### Reserved Field IDs`
   - `### Row Lineage` → `#### Row Lineage`
   - `### Partition Transforms` → `#### Partition Transforms`
   - `### Bucket Transform Details` → `#### Bucket Transform Details`
   - `### Truncate Transform Details` → `#### Truncate Transform Details`
   - `### Partition Evolution` → `#### Partition Evolution`
   - `### Manifest Entry Fields` → `#### Manifest Entry Fields`
   - `### Sequence Number Inheritance` → `#### Sequence Number Inheritance`
   - `### First Row ID Inheritance` → `#### First Row ID Inheritance`
   - `### Snapshot Row IDs` → `#### Snapshot Row IDs`
   - `### Table Metadata Fields` → `#### Table Metadata Fields`
   - `### Table Statistics` → `#### Table Statistics`
   - `### Partition Statistics` → `#### Partition Statistics`
   - `### File System Tables` → `#### File System Tables`
   - `### Metastore Tables` → `#### Metastore Tables`
   - `### Position Delete Files` → `#### Position Delete Files`
   - `### Equality Delete Files` → `#### Equality Delete Files`
   - `### Delete File Stats` → `#### Delete File Stats`
   - 等等

5. **H4 → H5 / H3 → H5**（深层子节相应降级）：
   - `#### Column Projection` → `##### Column Projection`
   - `#### Row lineage assignment` → `##### Row lineage assignment`
   - `#### Row lineage example` → `##### Row lineage example`
   - `### Enabling Row Lineage for Non-empty Tables` → `##### Enabling Row Lineage for Non-empty Tables`（原本是 H3 跳级在 H4 子节里，统一降为 H5 与其它 sibling 一致）
   - `#### Partition Statistics File` → `##### Partition Statistics File`
   - 等等

整体效果是：每个内部节点的子标题层级恰好比自身少 1，TOC 生成器据此可正确递归构造目录树。

## 小结

- **成效**：spec.md 的标题层级修正为单一 H1 下的一致层级树，消除跳级与多 H1，使 Hugo/Docsy 文档站点的 TOC 侧边栏能正确生成；正文内容零改动，仅调整 47 处标题的 `#` 数量，向后兼容（锚点 link 形如 `#version-2` 仍可用，因为 Docsy 的 anchor slug 不依赖层级）。
- **影响范围**：仅 `format/spec.md` 一个文档文件，不影响任何代码、构建或运行时行为。
- **回迁到 1.4.x 的注意事项**：纯文档类变更，回迁零风险。需注意：
  1. 若 1.4.x 上 spec.md 已有本地修改（如新增章节、修订措辞），回迁本提交时需手动确认每个被调整层级的标题在 1.4.x 上是否仍存在/仍同名，避免 patch conflict；
  2. 锚点稳定性：本提交不改任何标题文字，只改 `#` 数量，Docsy 生成的 slug 来自标题文字，因此外部链接（如 `spec.md#version-2`、`#schemas-and-data-types`）的锚点不会变化，可放心回迁；
  3. 若 1.4.x 上的文档站点用了不同的 TOC 生成器（如 mkdocs），层级一致性的要求仍然成立，本修复同样有效。
