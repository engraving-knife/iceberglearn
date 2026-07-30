# 提交 2726：Docs: Mention Hive 4.1

## 提交信息

- **序号**：2726 / 4088
- **哈希**：ca8fd16c4c3fb8c4f622d8758727e069ed8ccec5
- **短哈希**：ca8fd16c4
- **日期**：2025-10-09 00:02:55 -0700
- **作者**：Shohei Okumiya
- **提交说明**：Docs: Mention Hive 4.1
- **PR/Issue**：#14282

## 总体目的

Apache Hive 4.1 已发布，其中内嵌了 Iceberg 1.9.1 版本。Iceberg 的 Hive 文档需要更新，以反映 Hive 4.1 对 Iceberg 的集成支持情况。

Iceberg 的 Hive 文档说明了不同 Hive 版本与 Iceberg 的集成关系。从 Iceberg 1.8.0 开始，Iceberg 不再单独发布 Hive runtime connector，用户需要使用 Hive 自带的 Iceberg 集成（Hive 4.0.0+）或使用旧版 Iceberg 1.6.1 的 runtime connector。现在 Hive 4.1 发布，需要文档中添加对应说明。

## 如何达成设计目的

在 Hive 文档中，在 Hive 4.0.x 章节之前添加 Hive 4.1.x 章节，说明其内嵌的 Iceberg 版本。

## 修改详情

### `docs/docs/hive.md` (+4/-0 lines)

**修改目的**：添加 Hive 4.1.x 的文档说明。

**工作逻辑**：在已有的 Hive 4.0.x 章节之前插入新的 "Hive 4.1.x" 小节，内容为 "Hive 4.1.x comes with Iceberg 1.9.1 included."。这与现有的 Hive 4.0.x 说明格式一致（"Hive 4.0.x comes with Iceberg 1.4.3 included."），告知用户 Hive 4.1 内嵌的 Iceberg 版本为 1.9.1。

## 总结

此提交是简单的文档更新，在 Hive 集成文档中添加了 Hive 4.1.x 的说明，告知用户 Hive 4.1 内嵌了 Iceberg 1.9.1。这帮助使用 Hive 4.1 的用户了解他们可用的 Iceberg 版本。
