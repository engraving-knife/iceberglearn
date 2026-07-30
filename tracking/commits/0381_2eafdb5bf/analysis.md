# 提交 0381：Docs: Fix community link

## 提交信息

- **序号**：0381
- **哈希**：2eafdb5bfa834e76d840a0ec65483552d4847a10
- **短哈希**：2eafdb5bf
- **日期**：Wed Jan 17 15:47:42 2024 -0800
- **作者**：Brian "bits" Olsen <brianolsen87@gmail.com>
- **提交说明**：Docs: Fix community link (#9500)
- **PR/Issue**：#9500

## 总体目的

这个提交修复 Iceberg 官方文档站点（基于 mkdocs 构建）中社区链接的指向问题。在 `site/mkdocs.yml` 的 `extra.social` 配置段中，"community"（社区）社交图标原本使用相对路径 `./community` 作为跳转链接，这种写法在 mkdocs 构建产物中会因相对路径解析的上下文不同而指向错误位置或产生 404。

从设计意图上看，这是一个典型的文档站点可用性修复：把相对链接替换为站点绝对 URL `https://iceberg.apache.org/community/`，确保无论用户当前处于哪个页面、哪个层级的子路径下，点击图标都能稳定地跳转到 Apache Iceberg 官方社区页面。

这种修复属于低风险、单点的文档维护，但它直接关系到社区入口的可访问性——社区链接是用户参与开源项目、提问、贡献的入口，长期失效会严重影响项目对外的引流能力。

## 如何达成设计目的

提交通过修改 mkdocs 配置文件中社交链接条目的 `link` 字段实现，将 `'./community'` 改为 `'https://iceberg.apache.org/community/'`。改动只有一行，没有引入新的依赖、模板或构建逻辑。

## 修改详情

### site/mkdocs.yml

**修改目的**：修正社区社交图标的链接地址，避免相对路径在多级页面下解析错误。

**工作逻辑**：mkdocs 的 `extra.social` 用于在页面页脚或导航栏渲染一组带图标的社交链接（这里使用 `fontawesome/regular/comments` 图标，标题为 `community`）。`link` 字段既支持相对路径也支持绝对 URL。原配置 `'./community'` 是相对路径，mkdocs 在生成子目录页面时不会自动重写为正确的相对层级，容易导致链接指向不存在的资源；改用绝对 URL 后，链接在任何页面下都稳定指向官方社区页面，符合 mkdocs 文档关于社交链接推荐使用绝对 URL 的最佳实践。

## 小结

这是一个最小化、零风险的文档站点修复，体现了"以绝对 URL 替代相对路径"这一通用 Web 站点配置最佳实践。修复虽然只动一行，但解决了社区入口链接失效的实际可用性问题，是 1.4.x 分支相对 main 落后的若干修复补丁中典型的文档维护类提交。
