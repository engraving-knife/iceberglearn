# 提交 0563：文档——将规范文件从 Hugo 格式更新为 mkdocs 格式

## 提交信息

- **序号**：0563 / 4088
- **哈希**：f0b3733e0e7071894bdf63133bcc8c00754a1080
- **短哈希**：f0b3733e0
- **日期**：2024-03-06（AuthorDate 2024-03-06 04:09:22 -0600）
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Docs: Update specs from hugo to mkdocs format (#9861)
- **PR/Issue**：#9861。本提交是 PR #9591（提交 `ed288987c`，"Convert Hugo versioned docs to mkdocs format"，2024-02-01）的后续补漏——#9591 把 `docs/` 目录下的版本化文档从 Hugo 迁移到了 mkdocs，但漏掉了位于仓库根目录 `format/` 下的 4 个规范文件；本提交补齐这部分迁移。

## 总体目的

本提交要把 Iceberg 的 4 个格式规范文件（`format/spec.md`、`format/puffin-spec.md`、`format/gcm-stream-spec.md`、`format/view-spec.md`）从 Hugo 静态站点生成器格式转换为 mkdocs 格式，使它们与 #9591 已迁移完成的 `docs/` 目录文档保持一致，能够被 mkdocs 正确解析与渲染。

**背景动机**：

- Iceberg 官方文档站点原本基于 Hugo 构建。2024-02-01 的 PR #9591 完成了从 Hugo 到 mkdocs 的整体迁移：把 `docs/` 下的 `.md` 文件移到 `docs/docs/`、改写 frontmatter、调整内部链接、新增 `docs/mkdocs.yml` 配置。
- 但 `format/` 目录下的 4 个规范文件（Iceberg 表规范、Puffin 规范、AES GCM Stream 规范、视图规范）在 #9591 中**未被处理**，仍保留 Hugo 时代的 frontmatter 与链接风格。这导致这些规范文件在 mkdocs 站点中：
  - frontmatter 里的 Hugo 专属字段（`url:`、`toc:`、`disableSidebar:`）对 mkdocs 无意义，mkdocs 不识别这些字段（mkdocs 的 frontmatter 只关心 `title` 等），属于冗余/误导信息；
  - 内部链接仍按 Hugo 的 URL 结构书写，在 mkdocs 渲染后会指向错误路径，产生 404。
- 本提交补齐这一遗漏，是文档迁移的收尾工作。

## 如何达成设计目的

设计思路分两部分：

1. **清理 Hugo 专属 frontmatter**：对 4 个规范文件，把 YAML frontmatter 中 Hugo 专用的 3 个字段 `url:`、`toc:`、`disableSidebar:` 全部删除，只保留通用的 `title:` 字段。mkdocs 只需要 `title`（用于页面标题与导航显示），不需要也不识别其余字段。

2. **修正 `spec.md` 中的内部链接**以适应 mkdocs 的 URL 结构：
   - **规范间链接**：`../puffin-spec` 改为 `puffin-spec.md`。Hugo 默认把每个页面渲染为目录式 URL（如 `/puffin-spec/`），且规范文件在站点中处于有层级的路径，因此从 `spec` 页面引用 `puffin-spec` 需要先 `..` 回到父目录；mkdocs 中这些规范文件同处一个目录，直接用相对文件名 `puffin-spec.md` 引用即可（mkdocs 会把 `.md` 链接解析为对应的页面 URL）。
   - **Javadoc 链接**：`../../../javadoc/{{ icebergVersion }}/...` 改为 `../javadoc/{{ icebergVersion }}/...`。Hugo 站点结构中规范页面 URL 嵌套更深（需要 `../../../` 上溯三级才能到达站点根的 `javadoc/` 目录）；mkdocs 站点结构更扁平，只需 `../` 上溯一级。`{{ icebergVersion }}` 是 mkdocs 模板变量（配合 mkdocs-macros 或类似插件在构建时替换为当前 Iceberg 版本号），这部分保持不变。

`puffin-spec.md`、`gcm-stream-spec.md`、`view-spec.md` 三个文件只涉及 frontmatter 清理，无内部链接需要修改（它们没有指向其他规范或 javadoc 的相对链接）。

## 修改详情

### `format/gcm-stream-spec.md` / `format/puffin-spec.md` / `format/view-spec.md`

**修改目的**：移除 Hugo 专属 frontmatter 字段，使其成为纯 mkdocs 兼容的 frontmatter。

**工作逻辑**：把每个文件顶部的 YAML frontmatter 从

```yaml
---
title: "AES GCM Stream Spec"   # 各文件标题不同
url: gcm-stream-spec            # 各文件 url 不同
toc: true
disableSidebar: true
---
```

简化为

```yaml
---
title: "AES GCM Stream Spec"
---
```

即删除 `url:`、`toc: true`、`disableSidebar: true` 三行。这三个字段的作用分别是：
- `url:` —— Hugo 中覆盖页面 URL 路径，mkdocs 中 URL 由文件路径决定，不需要；
- `toc: true` —— Hugo 中开启该页面的目录侧栏，mkdocs 默认通过 `toc` 插件自动生成，不需要；
- `disableSidebar: true` —— Hugo 中隐藏左侧导航侧栏，mkdocs 的导航由 `mkdocs.yml` 的 `nav` 统一控制，不需要。

三个文件的改动完全同构（各 -3 行）。

### `format/spec.md`

**修改目的**：除 frontmatter 清理（同上，-3 行）外，修正 5 处内部链接以适应 mkdocs URL 结构。

**工作逻辑**：

1. **frontmatter**：同其他三文件，删除 `url: spec`、`toc: true`、`disableSidebar: true`，仅保留 `title: "Spec"`。

2. **Puffin 规范链接（3 处）**：

   - 第 689 行附近：`[Puffin files](../puffin-spec)` → `[Puffin files](puffin-spec.md)`
   - 第 698 行附近（表格内）：`[Puffin file format](../puffin-spec)` → `[Puffin file format](puffin-spec.md)`（statistics-path 行）
   - 第 702 行附近（表格内）：`[Puffin file format](../puffin-spec)` → `[Puffin file format](puffin-spec.md)`（file-footer-size-in-bytes 行）

   这三处都指向 `puffin-spec.md`（同一 `format/` 目录下的 Puffin 规范文件）。Hugo 的 `../puffin-spec` 写法是因为 Hugo 给每个页面生成目录式 URL，从 `spec` 页面（URL 形如 `.../spec/`）跳到 `puffin-spec` 需要先 `..` 退出当前目录；mkdocs 中同目录文件直接用 `puffin-spec.md` 相对引用，mkdocs 在构建时自动将其解析为目标页面 URL。

3. **Javadoc 链接（2 处）**：

   - 第 791 行附近：`[HadoopTableOperations](../../../javadoc/{{ icebergVersion }}/index.html?org/apache/iceberg/hadoop/HadoopTableOperations.html)` → `[HadoopTableOperations](../javadoc/{{ icebergVersion }}/index.html?org/apache/iceberg/hadoop/HadoopTableOperations.html)`
   - 第 804 行附近：`[BaseMetastoreTableOperations](../../../javadoc/{{ icebergVersion }}/index.html?org/apache/iceberg/BaseMetastoreTableOperations.html)` → `[BaseMetastoreTableOperations](../javadoc/{{ icebergVersion }}/index.html?org/apache/iceberg/BaseMetastoreTableOperations.html)`

   上溯层级从 `../../../`（三级）改为 `../`（一级），反映 mkdocs 站点中规范页面相对于 `javadoc/` 目录的路径深度比 Hugo 时浅了两级。`{{ icebergVersion }}` 模板变量与 `index.html?...` 锚点查询串保持不变。

## 小结

本提交是 Iceberg 文档从 Hugo 迁移到 mkdocs 的收尾补漏，使 `format/` 下的 4 个规范文件与 #9591 已迁移的 `docs/` 文档格式统一。改动共 4 个文件、+5/-17 行，纯粹是文档 frontmatter 清理与内部链接路径修正，不涉及任何代码或规范语义变更。

**影响范围**：仅影响文档站点的构建与渲染。修复后，规范文件在 mkdocs 站点中能被正确解析（无冗余 frontmatter 字段），且指向 Puffin 规范与 Javadoc 的内部链接不再 404。

**回迁到 1.4.x 的注意事项**：

- 本提交本身即是 1.4.x 文档维护期的改动，回迁无障碍。
- 回迁前提是 1.4.x 已合入 #9591 的 mkdocs 迁移（`docs/mkdocs.yml` 等已存在）；若 1.4.x 仍在用 Hugo，则本提交不应回迁（会反而破坏 Hugo 渲染）。
- 链接路径的层级（`../` vs `../../../`）依赖于 mkdocs 站点中规范文件的实际部署路径，若后续调整站点目录结构需同步复核这些相对链接。
