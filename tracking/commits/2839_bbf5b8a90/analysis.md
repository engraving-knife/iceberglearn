# 提交 2839：Docs: add Delta Lake Migration to nav (fix #14309) (#14323)

## 提交信息

- **序号**：2839 / 4088
- **哈希**：bbf5b8a9060932931275160d262af314c5c1f336
- **短哈希**：bbf5b8a90
- **日期**：2025-11-06 09:58:07 -0800
- **作者**：Gea Linggar Galih
- **提交说明**：Docs: add Delta Lake Migration to nav (fix #14309) (#14323)
- **PR/Issue**：#14323（修复 #14309）

## 总体目的

Iceberg 文档中已经存在表迁移相关页面（如 `table-migration.md`、`hive-migration.md`、`delta-lake-migration.md`），但 `docs/mkdocs.yml` 的导航（`nav`）中并未包含"Migration"分组，导致用户在文档站点上无法通过侧边栏导航找到这些迁移指南，尤其是新加入的 Delta Lake 迁移页面。Issue #14309 报告了这一缺失。

该提交在 `mkdocs.yml` 的 `nav` 中新增一个"Migration"分组，把 Overview、Hive Migration、Delta Lake Migration 三个页面纳入导航，修复导航缺失问题。

## 如何达成设计目的

在 `docs/mkdocs.yml` 的 `nav` 列表中，在"Apache Hive"条目之后、"Catalogs"分组之前插入：
```yaml
  - Migration:
      - Overview: table-migration.md
      - Hive Migration: hive-migration.md
      - Delta Lake Migration: delta-lake-migration.md
```
这样侧边栏会显示"Migration"一级菜单，展开后有三项，分别指向已有的三个迁移文档页面。无需新增页面内容，只是把已有页面接入导航。

## 修改详情

### `docs/mkdocs.yml` (+4/-0 lines)

**修改目的**：把迁移相关文档接入站点导航。

**工作逻辑**：在 `nav` 中 `Apache Hive: hive.md` 之后新增 `Migration` 分组，包含 `Overview: table-migration.md`、`Hive Migration: hive-migration.md`、`Delta Lake Migration: delta-lake-migration.md` 三项。位置选在引擎章节之后、Catalogs 之前，符合"先讲引擎再讲迁移到 Iceberg"的文档阅读流。

## 总结

该提交是纯导航配置修复，在 `docs/mkdocs.yml` 中新增 Migration 分组，把已有的 table/hive/delta-lake 三个迁移文档纳入侧边栏导航，修复 Issue #14309 报告的 Delta Lake Migration 等页面无法从导航访问的问题。无内容性改动。
