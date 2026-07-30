# 提交 3050：[doc] Add highlight note for Hadoop S3A FileSystem (#14913)

## 提交信息

- **序号**：3050 / 4088
- **哈希**：0651b8913d27c3b1c9aca4a9609bec521905fb36
- **短哈希**：0651b8913
- **日期**：2025-12-24
- **作者**：nhuantho
- **提交说明**：[doc] Add highlight note for Hadoop S3A FileSystem (#14913)
- **PR/Issue**：#14913

## 总体目的

Iceberg 在 AWS S3 场景下支持两种 FileIO 实现：一是官方推荐的 `S3FileIO`（直接使用 AWS SDK），二是通过 Hadoop S3A FileSystem 间接访问的 `HadoopFileIO`。在 `S3FileIO` 引入之前，很多用户使用 `HadoopFileIO` 写 S3。文档 `docs/docs/aws.md` 的 “Hadoop S3A FileSystem” 小节原本只是在一大段正文中间用一句话提到 “`S3FileIO` ... is thus recommended for S3 use cases rather than the S3A FileSystem”，这条关键建议被淹没在段落里，用户很容易忽略，从而继续使用性能与安全性都较差的 S3A FileSystem。

此提交的动机是把“推荐使用 S3FileIO 而非 S3A FileSystem”这一关键建议提升为一个醒目的高亮提示块（`!!! important`，MkDocs Material 的 admonition 语法），放在小节开头，确保用户在阅读该节第一眼就看到。同时对后续段落做了精简，把原本重复的“推荐”措辞去掉，避免与高亮块重复。

## 如何达成设计目的

在 `docs/docs/aws.md` 的 “Hadoop S3A FileSystem” 标题下新增一个 `!!! important` admonition 块，明确写出 `S3FileIO is recommended` 的建议；随后把原段落中已被高亮块覆盖的推荐语句删除，仅保留对 `S3FileIO` 采用最新 AWS 客户端与 S3 特性的背景说明。

## 修改详情

### `docs/docs/aws.md` (+3/-2 lines)

**修改目的**：为 S3A FileSystem 小节添加醒目推荐提示并精简正文。

**工作逻辑**：
- 在 `### Hadoop S3A FileSystem` 标题之后插入 admonition 块：
  ```
  !!! important
      **S3FileIO is recommended** for S3 use cases rather than the `S3A FileSystem` (`HadoopFileIO`).
  ```
  MkDocs Material 会将该块渲染为带醒目图标与配色的“重要”提示框，置于小节顶部。
- 原段落中 “As introduced in the previous sections, `S3FileIO` adopts the latest AWS clients and S3 features for optimized security and performance and is thus recommended for S3 use cases rather than the S3A FileSystem.” 被拆为两句：保留 “adopts the latest AWS clients and S3 features for optimized security and performance.”，删去重复的 “and is thus recommended ...” 部分（因推荐已在高亮块中给出）。

## 总结

此提交是一个文档可读性优化，通过 MkDocs admonition 把 “推荐 S3FileIO 而非 S3A FileSystem” 的关键建议从段落正文中提取为视觉醒目的高亮提示，帮助用户避免误用性能与安全性较弱的 HadoopFileIO 路径，实际指导价值在于减少 S3 接入踩坑。
