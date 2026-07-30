# 提交 2650：Add StreamNative to the vendor list (#14097)

## 提交信息

- **序号**：2650 / 4088
- **哈希**：7a4b69fff4e3359df831008b5a5722a531e51af9
- **短哈希**：7a4b69fff
- **日期**：2025-09-17 23:27:06 -0400
- **作者**：Hang Chen
- **提交说明**：Add StreamNative to the vendor list (#14097)
- **PR/Issue**：#14097

## 总体目的

本提交将 StreamNative 公司添加到 Apache Iceberg 官方文档的供应商列表（vendors）中。Iceberg 的 vendors 页面列出了支持或集成 Apache Iceberg 表格式的商业产品与平台，便于用户了解生态系统中可用的解决方案。

StreamNative 提供的数据流平台由 Ursa 引擎驱动，该引擎是 Kafka 兼容、无领导者（leaderless）且 lakehouse 原生的流式引擎。Ursa 直接将数据写入云对象存储上的 Apache Iceberg 表，无需专门的连接器，并自动进行数据压缩与提交，使得数据可立即被 Spark、Trino、Flink 等引擎查询。将其纳入供应商列表有助于提升其作为 Iceberg 生态参与者的可见度。

这是一次纯文档更新，不涉及任何代码逻辑变更。

## 如何达成设计目的

通过在 `site/docs/vendors.md` 文件中，于 Starburst 条目之后、Tinybird 条目之前，新增一个 StreamNative 的二级标题段落，描述其产品 Ursa 的特性及与 Iceberg 的集成方式，并附带官方链接与 VLDB 论文链接。

## 修改详情

### `site/docs/vendors.md` (+4/-0 lines)

**修改目的**：在供应商列表中添加 StreamNative 条目。

**工作逻辑**：在文档中 Starburst 段落与 Tinybird 段落之间插入了一个新的 `### [StreamNative](https://streamnative.io)` 段落。内容包括对 Ursa 引擎的介绍——Kafka 兼容、leaderless、lakehouse-native 流式引擎，直接写入 Iceberg 表，自动压缩和提交数据，可被 Spark/Trino/Flink 立即查询，并附上了 VLDB 论文链接作为参考。

## 总结

这是一次简单的文档维护更新，将 StreamNative 及其 Ursa 产品纳入 Iceberg 官方供应商生态列表，扩大了 Iceberg 生态的可见性，不涉及任何代码或行为变更。
