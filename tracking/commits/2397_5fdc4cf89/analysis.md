# 提交 2397：Docs: Move links to other implementations out of Java docs (#13618)

## 提交信息

- **序号**：2397 / 4088
- **哈希**：5fdc4cf8990140adc95d3a2b8ba1c878ac47ecd9
- **短哈希**：5fdc4cf89
- **日期**：2025-07-23 21:26:29 -0700
- **作者**：Manu Zhang
- **提交说明**：Docs: Move links to other implementations out of Java docs (#13618)
- **PR/Issue**：#13618

## 总体目的

此提交重组了 Iceberg 项目文档网站的导航结构，将 Python、Rust、Go、C++ 等其他语言实现的链接从 Java 文档的导航中移出，独立为"Other Implementations"分类。原先这些链接放在 "Libraries" 分类下与 Java 文档混在一起，容易让用户误以为它们是 Java 文档的一部分。

重组后，Java 文档（Quickstart、API、Javadoc）归入独立的 "API" 分类，其他语言实现归入 "Other Implementations" 分类，使文档结构更清晰，用户能更快找到所需内容。同时在站点配置中启用了 `navigation.sections` 功能以改善导航体验。

## 如何达成设计目的

通过修改三个 MkDocs 配置文件实现导航重组：

1. `docs/mkdocs.yml`：将 "Libraries" 分类拆分，Java 相关内容归入 "API"，移除其他语言链接。
2. `site/mkdocs.yml`：启用 `navigation.sections` 主题功能。
3. `site/nav.yml`：在 Docs 下创建 "Java" 和 "Other Implementations" 两个子分类。

## 修改详情

### `docs/mkdocs.yml` (+4/-8 lines)

**修改目的**：重组文档导航，将 Java 内容独立为 API 分类。

**工作逻辑**：将原 "Libraries" 分类（包含 Java Quickstart/API/Javadoc 和 Python/Rust/Go 链接）替换为 "API" 分类（仅包含 Java Quickstart/API/Javadoc），移除其他语言实现的链接。

### `site/mkdocs.yml` (+1/-0 lines)

**修改目的**：启用导航分组功能。

**工作逻辑**：在 theme.features 中添加 `navigation.sections`，使导航栏支持分区展示。

### `site/nav.yml` (+27/-19 lines)

**修改目的**：重组站点导航结构。

**工作逻辑**：在 Docs 分类下新增 "Java" 子分类（包含各版本的 Java 文档，从 nightly 到 1.4.0 及 archive）和 "Other Implementations" 子分类（包含 Python、Rust、Go、C++ 的外部链接）。原结构中这些版本文档和外部链接混在同一层级，新结构按语言实现清晰分组。

## 总结

这是一个文档导航结构优化提交，将 Java 文档与其他语言实现的链接分离，使文档站点结构更清晰。修改仅涉及 MkDocs 配置文件，不影响任何代码逻辑。
