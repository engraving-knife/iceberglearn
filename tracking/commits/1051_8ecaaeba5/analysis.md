# 提交 1051：Docs: Add Trademark symbol where appropriate (#10921)

## 提交信息

- **序号**：1051 / 4088
- **哈希**：8ecaaeba5aec612dfb6f5d8449ca2a8d2452ce86
- **短哈希**：8ecaaeba5
- **日期**：2024-08-12（Mon Aug 12 13:51:34 2024 -0500）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Docs: Add Trademark symbol where appropriate (#10921)
- **PR/Issue**：#10921

## 总体目的

Apache 软件基金会（ASF）对旗下项目商标有明确要求：项目名称首次或显著出现时应标注商标符号 ™（项目处于孵化或正式商标注册阶段用 ™，已注册用 ®）。Apache Iceberg 此前在网站多处使用了 "Apache Iceberg"、"Iceberg" 等名称但未标注 ™，不符合 ASF 商标政策，也存在被第三方不当使用的风险。

本提交的目标是在 Iceberg 官方网站（基于 MkDocs Material 构建，站点源码位于 `site/`）的关键位置补上商标符号 ™，使项目名称在显著位置（站点名、首页标题、快速开始文档）以 "Apache Iceberg™" 形式出现，同时顺手清理首页 header 模板中冗余的标题逻辑。

## 如何达成设计目的

整体思路是在站点配置与文档模板中，把项目名称的显著出现统一改为带 ™ 的形式：

1. **站点配置**：把 `mkdocs.yml` 的 `site_name` 设为 `"Apache Iceberg™"`，这样 MkDocs 在所有页面的 header/footer 等位置自动渲染带 ™ 的站点名。
2. **首页模板**：把 `home.html` 中标题与"什么是 Iceberg"标题加上 ™。
3. **header 模板**：把原本复杂的"取页面标题"逻辑简化为直接渲染 `config.site_name`（即统一显示 "Apache Iceberg™"），既符合商标要求也简化了模板。
4. **快速开始文档**：把 `hive-quickstart.md` 与 `spark-quickstart.md` 的开头从单独的 `## xxx Quickstart` 二级标题改为正文段落，并在正文中以 "Apache Iceberg™ using Apache Hive™" / "Apache Iceberg™ using Apache Spark™" 形式表达，使商标符号出现在文档显著位置。

## 修改详情

### `site/mkdocs.yml`

**修改目的**：把站点名称设为带 ™ 的形式，让所有页面 header/footer 自动渲染商标符号。

**工作逻辑**：将 `site_name: Apache Iceberg` 改为 `site_name: "Apache Iceberg™"`。MkDocs 会用 `site_name` 作为站点标题在各页面头部、浏览器标题等位置渲染。

### `site/overrides/home.html`

**修改目的**：在首页 hero 区与正文标题中加上 ™ 商标符号。

**工作逻辑**：

1. 把首页主标题 `<h1>Apache Iceberg</h1>` 改为 `<h1>Apache Iceberg™</h1>`。
2. 把正文标题 `<h2>What is Iceberg?</h2>` 改为 `<h2>What is Apache Iceberg™?</h2>`（同时把 "Iceberg" 完整为 "Apache Iceberg"）。

### `site/overrides/partials/header.html`

**修改目的**：简化 header 标题渲染逻辑，统一显示带 ™ 的站点名。

**工作逻辑**：原 header 模板里两个 `md-header__topic` span 分别渲染空字符串与"当前页面标题"（取自 `page.meta.title` 或 `page.title`）。改造后两个 span 都渲染 `{{ config.site_name }}`，即统一显示 "Apache Iceberg™"。这样去掉了"取页面标题"的复杂条件表达式，header 在所有页面一致显示站点名，更简洁也符合商标一致性要求。

### `site/docs/hive-quickstart.md`

**修改目的**：在 Hive 快速开始文档开头以带 ™ 的形式呈现项目名。

**工作逻辑**：删除原 `## Hive and Iceberg Quickstart` 二级标题，把正文首句改为 "This guide will get you up and running with Apache Iceberg™ using Apache Hive™, including sample code to ..."。即不再用二级标题重复页面标题，而是把带商标符号的项目名融入引导段落。

### `site/docs/spark-quickstart.md`

**修改目的**：在 Spark 快速开始文档开头以带 ™ 的形式呈现项目名。

**工作逻辑**：删除原 `## Spark and Iceberg Quickstart` 二级标题，把正文首句改为 "This guide will get you up and running with Apache Iceberg™ using Apache Spark™, including sample code to ..."。与 Hive 快速开始同样处理。

## 小结

- **成效**：在 Iceberg 官方网站的关键位置（站点名、首页标题、快速开始文档、header 模板）补上了 ™ 商标符号，使项目名称以 "Apache Iceberg™" 形式显著出现，符合 ASF 商标政策；同时简化了 header 模板的标题渲染逻辑。
- **影响范围**：仅 `site/` 目录下 5 个文档/模板文件（`mkdocs.yml`、`overrides/home.html`、`overrides/partials/header.html`、`docs/hive-quickstart.md`、`docs/spark-quickstart.md`），共 7 行新增、15 行删除，不触及任何代码。
- **回迁到 1.4.x 的注意事项**：本提交仅修改网站文档，**不影响代码与运行时行为**，回迁风险极低。1.4.x 若维护独立文档分支可按需回迁以保持商标合规；若 1.4.x 不单独维护文档站点，则无需回迁（文档以 main 为准）。
