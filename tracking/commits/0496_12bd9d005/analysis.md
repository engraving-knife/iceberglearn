# 提交 0496：Docs: Fix broken strike-through markup (#9696)

## 提交信息

- **序号**：0496 / 4088
- **哈希**：12bd9d0055b1a841b5fbe639f66ea09e00a6a12c
- **短哈希**：12bd9d005
- **日期**：2024-02-11 22:35:12 +0300
- **作者**：Muna Bedan
- **提交说明**：Docs: Fix broken strike-through markup (#9696)
- **PR/Issue**：#9696

## 总体目的

这个提交修复了 Iceberg 文档站点（基于 MkDocs Material 构建）中删除线（strike-through）标记语法无法正确渲染的问题。在 Markdown 扩展语法中，`~~text~~` 用于表示删除线效果，但该语法并非标准 Markdown 规范的一部分，需要依赖相应的扩展插件才能被正确解析和渲染。

Iceberg 的文档站点使用 MkDocs Material 作为主题，并通过 `site/mkdocs.yml` 配置文件中的 `markdown_extensions` 列表来启用各种 Python-Markdown 扩展。在本次修复之前，配置中已经启用了 `pymdownx.mark`（用于高亮标记 `==text==` 语法），但并未启用负责删除线语法的 `pymdownx.tilde` 扩展。这导致文档源文件中凡是使用 `~~text~~` 删除线语法的地方，都无法被正确渲染为带删除线的文本，而是原样显示为带有波浪号的字符串，影响文档的可读性和专业性。

这是一个典型的"配置遗漏"类修复：文档作者按照 Markdown 扩展语法书写了删除线内容，但站点构建配置缺少对应的扩展插件，导致渲染失败。修复方式是在 `markdown_extensions` 列表中追加 `pymdownx.tilde` 扩展。该扩展属于 PyMdown Extensions 套件，与已经启用的 `pymdownx.mark`、`pymdownx.tabbed` 等属于同一系列，启用后即可让 `~~text~~` 语法正常工作。

## 如何达成设计目的

实现路径非常直接：在 `site/mkdocs.yml` 的 `markdown_extensions` 配置块中，紧随 `pymdownx.mark` 之后追加一行 `- pymdownx.tilde`，使 MkDocs 在构建文档时加载该扩展，从而正确解析删除线语法。改动仅涉及一行配置，不涉及任何文档源文件内容的修改，因为文档中已经存在使用 `~~text~~` 语法的部分，只是缺少解析器支持。

## 修改详情

### `site/mkdocs.yml`

**修改目的**：启用 `pymdownx.tilde` 扩展，使文档中的 `~~text~~` 删除线语法能够被正确渲染。

**工作逻辑**：

在 `markdown_extensions` 列表中，原本的配置片段如下：

```yaml
  - pymdownx.tabbed:
      alternate_style: true
  - pymdownx.mark
  - attr_list
```

修改后变为：

```yaml
  - pymdownx.tabbed:
      alternate_style: true
  - pymdownx.mark
  - pymdownx.tilde
  - attr_list
```

`pymdownx.tilde` 是 PyMdown Extensions 提供的扩展，它将 `~~text~~` 解析为 `<del>` 标签（删除线），将 `~~^text^~~` 解析为 `<sup>`（上标）、`~~~text~~~` 解析为 `<sub>`（下标）等。将其放在 `pymdownx.mark` 之后、`attr_list` 之前，保持了与同类 PyMdown 扩展相邻的组织顺序，配置风格一致。

## 小结

本提交是一个单行文档配置修复，通过在 MkDocs 配置中启用 `pymdownx.tilde` 扩展，解决了文档站点中删除线（`~~text~~`）标记无法渲染的问题。改动范围极小但针对性明确，属于文档基础设施的可用性修复，确保文档作者使用的 Markdown 扩展语法都能在生成的站点中正确呈现。
