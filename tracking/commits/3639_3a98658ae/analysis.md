# 提交 3639：Docs: Move catalog properties to catalog section (#15848)

## 提交信息

- **序号**：3639 / 4088
- **哈希**：3a98658aef480559ee3d0d72c68d6ed27a32ecb1
- **短哈希**：3a98658ae
- **日期**：2026-05-04 16:37:33 +0200
- **作者**：gaborkaszab
- **提交说明**：Docs: Move catalog properties to catalog section (#15848)
- **PR/Issue**：#15848

## 总体目的

这个提交将文档中"Catalog properties"（Catalog 属性）相关内容从 `configuration.md`（通用配置页）迁移到独立的 `catalog-properties.md`（Catalog 属性页），并将其归入文档导航的 "Catalogs"（Catalog）分区下。

此前的文档结构将 catalog 属性放在通用配置页面中，但随着 catalog 属性内容增多（包括通用属性、REST 认证属性、OAuth2 属性、Google 认证属性、锁属性、Hadoop 配置等），放在 configuration 页面使页面过于臃肿，且与"Catalog"主题在导航上分离，不利于用户查找。将其独立成页并归入 Catalogs 分区，使文档结构更清晰、更符合内容主题。

## 如何达成设计目的

1. 新建 `docs/docs/catalog-properties.md`，将原 `configuration.md` 中的 Catalog properties 章节（含所有子节）整体迁移过来。
2. 从 `configuration.md` 删除对应的 Catalog properties 章节。
3. 在 `docs/mkdocs.yml` 的导航 "Catalogs" 分区下新增 `Catalog properties: catalog-properties.md` 条目。
4. 更新所有引用旧锚点 `configuration.md#catalog-properties` 的文档链接，改为指向新的 `catalog-properties.md`。

## 修改详情

### `docs/docs/catalog-properties.md` (+145 lines, new)

**修改目的**：新建独立的 Catalog 属性文档页。

**工作逻辑**：包含原 configuration.md 中的全部 catalog 属性内容，分为以下小节：
- Common properties（通用属性表：catalog-impl、io-impl、warehouse、uri、clients、cache-enabled 等）
- REST Catalog auth properties（REST 认证属性，含 REST auth、OAuth2 auth、Google auth 子节）
- Lock catalog properties（锁属性：lock-impl、lock.table、lock.acquire-interval-ms 等）
- Hadoop configuration（HadoopTables 锁配置、Hive Metastore 配置）

### `docs/docs/configuration.md` (+0/-124 lines)

**修改目的**：从通用配置页移除 Catalog properties 章节。

**工作逻辑**：删除了从 "## Catalog properties" 开始到文件末尾的全部内容（约 124 行），使 configuration.md 仅保留表属性相关配置。

### `docs/mkdocs.yml` (+1 line)

**修改目的**：在导航中加入新的 Catalog properties 页面。

**工作逻辑**：在 "Catalogs" 分区顶部新增条目：
```yaml
- Catalogs:
    - Catalog properties: catalog-properties.md
    - AWS Glue: aws/#glue-catalog
```

### `docs/docs/aws.md` (+1/-1 line)

**修改目的**：更新锁属性链接。

**工作逻辑**：将 `[Lock catalog properties](configuration.md#lock-catalog-properties)` 改为 `[Lock catalog properties](catalog-properties.md#lock-catalog-properties)`。

### `docs/docs/custom-catalog.md` (+2/-2 lines)

**修改目的**：更新两处 Catalog properties 引用链接。

**工作逻辑**：将 `(configuration.md#catalog-properties)` 改为 `(catalog-properties.md)`。

### `docs/docs/java-api-quickstart.md` (+1/-1 line)

**修改目的**：更新 Catalog properties 引用链接。

**工作逻辑**：将 `(configuration.md#catalog-properties)` 改为 `(catalog-properties.md)`。

### `docs/docs/metrics-reporting.md` (+1/-1 line)

**修改目的**：更新 catalog property 引用链接。

**工作逻辑**：将 `(configuration.md#catalog-properties)` 改为 `(catalog-properties.md)`。

### `docs/docs/spark-configuration.md` (+1/-1 line)

**修改目的**：更新 catalog configuration 引用链接。

**工作逻辑**：将 `(configuration.md#catalog-properties)` 改为 `(catalog-properties.md)`。

## 总结

这是一个纯文档重构提交，将 Catalog 属性内容从通用配置页独立为专门页面，并归入 Catalogs 导航分区。此举改善了文档的信息架构，使 catalog 相关配置更易于发现和浏览。同时更新了 5 个文档文件中的交叉引用链接，确保链接有效性。
