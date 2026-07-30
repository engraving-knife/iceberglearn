# 提交 2608：[docs] Move third-party integrations to root level of left-hand nav, add more catalogs (#13753)

## 提交信息

- **序号**：2608 / 4088
- **哈希**：76f21d8ac7c2219b3bd59c22113611b3ceef612d
- **短哈希**：76f21d8ac
- **日期**：2025-09-08 13:54:31 +0200
- **作者**：Robin Moffatt
- **提交说明**：[docs] Move third-party integrations to root level of left-hand nav, add more catalogs (#13753)
- **PR/Issue**：#13753

## 总体目的

本次提交对 Iceberg 官网的文档导航结构进行了重组，将第三方集成文档从 Java 文档的子目录中提升到左侧导航栏的根级别，使其更容易被发现和访问。

背景是 Iceberg 的第三方集成文档（如 Amoro、BladePipe、Daft、RisingWave 等）之前被嵌套在 Java 文档目录下，用户不易发现。随着 Iceberg 生态系统中第三方工具集成的增多，将这些文档提升为顶级导航项可以更好地展示 Iceberg 生态的丰富性，方便用户快速找到所需工具的集成文档。

此外，该提交还调整了文档的目录结构，将集成文档从 `docs/docs/` 移动到 `site/docs/integrations/`，并更新了 mkdocs 配置和导航配置。

## 如何达成设计目的

1. 将第三方集成文档文件从 `docs/docs/` 目录移动到 `site/docs/integrations/` 目录。
2. 更新 `site/mkdocs.yml` 配置，移除旧的文档路径引用。
3. 更新 `site/nav.yml` 导航配置，添加新的顶级 "Integrations" 导航条目。
4. 更新 `docs/mkdocs.yml` 配置（nightly 文档版本），调整文档路径和导航结构。
5. 添加重定向规则确保旧链接仍然有效。
6. 将 AWS、Dell、Nessie 集成保留在 Java 文档列表中（因为它们更偏向 Java API 集成而非独立的第三方工具）。

## 修改详情

### `docs/mkdocs.yml` (+2/-31 lines)

**修改目的**：更新 nightly 版本文档的 mkdocs 配置。

**工作逻辑**：移除了对已迁移文档的路径引用，调整文档目录结构配置。将 concepts 移回 nightly，将 integrations 移出 nightly。

### `site/docs/integrations/amoro.md` (renamed from `docs/docs/amoro.md`)

**修改目的**：移动 Amoro 集成文档到新目录。

**工作逻辑**：文件内容不变，仅从 `docs/docs/` 移动到 `site/docs/integrations/`。

### `site/docs/integrations/bladepipe.md` (renamed from `docs/docs/bladepipe.md`)

**修改目的**：移动 BladePipe 集成文档到新目录。

### `site/docs/integrations/daft.md` (renamed from `docs/docs/daft.md`)

**修改目的**：移动 Daft 集成文档到新目录。

### `site/docs/integrations/risingwave.md` (renamed from `docs/docs/risingwave.md`)

**修改目的**：移动 RisingWave 集成文档到新目录。

### `site/mkdocs.yml` (+10/-2 lines)

**修改目的**：更新正式版文档的 mkdocs 配置以引用新的文档路径。

**工作逻辑**：添加对 `site/docs/integrations/` 目录的引用，调整文档路径配置。

### `site/nav.yml` (+36/-0 lines)

**修改目的**：添加顶级 Integrations 导航条目。

**工作逻辑**：在导航配置中新增 Integrations 部分，列出所有第三方集成文档的导航链接。这样用户在官网左侧导航栏可以直接看到并访问第三方集成文档，而不需要深入 Java 文档子目录。

## 总结

这是一个文档信息架构优化提交，通过将第三方集成文档提升到导航根级别，改善了文档的可发现性和用户体验。这反映了 Iceberg 生态系统中第三方工具集成的增长，以及社区对这些集成文档可见性的重视。文件移动和导航重组保持了文档内容的完整性，同时优化了组织结构。
