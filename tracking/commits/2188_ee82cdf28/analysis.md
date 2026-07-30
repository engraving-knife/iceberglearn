# 提交 2188：Docs: Reorganize Spark Time Travel Doc (#13113)

## 提交信息

- **序号**：2188 / 4088
- **哈希**：ee82cdf287a304deb58b07aa44cefdad5f9c90da
- **短哈希**：ee82cdf28
- **日期**：2025-06-02 14:21:48 +0530
- **作者**：Bhargavkonidena
- **提交说明**：Docs: Reorganize Spark Time Travel Doc (#13113)
- **PR/Issue**：#13113

## 总体目的

此提交重新组织 Spark 查询文档中 Time Travel（时间旅行）相关内容的结构。在原来的文档组织中，"Querying with DataFrames" 和 "Time travel" 部分被放在文件靠前的位置，而 Time Travel 的 SQL 和 DataFrame 内容被分开。重新组织后，Time Travel 的 SQL 部分被提前并作为 files 表格之后的直接内容，DataFrame 查询部分（包括 Catalogs with DataFrameReader 和 Time Travel with DataFrame）被整合到一起放在后面。这样的组织方式更加合理，先介绍 SQL 的时间旅行查询，再介绍 DataFrame 的查询，逻辑更清晰。

## 如何达成设计目的

- 将 "Querying with DataFrames" 和 "Catalogs with DataFrameReader" 部分从文件前部移到后部
- 将 Time Travel SQL 部分从原来的 "#### SQL" 子节提升为 "### Time travel Queries with SQL" 子节
- 将 Time Travel DataFrame 部分从原来的 "#### DataFrame" 子节提升为 "### Time travel Queries with DataFrame" 子节
- 调整各部分的顺序为：files 表格 -> Time Travel SQL -> Querying with DataFrames -> Catalogs with DataFrameReader -> Time Travel DataFrame

## 修改详情

### `docs/docs/spark-queries.md` (修改, +27/-29 lines)

**修改目的**：重新组织文档结构，改善可读性。

**工作逻辑**：
- 从文件前部删除 "Querying with DataFrames"、"Catalogs with DataFrameReader" 和 "Time travel" 的标题及内容（约 27 行）
- 在 SQL Time Travel 内容之前添加 "### Time travel Queries with SQL" 标题
- 在 SQL Time Travel 内容之后添加 "## Querying with DataFrames" 和 "### Catalogs with DataFrameReader" 的内容（之前删除的内容）
- 在 DataFrame Catalogs 内容之后添加 "### Time travel Queries with DataFrame" 标题（替代原来的 "#### DataFrame"）

## 总结

此提交重新组织了 Spark 查询文档中 Time Travel 相关内容的结构，将 SQL 时间旅行查询提前到 files 表格之后，将 DataFrame 相关内容（包括 Catalogs 和 Time Travel）整合到一起放在后面。这改善了文档的逻辑结构和可读性。
