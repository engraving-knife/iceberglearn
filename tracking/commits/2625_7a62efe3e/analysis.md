# 提交 2625：docs: Add link to BladePipe (#14047)

## 提交信息

- **序号**：2625 / 4088
- **哈希**：7a62efe3e35c55b8887e5374e11af97957080281
- **短哈希**：7a62efe3e
- **日期**：2025-09-11 22:01:28 +0200
- **作者**：Fokko Driesprong
- **提交说明**：docs: Add link to BladePipe
- **PR/Issue**：#14047（follow up 于 #14033，即提交 2617）

## 总体目的

这是提交 2617（移除 BladePipe 集成文档）的后续修复。提交 2617 因 BladePipe 网站看似不可达而删除了整个 BladePipe 集成页面。但作者随后发现：BladePipe 网站本身其实是正常的，只是页面上使用的图片资源失效了。

因此，与其完全移除 BladePipe（它仍是一个有效的 Iceberg 集成伙伴），更好的做法是不再维护一个本地文档页面（避免图片死链问题），而是在导航中直接添加一个指向 BladePipe 官网 Iceberg 专属文档页的外部链接。这样用户仍能从 Iceberg 文档站点发现 BladePipe 集成，同时避免了在仓库内维护可能失效的图片资源。

## 如何达成设计目的

在 `site/nav.yml` 的集成（Integrations）导航列表中，重新加入 BladePipe 条目，但与 2617 之前的形式不同：不再指向本地 `integrations/bladepipe.md` 页面（该页面已在 2617 删除），而是直接指向 BladePipe 官网的 Iceberg 数据源文档页面 URL（`https://www.bladepipe.com/docs/dataMigrationAndSync/datasource_func/Iceberg/props_for_iceberg_ds`）。这与导航中其他外部集成（如 Apache Doris、Apache Druid、ClickHouse 等直接指向外部 URL）的形式一致。

## 修改详情

### `site/nav.yml` (+1 line)

**修改目的**：在集成导航中恢复 BladePipe 入口，以外部链接形式。

**工作逻辑**：在 Apache Druid 条目之后、ClickHouse 条目之前，新增一行 `- BladePipe: https://www.bladepipe.com/docs/dataMigrationAndSync/datasource_func/Iceberg/props_for_iceberg_ds`。该 URL 指向 BladePipe 文档中 Iceberg 数据源的专属页面，比 2617 中删除的本地页面更具体（直接到 Iceberg 相关文档）。未恢复 `site/docs/integrations/bladepipe.md` 本地页面与 `mkdocs.yml` 的重定向规则，避免图片死链问题。

## 总结

本提交是 2617 的后续修正。发现 BladePipe 网站实际可用（仅图片失效）后，改为在导航中以外部链接形式恢复 BladePipe 集成入口，指向其官网 Iceberg 专属文档页。这既保留了集成伙伴的可发现性，又避免了在仓库内维护易失效的本地文档与图片资源，与导航中其他外部集成的处理方式一致。
