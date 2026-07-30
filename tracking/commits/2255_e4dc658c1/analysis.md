# 提交 2255：Docs: Add Firebolt to vendor-related documentation (#13350)

## 提交信息

- **序号**：2255 / 4088
- **哈希**：e4dc658c199fe68b0a4f53712132735a70e4f7fc
- **短哈希**：e4dc658c1
- **日期**：2025-06-18 20:32:03 -0700
- **作者**：jingtao-firebolt
- **提交说明**：Docs: Add Firebolt to vendor-related documentation
- **PR/Issue**：#13350

## 总体目的

本提交将 Firebolt 添加到 Iceberg 项目的供应商相关文档中。Firebolt 是一个云数据仓库，专为需要低延迟和高并发的数据密集型应用而设计，能够以亚秒级性能读取 Apache Iceberg 表，并与主流 Iceberg catalog 无缝集成。Firebolt 还提供免费的自托管版本 Firebolt Core。

此改动属于纯文档更新，目的是让 Iceberg 社区用户了解 Firebolt 作为 Iceberg 生态系统的供应商之一，并在文档导航中提供指向 Firebolt 文档和博客的链接。

## 如何达成设计目的

- 在 mkdocs 导航配置中添加 Firebolt 文档链接。
- 在供应商页面（vendors.md）中添加 Firebolt 的描述段落。
- 在博客页面（blogs.md）中添加 Firebolt 关于查询 Apache Iceberg 的博客条目。

## 修改详情

### `docs/mkdocs.yml` (修改, +1/0 lines)

**修改目的**：在文档导航中添加 Firebolt 链接。

**工作逻辑**：在 nav 配置的供应商导航部分添加一行 `Firebolt: https://docs.firebolt.io/reference-sql/functions-reference/table-valued/read_iceberg`，指向 Firebolt 的 `read_iceberg` 表函数文档。

### `site/docs/blogs.md` (修改, +5/0 lines)

**修改目的**：添加 Firebolt 关于 Iceberg 的博客条目。

**工作逻辑**：在博客列表的最前面（最新的）添加 Firebolt 2025年6月11日的博客"Querying Apache Iceberg with Sub-Second Performance"，作者为 Lorenz Hübschle。

### `site/docs/vendors.md` (修改, +8/0 lines)

**修改目的**：在供应商列表中添加 Firebolt 的描述。

**工作逻辑**：添加 Firebolt 段落，描述其为云数据仓库，优化了亚秒级读取 Iceberg 表的性能，与主流 Iceberg catalog 集成。同时提及 Firebolt Core 自托管版本，并提供指向 Firebolt 博客的链接了解更多信息。

## 总结

本提交是纯文档更新，将 Firebolt 作为 Iceberg 生态系统的新供应商添加到项目文档中。涉及 3 个文档文件共 14 行新增内容，不包含任何代码改动。这有助于 Iceberg 社区用户了解 Firebolt 的 Iceberg 支持能力。
