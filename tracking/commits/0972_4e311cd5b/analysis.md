# 提交 0972：Docs: Add bodo to iceberg vendors (#10756)

## 提交信息

- **序号**：0972 / 4088
- **哈希**：4e311cd5b8ab4ecdfbfd080ad5a807c4a8b27657
- **短哈希**：4e311cd5b
- **日期**：2024-07-24 11:26:29 -0600
- **作者**：ritwika314
- **提交说明**：Docs: Add bodo to iceberg vendors (#10756)
- **PR/Issue**：#10756

## 总体目的

Apache Iceberg 官网维护了一个 vendors（厂商）页面 `site/docs/vendors.md`，用于列出在自家产品中集成并支持 Apache Iceberg 的商业厂商，方便使用者了解生态中可用的商业化方案。该页面按字母顺序排列各厂商条目，每个条目包含厂商名称、官网链接以及一段对其 Iceberg 集成能力的简短描述。

本提交的目的是将 Bodo 这一厂商新增到 vendors 列表中。Bodo 是一个高性能的 SQL 与 Python 计算引擎，将 HPC（高性能计算）和超算技术引入数据分析领域，并以一等公民（first-class）的方式支持读写 Iceberg 表。此举属于生态文档的常规更新，反映 Iceberg 商业生态的扩充。

## 如何达成设计目的

实现方式非常直接：在 `site/docs/vendors.md` 文件中，按字母顺序在 CelerData 条目之前（Bodo 的 B 在 C 之前）插入一个新的 `### Bodo` 章节，包含官网链接和一段介绍文字。无任何代码或构建逻辑改动。

## 修改详情

### `site/docs/vendors.md`

**修改目的**：在厂商列表中新增 Bodo 条目。

**工作逻辑**：在文件顶部的引导语之后、原首个厂商条目 CelerData 之前，插入一个三级标题 `### [Bodo](https://bodo.ai)` 及其描述段落。描述内容说明：Bodo 是高性能 SQL & Python 计算引擎，将 HPC 与超算技术应用于数据分析；以一等公民方式支持 Iceberg 表的读写；在 AWS、Azure 上提供云服务，并提供本地部署方案。共新增 7 行，无删除。

## 小结

- **成效**：在 Iceberg 官网 vendors 页面中新增了 Bodo 厂商条目，使访问者能了解到 Bodo 对 Iceberg 的商业支持。
- **影响范围**：仅 `site/docs/vendors.md` 一个文档文件，7 行新增，无功能影响。
- **回迁到 1.4.x 的注意事项**：该提交仅为文档新增，与版本分支无强耦合，理论上可安全回迁到 1.4.x 分支（vendor 列表通常希望保持最新）。但实际上文档类生态更新一般不需要回迁到维护分支，1.4.x 分支可按需决定是否同步；如回迁无任何代码风险。
