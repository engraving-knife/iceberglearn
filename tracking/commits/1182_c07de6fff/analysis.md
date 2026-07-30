# 提交 1182：Build: Bump mkdocs-macros-plugin from 1.0.5 to 1.2.0 (#11189)

## 提交信息

- **序号**：1182 / 4088
- **哈希**：c07de6fffa762577562f4cc3d0205e7bd57b7a9d
- **短哈希**：c07de6fff
- **日期**：2024-09-25（Wed Sep 25 11:13:39 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump mkdocs-macros-plugin from 1.0.5 to 1.2.0 (#11189)
- **PR/Issue**：#11189

## 总体目的

Iceberg 仓库的 `site/` 目录用于构建项目文档站点（基于 MkDocs Material），其依赖通过 `site/requirements.txt` 固定版本。本次提交由 Dependabot 自动发起，将 `mkdocs-macros-plugin` 从 1.0.5 升级到 1.2.0（一个 semver minor 升级）。

`mkdocs-macros-plugin` 是 MkDocs 的一个插件，用于在文档中嵌入宏（macros），支持在 Markdown 中使用 Python 变量、Jinja2 模板等动态内容。1.0.5 → 1.2.0 跨越了多个小版本，包含若干功能增强与 bug 修复。升级的目的是跟进上游社区进展，获取新版本的能力和修复，避免长期停留在旧版本造成与其它 MkDocs 生态包的兼容性偏差。

## 如何达成设计目的

通过修改 `site/requirements.txt` 中的依赖版本号，将 `mkdocs-macros-plugin==1.0.5` 替换为 `mkdocs-macros-plugin==1.2.0`。CI/CD 流水线（或本地构建文档时）会通过 `pip install -r site/requirements.txt` 自动获取新版本。这是纯依赖版本字符串变更，无代码逻辑改动。

## 修改详情

### `site/requirements.txt`

**修改目的**：升级 mkdocs-macros-plugin 到 1.2.0。

**工作逻辑**：将第 19 行的依赖声明由：

```
mkdocs-macros-plugin==1.0.5
```

改为：

```
mkdocs-macros-plugin==1.2.0
```

文件中其它依赖项（mkdocs-awesome-pages-plugin 2.9.3、mkdocs-material 9.5.34、mkdocs-material-extensions 1.3.1 等）保持不变。

## 小结

- **成效**：文档站点构建依赖 `mkdocs-macros-plugin` 跟进到 1.2.0，可使用其新版本带来的能力与修复。
- **影响范围**：仅 `site/requirements.txt` 一个文件，单行版本号变更，不影响 Iceberg 运行时或 Java 代码。
- **回迁到 1.4.x 的注意事项**：这是文档站点依赖升级，与产品发布物（jar 包）无关。1.4.x 作为维护分支通常不发布新文档站点，且 `site/requirements.txt` 与 Java 产物完全解耦。**无需回迁**。即使 1.4.x 分支保留旧的 1.0.5 版本，也不会影响其发布质量。Dependabot 这类升级通常只服务于 main 分支的活跃文档构建。
