# 提交 1879：Docs: Update statements mentioning Hive's alpha/beta versions (#12430)

## 提交信息

- **序号**：1879 / 4088
- **哈希**：82044624da9d4e45c94b962659acd3db5378a8fa
- **短哈希**：82044624d
- **日期**：2025-03-19 11:19:13 +0100
- **作者**：Shohei Okumiya
- **提交说明**：Docs: Update statements mentioning Hive's alpha/beta versions (#12430)
- **PR/Issue**：#12430

## 总体目的

本提交更新 Hive 集成文档，移除对 Hive 4.0.0 alpha/beta 预发布版本的过时引用，统一以 Hive 4.0.x / Hive 4 / Hive 4.0.0 表述。

背景：Hive 4.0.0 已正式发布，文档中仍保留对 alpha-1、alpha-2、beta-1 等预发布版本的详细说明（以及各自捆绑的 Iceberg 版本），这些信息对用户已无实际价值，反而造成混淆。本提交精简这些过时内容。

## 如何达成设计目的

直接编辑 `docs/docs/hive.md`：
1. 将"Hive 4.0.0 / beta-1 / alpha-2 / alpha-1"四个小节合并为"Hive 4.0.x"一节，仅说明 Hive 4.0.x 捆绑 Iceberg 1.4.3。
2. 将正文中"Hive 4.0.0-alpha-1"改为"Hive 4"或"Hive 4.0.0"。
3. 微调查询引擎支持表述（Hive 4.x 支持 Tez）。

## 修改详情

### `docs/docs/hive.md` (修改, +6/-17 lines)

**修改目的**：移除 Hive alpha/beta 版本引用。

**工作逻辑**：
- 删除 Hive 4.0.0-beta-1、4.0.0-alpha-2、4.0.0-alpha-1 三个小节及其捆绑 Iceberg 版本说明，合并为"Hive 4.0.x comes with Iceberg 1.4.3 included."
- "Hive 4.0.0-alpha-1 provides the possibility to use STORED BY ICEBERG" 改为 "Hive 4 provides..."
- "supported only from Hive 4.0.0-alpha-1" 改为 "supported only from Hive 4.0.0"
- 查询引擎支持："With Hive 4.0.0-alpha-1 Tez..." 改为 "With Hive 4.x, the Tez..."

## 总结

本提交是文档清理，移除 Hive 4.0.0 预发布版本的过时引用，统一用 Hive 4.0.x / Hive 4 表述，精简文档。
