# 提交 0946：Docs: Add examples for DataFrame branch writes (#10644)

## 提交信息

- **序号**：0946 / 4088
- **哈希**：319f29ea860e42e7cc21cda8c05d882134e6431f
- **短哈希**：319f29ea8
- **日期**：2024-07-17 23:37:10 +0530
- **作者**：Anurag Mantripragada <amantripragada@apple.com>
- **提交说明**：Docs: Add examples for DataFrame branch writes (#10644)
- **PR/Issue**：#10644

## 总体目的

Iceberg 的 Spark 写入文档 `docs/docs/spark-writes.md` 中"Writing to Branches"一节原本只展示了通过 SQL（含 WAP 工作流）向分支写入数据的方式，缺少通过 Spark DataFrame API（`DataFrameWriterV2`）向分支写入的示例。对于不熟悉 Iceberg 分支标识符语法（`prod.db.table.branch_xxx`）的用户而言，难以从文档直观判断 DataFrame 写分支是否被支持、以及具体如何调用。

本提交（PR #10644）补充 DataFrame 分支写入的 Scala 示例，并对该节结构进行重组：将 SQL 与 DataFrame 两种写入路径分小节呈现，明确"分支必须先存在、写入操作不会自动创建分支"，并指向 Spark DDL 文档中关于分支/标签的创建语法。这属于文档可读性与完整性改进，使分支写入的两种主流入口都有可复制的代码样例。

## 如何达成设计目的

通过纯文档编辑实现：
1. 将原"Writing to Branches"段落中关于"分支必须预先存在、操作不会创建分支"的说明前置并补充一条指向 `spark-ddl.md#branching-and-tagging-ddl` 的链接，指导用户先用 DDL 创建分支；
2. 新增 `### Via SQL` 子标题，把原有 SQL（含 WAP）示例归入其下；
3. 新增 `### Via DataFrames` 子标题，给出两段 Scala 示例：分别演示 `data.writeTo("prod.db.table.branch_audit").append()`（向分支追加）与 `data.writeTo("prod.db.table.branch_audit").overwritePartitions()`（覆盖分支分区）。

## 修改详情

### `docs/docs/spark-writes.md`

**修改目的**：为"Writing to Branches"章节补充 DataFrame 分支写入示例，并重组为 SQL / DataFrame 两个子节，提升文档完整性。

**工作逻辑**：改动集中在"Writing to Branches"段落（约 195–252 行）：

- 段首改写：先强调"分支必须先存在、操作不会创建分支"，并新增指向 Spark DDL 分支/标签语法的链接：
  ```markdown
  The branch must exist before performing write. Operations do **not** create the branch if it does not exist.
  A branch can be created using [Spark DDL](spark-ddl.md#branching-and-tagging-ddl).
  ```
- 新增 `### Via SQL` 子节，归并原有 SQL 与 WAP 示例说明；
- 新增 `### Via DataFrames` 子节，含两段示例：
  ```scala
  // To insert into `audit` branch
  val data: DataFrame = ...
  data.writeTo("prod.db.table.branch_audit").append()
  ```
  ```scala
  // To overwrite `audit` branch
  val data: DataFrame = ...
  data.writeTo("prod.db.table.branch_audit").overwritePartitions()
  ```
  通过在表名后追加 `.branch_audit` 分支标识符，`DataFrameWriterV2` 即可将数据写入指定分支，与 SQL 路径的 `branch_audit` 标识符语义一致。

## 小结

- **成效**：补齐了 Spark 分支写入文档中缺失的 DataFrame 示例，并改善章节结构，使用户能并行参考 SQL 与 DataFrame 两种写入方式。
- **影响范围**：仅 `docs/docs/spark-writes.md` 一个文档文件，25 行新增、6 行删除，无代码变更。
- **回迁到 1.4.x 的注意事项**：纯文档改动，可安全回迁到 1.4.x（前提是 1.4.x 的 Spark 版本同样支持 `DataFrameWriterV2` 与分支标识符语法，1.4.x 对应 Spark 3.3/3.4/3.5 均已支持）。回迁时注意 1.4.x 文档基线中该章节的原始结构可能略有差异，应按上下文调整插入位置。
