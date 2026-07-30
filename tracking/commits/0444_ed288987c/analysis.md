# 提交 0444：Convert Hugo versioned docs to mkdocs format (#9591)

## 提交信息

- **序号**：0444
- **哈希**：ed288987cce5e2f8c3bea99bf903a548bcd3b29a
- **短哈希**：ed288987c
- **日期**：2024-02-01 12:55:49 -0800
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Convert Hugo versioned docs to mkdocs format (#9591)
- **PR/Issue**：#9591

## 总体目的

本提交把 Iceberg 网站的"版本化文档"（versioned docs）从 Hugo 静态站点生成器的格式整体迁移到 mkdocs 格式。这是一次纯文档基础设施切换，目的是统一文档构建工具链、简化 front matter 与链接语法、为后续版本化文档管理铺路。改动横跨 45 个文件，净增 432 行、净删 638 行（删除多于新增，因为剥离了大量 Hugo 专有的 front matter 字段和 shortcode 包装）。

Hugo 的文档体系依赖 `config.toml`/`_index.md`/`menu`/`weight`/`aliases`/`url` 等 front matter 字段来组织导航，并用 `{{< hint warning >}}...{{< /hint >}}` 这类 shortcode 表达告警/提示框，用 `{{% icebergVersion %}}` 做 shortcode 插值。mkdocs 不支持 shortcode，导航改为在 `mkdocs.yml` 里显式声明 `nav`，告警框改用 mkdocs-material 的 `!!! warning\n    ...` 语法，变量插值改为 `{{ icebergVersion }}`（Jinja2 风格）。同时 mkdocs 对相对链接有更严格的要求——同目录文件需要带 `.md` 后缀，跨目录链接也要显式写出目标文件，所以所有内部链接都被改写了。

目录布局也做了调整：原来散落在 `docs/` 根的 markdown 全部移到 `docs/docs/` 子目录下，图片资源移到 `docs/docs/assets/images/`，这样 `docs/` 顶层只放 `mkdocs.yml` 等构建配置，文档内容统一在 `docs/docs/` 下，便于版本化目录管理（不同版本可在 `docs/<version>/` 下并存）。少数文件在迁移时做了重命名以更符合 mkdocs 习惯：`_index.md`→`index.md`、`java-api.md`→`api.md`、`java-custom-catalog.md`→`custom-catalog.md`、`branching-and-tagging.md`→`branching.md`、`flink-getting-started.md`→`flink.md`。

## 如何达成设计目的

实现路径是机械但系统的批量改写：(1) 新建 `docs/mkdocs.yml`，用显式 `nav` 树定义全部页面层级（Tables / Spark / Flink / Integrations / API 等分组，外加指向外部站点的 Trino/Clickhouse/Presto 等引擎链接和 Javadoc/PyIceberg 链接）；(2) 用 `git mv` 把 35 个 markdown 从 `docs/` 移到 `docs/docs/`，5 个文件同时改名；(3) 对每个文件剥除 Hugo front matter（只保留 `title`），把 shortcode 改写成 mkdocs 语法，把内部链接补上 `.md` 后缀并修正相对路径，把 `{{% icebergVersion %}}` 改成 `{{ icebergVersion }}`；(4) 把 9 张图片资源新增到 `docs/docs/assets/images/`。下面按文件分组说明。

## 修改详情

### docs/mkdocs.yml（新增）

**修改目的**：定义 mkdocs 站点配置与导航结构，取代 Hugo 的 `config.toml` + front matter `menu` 体系。

**工作逻辑**：70 行的配置文件，`site_name: docs/latest`，启用 `search` 插件，`nav` 显式列出所有页面并按"Tables / Spark / Flink / hive / 外部引擎 / Integrations / API / Javadoc / PyIceberg"分组。外部引擎（Trino、Clickhouse、Presto、Dremio、Starrocks、Amazon Athena/EMR、Impala、Doris）直接给绝对 URL，Javadoc 指向 `../../javadoc/latest/`，PyIceberg 指向 `https://py.iceberg.apache.org/`。这是 mkdocs 的标准做法——导航完全在配置里声明，文件本身不需要 front matter 的 `menu`/`weight`。

### docs/docs/*.md（35 个文件的批量迁移与改写）

**修改目的**：把所有文档从 Hugo 格式转为 mkdocs 格式，并移入 `docs/docs/` 子目录。

**工作逻辑**（统一模式，适用于全部 35 个文件）：

1. **目录移动 + 部分改名**：`docs/<name>.md` → `docs/docs/<name>.md`。其中 5 个改名：`_index.md`→`index.md`、`java-api.md`→`api.md`、`java-custom-catalog.md`→`custom-catalog.md`、`branching-and-tagging.md`→`branching.md`、`flink-getting-started.md`→`flink.md`。改名是为了让 mkdocs 默认 URL 更简洁（`flink/` 而非 `flink-getting-started/`）并匹配 `mkdocs.yml` 里的 nav 条目。

2. **front matter 精简**：每个文件原本的 YAML front matter 包含 `title`、`url`、`aliases`、`menu.main.parent`、`menu.main.identifier`、`weight` 等 Hugo 专有字段。迁移后只保留 `title`，其余全部删除。例如 `spark-ddl.md` 原 front matter 有 9 行，迁移后只剩 `title: "DDL"`。

3. **内部链接改写**：Hugo 允许 `../spark-configuration` 这种不带后缀的相对链接（它会自动找 `.md`），mkdocs 要求显式 `.md`。由于所有文件现在同在 `docs/docs/` 目录，原本 `../spark-configuration` 变成 `spark-configuration.md`；指向 `docs/` 之外页面的链接如 `../../spec` 变成 `../../spec.md`、`../../community` 变成 `../../community.md`。锚点链接如 `../spark-configuration#catalog-configuration` 变成 `spark-configuration.md#catalog-configuration`。少数链接还修正了锚点名（如 hive.md 里 `#create-external-table` 改为更准确的 `#create-external-table-overlaying-an-existing-iceberg-table`）。

4. **shortcode → mkdocs 语法**：
   - Hugo 变量插值 `{{% icebergVersion %}}` → mkdocs/Jinja2 `{{ icebergVersion }}`（出现在 flink.md、api.md 等文件的 Javadoc 链接里，如 `../../../javadoc/{{% icebergVersion %}}/index.html` → `../../javadoc/{{ icebergVersion }}/index.html`，路径也因目录变化从 `../../../` 改为 `../../`）。
   - Hugo 告警 shortcode `{{< hint warning >}}...{{< /hint >}}` → mkdocs-material admonition `!!! warning\n    ...`；`{{< hint info >}}` → `!!! info`；`{{< hint danger >}}` → `!!! danger`。改写时把内容缩进 4 个空格放进 admonition 体。这种改写出现在 hive.md、spark-procedures.md、spark-writes.md 等文件中。

5. **少量文案微调**：个别文件在迁移时顺手调整了措辞，例如 hive.md 把 "With Hive version 4.0.0-alpha-2 and above, Iceberg integration when using HiveCatalog supports..." 改为 "The HiveCatalog supports the following additional features with Hive version 4.0.0-alpha-2 and above:"，把 "Honours" 改为 "Honors"（美式拼写）；flink.md 把指向 multi-engine-support 页面的链接从绝对 URL 改为相对路径 `../../multi-engine-support.md#apache-flink`。

代表文件示例：
- `docs/docs/index.md`（原 `docs/_index.md`）：首页，剥除 `menu: main`/`weight: 0`，所有功能链接补 `.md`（`evolution#schema-evolution` → `evolution.md#schema-evolution`），spec 链接 `../../spec` → `../../spec.md`。
- `docs/docs/hive.md`（原 `docs/hive.md`）：剥除 `url`/`weight`/`menu`，多处 `{{< hint warning/danger >}}` 改 admonition，`#create-external-table` 锚点补全，"Honours"→"Honors"。
- `docs/docs/spark-ddl.md`（原 `docs/spark-ddl.md`）：剥除 9 行 front matter，所有 `../spark-configuration` → `spark-configuration.md`、`../partitioning` → `partitioning.md`、`../configuration` → `configuration.md`。
- `docs/docs/api.md`（原 `docs/java-api.md`）：Javadoc 链接 `{{% icebergVersion %}}` → `{{ icebergVersion }}`，相对路径 `../../../javadoc` → `../../javadoc`，`../schemas` → `schemas.md`。
- `docs/docs/flink.md`（原 `docs/flink-getting-started.md`）：`{{% icebergVersion %}}` → `{{ icebergVersion }}`（多处 jar 下载链接），multi-engine-support 链接改为相对 `.md` 路径。
- `docs/docs/branching.md`（原 `docs/branching-and-tagging.md`）：这是 similarity index 86% 的文件，改动较多——除了常规 front matter/链接改写，还新增了图片引用 `![Historical Tags](assets/images/historical-snapshot-tag.png)` 等（图片资源同步新增到 `docs/docs/assets/images/`）。

### docs/docs/assets/images/*.png（9 张图片，新增）

**修改目的**：把文档引用的图片资源放到 mkdocs 新目录结构下。

**工作逻辑**：9 张 PNG（`audit-branch.png`、`historical-snapshot-tag.png`、`iceberg-in-place-metadata-migration.png`、`iceberg-migrateaction-step1/2/3.png`、`iceberg-snapshotaction-step1/2.png`、`partition-spec-evolution.png`）以二进制形式新增到 `docs/docs/assets/images/`。这些图片被 `branching.md`、`maintenance.md`、`table-migration.md` 等文件引用，迁移后图片路径调整为相对当前文件的 `assets/images/<name>.png`。从 `git show --stat` 的 `Bin 0 -> N bytes` 可以看出这些都是新增而非重命名（原 Hugo 站点的图片可能在另一处或由构建系统处理）。

## 小结

这是一次大规模但机械的文档基础设施迁移：把 35 篇 markdown 从 Hugo 格式（shortcode、`menu`/`weight` front matter、无后缀相对链接）转换为 mkdocs 格式（`!!! admonition`、显式 `mkdocs.yml` nav、带 `.md` 后缀的相对链接、`{{ var }}` 插值），文件统一移入 `docs/docs/` 子目录，5 个文件改名，9 张图片资源归位到 `docs/docs/assets/images/`。改动本身不涉及文档内容语义变更，主要是工具链切换和路径修正，为后续 mkdocs 版本化文档管理奠基。
