# 提交 1616 ab9e05e73 分析

## 提交信息
- 哈希：ab9e05e7369c55a95632b856729a05ea876cf788
- 日期：2025-01-22 09:04:27 +0100
- 作者：Yuya Ebihara
- 消息：Doc: Fix expired links on vendor page (#12045)

## 总体目的

本提交修复 Iceberg 文档站点 vendors 页面中失效的第三方链接。`site/docs/vendors.md` 列举了基于 Apache Iceberg 的各家厂商产品与服务，每条目中嵌入了指向厂商官网或文档的 URL。随着时间推移，部分厂商（本提交涉及 Tabular 与 Upsolver）调整了其网站路径结构，导致原文档中的部分深链（deep link）返回 404 或重定向到无关页面，影响用户查阅体验与文档可信度。

这是一次纯文档维护：作者在检查文档时发现这些失效链接，将其更新为当前可用的对应 URL，并保持文档的描述文字不变。

## 如何达成设计目的

设计思路是逐一定位失效链接并用厂商当前提供的对应页面 URL 替换。本提交涉及两处替换：

1. **Tabular**：原文中 `[Tabular](https://tabular.io/product/)` 的链接指向 `/product/` 子路径，该路径已失效（Tabular 网站重构后不再有此子路径）。改为根路径 `https://tabular.io/`，即与上一行标题 `[Tabular](https://tabular.io)` 一致的主站入口。

2. **Upsolver**：原文中两条 Upsolver 文档深链使用了 `https://docs.upsolver.com/reference/sql-commands/iceberg-tables/upsolver-managed-tables` 和 `https://docs.upsolver.com/how-to-guides/apache-iceberg/optimize-your-iceberg-tables`。Upsolver 文档站点迁移后路径前缀变为 `/content/reference-1/...` 和 `/content/how-to-guides-1/...`。本提交把这两条链接分别更新为 `https://docs.upsolver.com/content/reference-1/sql-commands/iceberg-tables/upsolver-managed-tables` 和 `https://docs.upsolver.com/content/how-to-guides-1/apache-iceberg/optimize-your-iceberg-tables`，使其指向迁移后的同一文档。

### 修改详情

#### site/docs/vendors.md

文件第 84-91 行附近的两处修改：

- Tabular 段落：`[Tabular](https://tabular.io/product/)` → `[Tabular](https://tabular.io/)`。仅改 URL，描述文字保持不变。
- Upsolver 段落：`[Iceberg tables](https://docs.upsolver.com/reference/...)` → `https://docs.upsolver.com/content/reference-1/...`；`[analyzes the health](https://docs.upsolver.com/how-to-guides/...)` → `https://docs.upsolver.com/content/how-to-guides-1/...`。仅改 URL，描述文字保持不变。

## 小结

此次修复让 vendors 页面中 Tabular 与 Upsolver 的链接恢复可访问，避免用户点击后遇到 404。这是文档质量维护的常规工作。

影响范围：仅文档站点一个 markdown 文件，对代码、构建、运行时行为均无影响。

回迁到 1.4.x 分支的注意事项：1.4.x 分支的 `site/docs/vendors.md` 内容通常比当前 main 分支旧，可能不包含这些链接（或链接指向的厂商在 1.4.x 时期尚未列入）。回迁前需确认 1.4.x 的 vendors.md 中是否存在这两条链接；若存在且仍为旧 URL，则可套用本提交；若 1.4.x 的 vendors.md 结构已与 main 不同（例如厂商列表不同），则应手动核对每条链接是否失效，而非机械套用。本提交本身不引入任何功能变化，回迁风险极低。
