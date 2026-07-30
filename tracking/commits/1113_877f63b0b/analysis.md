# 提交 1113：Docs: bump latest version to 1.6.1 (#11036)

## 提交信息

- **序号**：1113 / 4088
- **哈希**：877f63b0b9e2f47c24a528c55e8adf2018ebd2ff
- **短哈希**：877f63b0b
- **日期**：2024-08-28（Wed Aug 28 06:21:28 2024 -0700）
- **作者**：Carl Steinbach <cws@apache.org>
- **提交说明**：Docs: bump latest version to 1.6.1 (#11036)
- **PR/Issue**：#11036

## 总体目的

Apache Iceberg 在 2024-08-27 正式发布 1.6.1 维护版本，本提交将仓库内所有指向"最新版本"的元数据/文档指针从 1.6.0 升级到 1.6.1，并保留旧版本入口，使发布后的官网、Issue 模板、Apache 项目描述文件均能正确反映新版本。

涉及四类资产：

1. **GitHub Issue Bug 报告模板**：版本下拉项中把"latest release"标记从 1.6.0 转移到 1.6.1，并把 1.6.0 退化为普通历史版本项。
2. **DOAP（Description of a Project）RDF 文件**：更新 Apache 项目元数据中发布的版本号与发布日期。
3. **官网 MkDocs 配置**：将注入到所有文档页面的 `icebergVersion` 变量从 `1.6.0` 改为 `1.6.1`，便于文档中通过变量插值显示最新版本号。
4. **官网导航配置**：在版本切换器中新增 1.6.1 子文档入口，使网站访问者可切换到 1.6.1 文档。

## 如何达成设计目的

直接修改四个静态配置文件，把"1.6.0 (latest release)"中的 latest 标记迁移到 1.6.1，并在版本列表中保留 1.6.0 作为历史版本；DOAP 与 mkdocs.yml 中替换版本字符串；nav.yml 中新增 1.6.1 文档 include 项。提交说明中"Add missing files"指同步补齐相关配置文件，避免遗漏。

这是发布流程相关的文档元数据变更，无任何代码逻辑改动。

## 修改详情

### `.github/ISSUE_TEMPLATE/iceberg_bug_report.yml`

**修改目的**：把 Bug 报告模板中的"最新版本"标记从 1.6.0 改为 1.6.1。

**工作逻辑**：在 bug 报告表单的"version"下拉选项（`options`）中，将

```yaml
- "1.6.0 (latest release)"
```

替换为两项：

```yaml
- "1.6.1 (latest release)"
- "1.6.0"
```

新增 1.6.1 作为最新版本，1.6.0 退化为普通历史选项，便于用户在提交 Bug 时选择自己实际使用的版本。其余历史版本项（1.5.2、1.5.1、1.5.0 等）保持不变。

### `doap.rdf`

**修改目的**：更新 Apache 项目 DOAP 文件中"最新发布版本"信息。

**工作逻辑**：在 `<release>/<Version>` 节点中，将

```xml
<name>1.6.0</name>
<created>2024-07-23</created>
<revision>1.6.0</revision>
```

替换为：

```xml
<name>1.6.1</name>
<created>2024-08-27</created>
<revision>1.6.1</revision>
```

DOAP 是 Apache 项目向 https://projects.apache.org/ 暴露项目元数据的 RDF 文件，`revision` 即版本号、`created` 即发布日期。修改后外部抓取工具会显示 Iceberg 最新版本为 1.6.1（2024-08-27 发布）。

### `site/mkdocs.yml`

**修改目的**：更新官网 MkDocs 配置注入的 `icebergVersion` 变量。

**工作逻辑**：在 `extra:` 配置节中，将

```yaml
icebergVersion: '1.6.0'
```

改为：

```yaml
icebergVersion: '1.6.1'
```

该变量通过 MkDocs 的 `{{ icebergVersion }}` 模板插值在多个文档页面中显示当前最新版本号（例如安装指南、快速开始中"请使用 iceberg-x.x.x.jar"的版本提示）。修改后所有引用该变量的页面会自动显示 1.6.1。其他依赖版本（nessieVersion、flinkVersion 等）保持不变。

### `site/nav.yml`

**修改目的**：在官网 Docs 下拉中新增 1.6.1 版本文档入口。

**工作逻辑**：在 `nav: Docs:` 列表中，于 `latest` 与 `1.6.0` 之间插入一项：

```yaml
- 1.6.1: '!include docs/docs/1.6.1/mkdocs.yml'
```

完整结构变为：

```yaml
- Docs:
  - nightly: '!include docs/docs/nightly/mkdocs.yml'
  - latest: '!include docs/docs/latest/mkdocs.yml'
  - 1.6.1: '!include docs/docs/1.6.1/mkdocs.yml'
  - 1.6.0: '!include docs/docs/1.6.0/mkdocs.yml'
  - 1.5.2: '!include docs/docs/1.5.2/mkdocs.yml'
  ...
```

依赖 mkdocs-monorepo-plugin 的 `!include` 机制把 `docs/docs/1.6.1/mkdocs.yml` 子站合并进主站，让访问者可在版本切换器中选择 1.6.1。

## 小结

- **成效**：发布 1.6.1 后，仓库元数据与官网配置全面反映新版本——Bug 模板版本下拉含 1.6.1（latest），DOAP 暴露 1.6.1 元数据，官网所有页面通过 `icebergVersion` 变量显示 1.6.1，版本切换器新增 1.6.1 入口。
- **影响范围**：4 个文件、7 增 5 删，全部为文档/配置，无代码逻辑改动。
- **回迁到 1.4.x 的注意事项**：这是 main 分支在 1.6.1 发布时做的版本号同步，**无需回迁到 1.4.x**。1.4.x 是 1.4 系列维护分支，其版本元数据应继续指向 1.4.x 系列最新发布版本（如 1.4.3），不应跟随 main 切换到 1.6.x。即使 1.4.x 文档分支需要单独维护版本入口，也应在 1.4.x 自己的发布流程中处理，与 main 的此提交无回迁关系。
