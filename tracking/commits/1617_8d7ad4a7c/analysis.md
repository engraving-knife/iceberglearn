# 提交 1617 8d7ad4a7c 分析

## 提交信息
- 哈希：8d7ad4a7c94d1755f58d8c5f4fd4040fe4873e32
- 日期：2025-01-22 11:50:33 +0100
- 作者：hengm3467
- 消息：Docs: Add RisingWave to the Vendors page (#12043)

## 总体目的

本提交在 Iceberg 文档站点的 vendors 页面新增一条 RisingWave 厂商条目。Apache Iceberg 的 vendors 页面用于列举生态中基于或集成 Iceberg 的第三方产品与服务，方便用户了解可用的工具选项。RisingWave 是一个流式数据库，已经实现了对 Iceberg 表的读取与写入集成，本提交让其正式进入官方 vendor 列表。

这是一次纯文档新增：作者（疑似 RisingWave 团队成员或社区贡献者）按照 vendors 页面既有的条目格式，提交了一个段落介绍 RisingWave 与 Iceberg 的集成方式、可用形态（开源 / 托管云 / 企业版），并附上相关文档链接。

## 如何达成设计目的

设计思路是沿用 vendors 页面已有的"### [厂商名](官网链接)"二级标题 + 一段描述性文字的格式，在 PuppyGraph 条目之后、Snowflake 条目之前插入新的 RisingWave 条目。位置选择上，vendors 页面条目大致按厂商名字母顺序排列（PuppyGraph 之后、Snowflake 之前正好是 R 字母段），保持页面有序。

### 修改详情

#### site/docs/vendors.md

在 PuppyGraph 段落之后、Snowflake 段落之前新增 4 行：

- `### [RisingWave](https://risingwave.com/)` —— 二级标题，链接到 RisingWave 官网；
- 空行；
- 一段描述性文字，介绍 RisingWave 是云原生流式数据库，支持从消息队列、数据库（CDC）、数据湖、文件等来源进行实时数据摄入与处理；与 Iceberg 的集成支持[读取](https://docs.risingwave.com/integrations/sources/apache-iceberg)和[写入](https://docs.risingwave.com/integrations/destinations/apache-iceberg)Iceberg 表，并能跨源高效合并小文件；并说明 RisingWave 提供三种形态：[开源](https://github.com/risingwavelabs/risingwave)、托管云服务 [RisingWave Cloud](https://cloud.risingwave.com/auth/signin/)（支持 BYOC）、企业版 [RisingWave Premium](https://docs.risingwave.com/get-started/rw-premium-edition-intro)；
- 空行（与下一段 Snowflake 隔开）。

注意：插入位置紧邻 `<!-- markdown-link-check-disable-next-line -->` 注释上方，该注释是给 markdown-link-check 工具用的，用于跳过对下一条 Snowflake 链接的检查（Snowflake 站点可能阻止自动化检查），本提交未改动该注释。

## 小结

此次新增让 RisingWave 正式进入 Iceberg 官方 vendor 列表，向用户暴露一个已集成 Iceberg 的流式数据库选项。对 Iceberg 项目本身无任何代码影响。

影响范围：仅文档站点一个 markdown 文件，新增 4 行。对编译、运行时行为、API 兼容性均无影响。

回迁到 1.4.x 分支的注意事项：1.4.x 分支的 `site/docs/vendors.md` 内容通常比当前 main 分支旧，可能不包含 PuppyGraph 等较新的条目，页面顺序也可能不同。回迁时不应机械地按 main 分支的行号定位，而应按字母顺序找到 R 字母段对应位置插入。若 1.4.x 仍维护文档站点并希望反映最新生态，则可套用本提交；若 1.4.x 已不再发布文档更新，则本提交可跳过。本提交本身不引入功能变化，回迁风险极低，但需注意 1.4.x 的 vendors.md 中是否已存在 RisingWave 条目以避免重复。
