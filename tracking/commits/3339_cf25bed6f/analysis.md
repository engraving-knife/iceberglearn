# 提交 3339：Docs: Add UDF spec to website navigation (#15491)

## 提交信息

- **序号**：3339 / 4088
- **哈希**：cf25bed6f41627e6d8d343ef63845d88209b77fd
- **短哈希**：cf25bed6f
- **日期**：2026-03-02 11:36:58 -0800
- **作者**：Steven Zhen Wu
- **提交说明**：Docs: Add UDF spec to website navigation (#15491)
- **PR/Issue**：#15491

## 总体目的

本提交将 Iceberg 的 UDF（用户定义函数）规范文档接入文档网站的导航，使访问者能在网站侧边栏找到并阅读该规范。

背景是：Iceberg 的各类格式规范（如 view-spec、puffin-spec、gcm-stream-spec）都存放于仓库根目录的 `format/` 目录下，作为权威的规范源文件。而文档网站基于 MkDocs 构建，源码在 `site/` 目录，MkDocs 要求文档内容位于 `site/docs/` 下。为避免在两处维护重复内容，仓库采用软链接（symlink）的方式：在 `site/docs/` 下创建指向 `../../format/<spec>.md` 的符号链接，再在 MkDocs 的导航配置（`site/mkdocs-dev.yml` 与 `site/nav.yml`）中注册导航条目。UDF 规范文档 `format/udf-spec.md` 此前已存在，但未接入网站导航，导致用户在文档网站上无法直接浏览。本提交补齐了这一缺失，沿用与其他 spec 完全相同的接入模式。

## 如何达成设计目的

沿用既有的 spec 接入模式，分三步：在 `site/docs/` 下新建指向 `../../format/udf-spec.md` 的软链接；在 `site/mkdocs-dev.yml`（开发环境 MkDocs 配置）与 `site/nav.yml`（导航定义）的 Specifications 导航分组中、紧随 AES GCM Stream spec 之后追加 `UDF spec: udf-spec.md` 条目；同时删除 `format/udf-spec.md` 中一处多余空行以保持格式整洁。

## 修改详情

### `site/docs/udf-spec.md` (new file, +1 line)

**修改目的**：在网站文档目录中建立指向 UDF 规范源文件的软链接。

**工作逻辑**：
新建文件 `site/docs/udf-spec.md`，内容为符号链接 `../../format/udf-spec.md`（mode 120000）。这样 MkDocs 在构建网站时会把 `format/udf-spec.md` 的实际内容作为 `udf-spec.md` 页面纳入站点，无需复制文件、避免双份维护。这与 `view-spec.md`、`puffin-spec.md`、`gcm-stream-spec.md` 的处理方式一致。

### `site/mkdocs-dev.yml` (+1 line)

**修改目的**：在开发环境 MkDocs 导航中注册 UDF spec 条目。

**工作逻辑**：
在 `nav` 的 Specifications 分组中，于 `AES GCM Stream spec: gcm-stream-spec.md` 之后、`Implementation status: status.md` 之前，新增 `- UDF spec: udf-spec.md`。该文件是 MkDocs 的开发配置，用于本地预览站点。

### `site/nav.yml` (+1 line)

**修改目的**：在站点导航定义中注册 UDF spec 条目。

**工作逻辑**：
与 `mkdocs-dev.yml` 同样新增 `- UDF spec: udf-spec.md`，位置一致。`nav.yml` 是最终发布站点的导航定义来源，两处都加保证开发与发布环境导航一致。

### `format/udf-spec.md` (+0/-1 lines)

**修改目的**：删除一处多余空行，保持格式整洁。

**工作逻辑**：
在 `timestamp-ms` 行所在的表格与 `#### Null Input Handling` 小节之间，原本有两个连续空行，删除其中一个。该改动不影响内容渲染，仅是格式规范化（可能与通过软链接接入 MkDocs 渲染时的格式一致性有关）。

## 总结

本提交通过在 `site/docs/` 下建立指向 `format/udf-spec.md` 的软链接，并在 `mkdocs-dev.yml` 与 `nav.yml` 导航中注册条目，将 UDF 规范接入 Iceberg 文档网站，使用户能在网站侧边栏 Specifications 分组中直接查阅该规范；改动完全沿用其他 spec 的既有接入模式，并顺带清理了源文件中的一处多余空行。
