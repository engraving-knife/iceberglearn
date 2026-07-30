# 提交 2618：Remove BladePipe due to broken links (#14033)

## 提交信息

- **序号**：2618 / 4088
- **哈希**：720ef99720a1c59e4670db983c951243dffc4f3e
- **短哈希**：720ef9972
- **日期**：2025-09-09 20:16:06 +0200
- **作者**：Fokko Driesprong
- **提交说明**：Remove BladePipe due to broken links
- **PR/Issue**：#14033

## 总体目的

BladePipe 是一个实时端到端数据集成工具，此前作为第三方集成伙伴在 Iceberg 文档站点中有一整页介绍（`site/docs/integrations/bladepipe.md`），描述其支持的源（MySQL、Oracle、PostgreSQL、SQL Server、Kafka）与 catalog/存储组合，并链接到 BladePipe 官网与图片资源。

提交说明指出 BladePipe 网站当前不可达，导致文档中的外链与图片成为死链。为避免文档站点出现失效链接影响用户体验，本提交暂时移除 BladePipe 集成文档页面及其在导航与重定向配置中的引用，待其网站与图片修复后再重新添加（后续提交 2624 会重新加回）。

## 如何达成设计目的

删除 BladePipe 文档页面文件，并从 mkdocs 的导航配置（`nav.yml`）和重定向映射（`mkdocs.yml`）中移除对它的引用，确保站点不再生成该页面、不再出现指向已删文件的导航项与重定向规则，从而消除死链。

## 修改详情

### `site/docs/integrations/bladepipe.md` (删除, -119 lines)

**修改目的**：移除 BladePipe 集成文档页面。

**工作逻辑**：整文件删除。该文件原包含 BladePipe 产品介绍、支持的数据源列表、支持的 catalog/存储组合，以及指向 BladePipe 官网 `https://www.bladepipe.com/` 和图片资源的链接，这些链接因网站不可达而成为死链。

### `site/mkdocs.yml` (-1 line)

**修改目的**：移除 BladePipe 的重定向规则。

**工作逻辑**：在 `redirects.redirect_maps` 配置中删除 `'docs/nightly/bladepipe.md': 'integrations/bladepipe.md'` 这一行，避免对已删除页面配置无用重定向。

### `site/nav.yml` (-1 line)

**修改目的**：从导航菜单移除 BladePipe 入口。

**工作逻辑**：在集成（Integrations）导航列表中删除 `- BladePipe: integrations/bladepipe.md` 一行，使站点导航不再显示该入口。

## 总结

本提交因 BladePipe 网站不可达导致死链，暂时移除其集成文档页面及导航/重定向配置。这是文档质量维护的常规操作——保证站点不出现失效链接。提交说明明确表示待网站修复后会重新添加，后续提交 2624（`docs: Add link to BladePipe`）确实将其恢复。
