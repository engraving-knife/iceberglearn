# 提交 0848：docs: Introduce variable for setting the Flink version (#10463)

## 提交信息
- **序号**：0848 / 4088
- **哈希**：2862047771da068fe11745064f78d44f39ca9d05
- **短哈希**：286204777
- **日期**：2024-06-18（Tue Jun 18 15:36:11 2024 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：docs: Introduce variable for setting the Flink version (#10463)
- **PR/Issue**：#10463

## 总体目的

本提交为 Iceberg 网站文档（基于 MkDocs）引入两个用于设置 Flink 版本的全局变量 `flinkVersion` 与 `flinkVersionMajor`，并将 Flink 相关文档中散落的硬编码 Flink 版本字符串（`1.16`、`1.16.2`、`1.13` 等）替换为 MkDocs 模板变量引用 `{{ flinkVersion }}` 与 `{{ flinkVersionMajor }}`。

在此修改之前，Iceberg 文档默认参考的 Flink 版本是 `1.16.2`，散落在多个 `docs/docs/flink*.md` 文件中，包括 bash 命令的版本号、Maven 仓库 URL 路径段（如 `iceberg-flink-runtime-1.16`）、Flink 官方文档链接（如 `flink-docs-release-1.16`）以及 Python 包安装命令（`pip install apache-flink==1.16.2`）。一旦需要升级文档参考的 Flink 版本（例如升级到 1.19.0），需要在多个文件多处手动修改，容易遗漏且不一致。

本次提交将 Flink 版本号集中到 `site/mkdocs.yml` 的 `extra` 字段（`flinkVersion: '1.19.0'`、`flinkVersionMajor: '1.19'`），后续升级时只需修改 mkdocs.yml 一处即可同步至所有 Flink 相关文档。同时附带将部分 `flink-docs-stable`、`flink-docs-master` 形式的链接统一切换为 `flink-docs-release-{{ flinkVersionMajor }}`，确保文档链接绑定到具体的大版本（1.19）发布文档，提升链接稳定性与可读性。

总体上，这是一个文档基础设施类的重构提交，目的在于提升文档版本管理的可维护性，并将文档参考的 Flink 版本从过时的 1.16.2 升级到当时较新的 1.19.0。

## 如何达成设计目的

提交分两步完成：
1. 在 `site/mkdocs.yml` 的 `extra:` 段落下添加 `flinkVersion: '1.19.0'` 与 `flinkVersionMajor: '1.19'` 两个键。MkDocs 的 `extra` 字段允许自定义任意全局变量，并通过 `{{ varName }}` 语法在 Markdown 页面中插值替换。
2. 在四个 Flink 相关文档（`docs/docs/flink.md`、`flink-connector.md`、`flink-ddl.md`、`flink-queries.md`）中，将所有硬编码 Flink 版本号或链接路径段替换为对应变量引用：
   - bash 代码块中的 `FLINK_VERSION=1.16.2` 改为 `FLINK_VERSION={{ flinkVersion }}`
   - URL 路径中的 `flink-docs-release-1.16` 改为 `flink-docs-release-{{ flinkVersionMajor }}`
   - Maven 仓库 jar 路径 `iceberg-flink-runtime-1.16` 改为 `iceberg-flink-runtime-{{ flinkVersionMajor }}`
   - Python 安装命令 `pip install apache-flink==1.16.2` 改为 `pip install apache-flink=={{ flinkVersion }}`
   - 同时将零星出现的 `flink-docs-stable` 与 `flink-docs-master` 链接统一改为 `flink-docs-release-{{ flinkVersionMajor }}`，与文档主体保持一致
3. 在 `flink.md` 中还顺手删除了一行不再需要的 `<!-- markdown-link-check-disable-next-line -->` HTML 注释（原用于屏蔽链接检查的旧链接现已改用变量并指向稳定路径）。

MkDocs 在构建站点时会进行变量插值，渲染出的 HTML 页面中所有 `{{ flinkVersion }}` 与 `{{ flinkVersionMajor }}` 会被替换为 `1.19.0` 与 `1.19`，从而保证读者看到的命令、URL 与版本号始终与 mkdocs.yml 中声明的一致。

## 修改详情

### `site/mkdocs.yml`
**修改目的**：声明 `flinkVersion` 与 `flinkVersionMajor` 两个全局变量。

**工作逻辑**：
- 在 `extra:` 字段下新增两行：
  ```yaml
  flinkVersion: '1.19.0'
  flinkVersionMajor: '1.19'
  ```
- `flinkVersion`（完整版本号 `1.19.0`）用于 bash 命令、Maven jar 完整路径、`pip install apache-flink==...` 等需要精确到补丁号的场景。
- `flinkVersionMajor`（仅主次版本号 `1.19`）用于 Flink 官方文档链接路径段、Maven 仓库中按主版本号分目录的路径段（如 `iceberg-flink-runtime-1.19/`）等仅需大版本号的场景。
- 同时也保留了原 `icebergVersion` 与 `nessieVersion`，遵循同样的变量化模式。

### `docs/docs/flink.md`
**修改目的**：将 Flink 文档主页面中所有硬编码 Flink 版本字符串替换为变量引用。

**工作逻辑**：
- `FLINK_VERSION=1.16.2` → `FLINK_VERSION={{ flinkVersion }}`
- Flink SQL Client 链接：`flink-docs-stable` → `flink-docs-release-{{ flinkVersionMajor }}`
- 文字描述 "Flink 1.16 bundled with Scala 2.12" → "Flink {{ flinkVersionMajor }} bundled with Scala 2.12"
- Maven jar 下载链接中 `iceberg-flink-runtime-1.16` 与 `flink-sql-connector-hive-2.3.9_2.12/1.16.2/` 路径段改为变量
- Hive bundled jar URL 路径 `flink-sql-connector-hive-2.3.9_2.12-1.16.2.jar` 改为 `flink-sql-connector-hive-2.3.9_2.12-{{ flinkVersion }}.jar`
- `pip install apache-flink==1.16.2` → `pip install apache-flink=={{ flinkVersion }}`
- pyflink 中 `iceberg-flink-runtime-1.16-{{ icebergVersion }}.jar` → `iceberg-flink-runtime-{{ flinkVersionMajor }}-{{ icebergVersion }}.jar`
- 多处 Flink 官方文档链接 `flink-docs-release-1.16` → `flink-docs-release-{{ flinkVersionMajor }}`
- 删除一行 `<!-- markdown-link-check-disable-next-line -->` HTML 注释
- bash 代码块中新增 `FLINK_VERSION_MAJOR={{ flinkVersionMajor }}` 变量声明，供后续 `wget` 命令拼路径使用

### `docs/docs/flink-connector.md`
**修改目的**：将 Flink Connector 文档中两处 Flink 官方文档链接（原指向 `flink-docs-release-1.13`）替换为变量引用。

**工作逻辑**：
- 原 `https://nightlies.apache.org/flink/flink-docs-release-1.13/docs/connectors/table/overview/` → `.../flink-docs-release-{{ flinkVersionMajor }}/docs/connectors/table/overview/`
- 原 `https://ci.apache.org/projects/flink/flink-docs-release-1.13/docs/dev/table/catalogs/#genericinmemorycatalog` → `.../flink-docs-release-{{ flinkVersionMajor }}/docs/dev/table/catalogs/#genericinmemorycatalog`
- 注意：这两处原本写死的是 1.13（更旧的版本），与 flink.md 写死的 1.16 不一致，本提交一并统一为 `{{ flinkVersionMajor }}`，消除了文档内部版本不一致问题。

### `docs/docs/flink-ddl.md`
**修改目的**：将 Flink DDL 文档中两处链接替换为变量引用。

**工作逻辑**：
- `flink-docs-master/docs/dev/table/sql/create/` → `flink-docs-release-{{ flinkVersionMajor }}/docs/dev/table/sql/create/`（从指向 master 改为指向具体大版本发布文档，避免 master 内容漂移导致链接失效）
- `flink-docs-release-1.16/docs/dev/table/sql/create/` → `flink-docs-release-{{ flinkVersionMajor }}/docs/dev/table/sql/create/`

### `docs/docs/flink-queries.md`
**修改目的**：将 Flink Queries 文档中两处链接替换为变量引用。

**工作逻辑**：
- `flink-docs-stable/docs/dev/datastream/event-time/generating_watermarks/#watermark-alignment` → `flink-docs-release-{{ flinkVersionMajor }}/docs/dev/datastream/event-time/generating_watermarks/#watermark-alignment`
- `flink-docs-stable/docs/dev/datastream/operators/windows/` → `flink-docs-release-{{ flinkVersionMajor }}/docs/dev/datastream/operators/windows/`
- `flink-docs-stable` 是 Flink 项目维护的"稳定版"文档别名，会随 Flink 最新稳定版漂移；改用 `flink-docs-release-1.19` 后绑定具体大版本，与 Iceberg 文档其他章节风格统一，避免随 Flink 上游版本变化导致内容不一致。

## 小结
- **成效**：Flink 文档版本管理集中化，未来升级 Flink 版本仅需修改 `site/mkdocs.yml` 一处；同时清理了文档中 `1.13`、`1.16`、`1.16.2` 等历史版本残留以及 `flink-docs-stable`、`flink-docs-master` 等不稳定链接形式，统一为 `flink-docs-release-{{ flinkVersionMajor }}`。
- **影响范围**：仅文档构建与渲染，不影响 Iceberg 代码、构建产物或运行时行为；影响 4 个 `docs/docs/flink*.md` 文档与 1 个 `site/mkdocs.yml` 配置。所有改动仅作用于 Iceberg 站点（mkdocs build）渲染，不影响仓库源码。
- **回迁注意事项**：当前 1.4.x 分支不存在 `site/` 目录与 `docs/docs/flink*.md` 等文件（site 与 docs 目录结构是后续 main 分支新增/重构的）。回迁前需先确认 1.4.x 分支是否已存在 site 与 docs 目录；若不存在，需先回迁建立文档站点的相关基础提交，否则本提交无法直接应用。建议回迁时检查 1.4.x 是否已有 `mkdocs.yml`，若有则将 `flinkVersion: '1.19.0'`、`flinkVersionMajor: '1.19'` 直接加入 `extra:`；同时需注意 1.4.x 时期 Iceberg 默认支持的 Flink 版本可能与 1.19 不同（1.4.x 时期更多对应 Flink 1.17/1.18），变量取值需根据 1.4.x 实际兼容的 Flink 版本调整，不能盲目照搬 1.19.0。
