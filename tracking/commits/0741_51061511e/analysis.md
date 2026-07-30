# 提交 0741：Docs: Update features for Hive 4.0 (#10162)

## 提交信息

- **序号**：0741 / 4088
- **哈希**：51061511edd953cc0456b7e2b8e8c7dff7567795
- **短哈希**：51061511e
- **日期**：2024-05-03 15:24:24 +0530
- **作者**：Sourabh Badhya
- **提交说明**：Docs: Update features for Hive 4.0 (#10162)
- **PR/Issue**：#10162
- **共同作者**：Sourabh Badhya <sbadhya@cloudera.com>

## 总体目的

本提交对 Iceberg 的 Hive 集成文档（`docs/docs/hive.md`）进行大规模更新，使其反映 Hive 4.0 正式版（GA）相对 Hive 2.x/3.x 新增的 Iceberg 功能支持。在本次更新前，文档将 Hive 4.0 的特性分散描述为 "4.0.0-alpha-2 and above" 与 "4.0.0-alpha-1 and above" 两个阶段，且仅罗列了部分特性，未覆盖 Hive 4.0 最终版带来的全部新能力（如 DELETE/UPDATE/MERGE INTO、分支与标签、分区级 DML、表压缩等）。

此次更新的核心动机是 Hive 4.0 已经正式发布（GA），Iceberg 在 Hive 4 中内置的版本也已确定（1.4.3），因此文档需要从"按 alpha 版本分段描述"转变为"统一以 Hive 4.0 GA 为基线描述特性矩阵"，并补齐所有新增 SQL 操作的用法示例。这对用户准确了解 Hive 4 与 Iceberg 的集成能力边界至关重要。

## 如何达成设计目的

整体设计思路是纯文档改写，不涉及任何代码变更，改动集中在 `docs/docs/hive.md` 单个文件（276 行新增、21 行删除）。改动按以下几个层次展开：

1. **新增特性支持矩阵**：在 "Feature support" 章节顶部插入一个 Markdown 表格，以 Hive 2/3 与 Hive 4 两列横向对比各项特性的支持情况，并每项都带有跳转到对应详细章节的锚点链接。这让用户能一目了然地看到 Hive 4 相对旧版新增了哪些能力（DELETE FROM、UPDATE、MERGE INTO、Branches and tags）。

2. **统一特性列表**：删除原先按 alpha-2/alpha-1 分段的两段特性列表，合并为一段 "Hive supports the following additional features with Hive version 4.0.0 and above" 的统一列表，并把原先 alpha-2 段落中的特性（expire snapshots、CTLT、parquet compression、metadata location、rollback、sort orders）合并进来，同时新增大量 Hive 4.0 GA 才有的特性条目（branch/tag 增删、cherry-pick、fast-forward、orphan files 删除、表压缩、SHOW PARTITIONS 等）。

3. **新增版本说明**：新增 "Hive 4.0.0" 小节，明确说明 Hive 4.0.0 内置 Iceberg 1.4.3。

4. **补充新 SQL 操作的详细用法**：新增多个子章节，包含 DROP PARTITION、Branches and tags（CREATE BRANCH / CREATE TAG / DROP BRANCH / DROP TAG / FAST-FORWARD / CHERRY-PICK）、TRUNCATE TABLE PARTITION、DELETE FROM、UPDATE、MERGE INTO、INSERT INTO/OVERWRITE PARTITION、分支级 SELECT 与 INSERT、Compaction 等操作的完整 SQL 语法与示例。

5. **更新元数据表列表**：将原本仅列 4 个元数据表（files/entries/snapshots/manifests/partitions）扩展为 12 个，覆盖 Hive 4 新增的 all_data_files、all_delete_files、all_entries、all_files、all_manifests、data_files、delete_files、metadata_log_entries、refs 等。

## 修改详情

### `docs/docs/hive.md`

**修改目的**：将 Hive 集成文档从 alpha 版本分段描述更新为 Hive 4.0 GA 统一描述，并补齐所有新增特性的文档说明。

**工作逻辑**（按文档结构自上而下）：

1. **Feature support 章节顶部新增特性矩阵**：插入一个 11 行的 Markdown 表格，列出 SQL create table、CTAS、CTLT、drop table、insert into、insert overwrite、delete from、update、merge into、branches and tags 十个特性在 Hive 2/3 与 Hive 4 两列的支持情况（用 ✔️ 标记），每行带锚点链接。其中 delete from / update / merge into / branches and tags 仅 Hive 4 支持。

2. **合并并扩充特性列表**：
   - 删除原 "HiveCatalog supports the following additional features with Hive version 4.0.0-alpha-2 and above" 段落（含 expire snapshots、CTLT、parquet compression、metadata location、rollback、sort orders 6 项）。
   - 删除原 "With Hive version 4.0.0-alpha-1 and above, ... HiveCatalog supports" 段落的限定语，改为 "Hive supports the following additional features with Hive version 4.0.0 and above"。
   - 将原 alpha-2 的 6 项特性合并进新列表，并新增约 15 项 Hive 4 GA 特性：copy-on-write delete/update/merge 与 V1 表 CRUD、truncate/drop partition、branch/tag 创建写入与删除、按 snapshot ID / 时间范围 / 保留 N 个 / 表属性过期快照、set current snapshot、rename table、alter 转 Iceberg、fast-forward、cherry-pick、从 tag 创建 branch、按 branch/tag set current snapshot、删除孤儿文件、全表压缩、SHOW PARTITIONS。

3. **新增 Hive 4.0.0 版本小节**：在版本说明区新增 "Hive 4.0.0 comes with the Iceberg 1.4.3 included."，置于已有的 4.0.0-beta-1（Iceberg 1.3.0）之前。

4. **ALTER TABLE 章节新增 RENAME TABLE**：在 "Add a column" 之前新增 `ALTER TABLE orders RENAME TO renamed_orders;` 示例。

5. **新增 DROP PARTITIONS 子章节**：说明 `ALTER TABLE orders DROP PARTITION (...)` 支持按单/多分区规格删除，并注明仅支持 identity 分区列、不支持 transform 列。

6. **新增 Branches and tags 子章节**（文档中最大的一块新增内容）：
   - `CREATE BRANCH`：5 种创建方式（默认、指定 snapshot ID、系统时间、保留 N 个快照、从指定 tag），每种均给出 SQL 示例。
   - `CREATE TAG`：3 种创建方式（默认、指定 snapshot ID、系统时间）。
   - `DROP BRANCH`：含 IF EXISTS 选项。
   - `DROP TAG`：含 IF EXISTS 选项。
   - `EXECUTE FAST-FORWARD`：将一个分支快进到另一个分支/main 的状态。
   - `EXECUTE CHERRY-PICK`：按 snapshot ID cherry-pick 到 main 分支。

7. **TRUNCATE TABLE 章节扩充**：新增 `TRUNCATE TABLE ... PARTITION` 子章节，给出分区级截断示例，注明仅支持 identity 分区列。

8. **新增 SELECT on branches 说明**：说明分支查询需用 `<database>.<table>.branch_<branch>` 格式，给出 SELECT 示例。

9. **INSERT INTO 章节扩充**：
   - 新增分支级 INSERT INTO 说明与示例（`INSERT INTO default.test.branch_branch1`）。
   - 新增 `INSERT INTO ... PARTITION` 子章节，给出分区级 INSERT 示例。

10. **新增 INSERT OVERWRITE ... PARTITION 子章节**：给出分区级 INSERT OVERWRITE 示例。

11. **新增 DELETE FROM 章节**：说明 Hive 4 支持 DELETE FROM，给出 3 种示例（范围过滤、IN 子查询、聚合子查询），说明匹配整分区时走 metadata-only 删除、匹配行时重写受影响数据文件。

12. **新增 UPDATE 章节**：说明 Hive 4 支持 UPDATE，给出 3 种示例，并引导至 MERGE INTO 章节。

13. **新增 MERGE INTO 章节**：说明 Hive 4 支持 MERGE INTO，给出语法框架与 WHEN MATCHED / WHEN NOT MATCHED 子句的详细示例。

14. **更新元数据表列表**：从 5 个（files/entries/snapshots/manifests/partitions）扩展为 12 个，新增 all_data_files、all_delete_files、all_entries、all_files、all_manifests、data_files、delete_files、metadata_log_entries、refs。

15. **新增 Compaction 章节**：说明 Hive 4 支持全表压缩，给出两种等价语法 `ALTER TABLE t COMPACT 'major'` 与 `OPTIMIZE TABLE t REWRITE DATA`。

## 小结

- **成效**：文档从 Hive 4.0 alpha 阶段的碎片化描述升级为 Hive 4.0 GA 的完整特性文档，新增特性矩阵、约 15 项新特性的 SQL 用法示例与元数据表列表，使用户能准确了解 Hive 4 与 Iceberg 的集成能力。
- **影响范围**：仅文档，不涉及任何代码或构建配置变更。影响所有通过 Hive 使用 Iceberg 的用户对功能边界的认知。
- **回迁注意事项**：纯文档变更，回迁到 1.4.x 无技术风险。但需注意文档中提到的 "Hive 4.0.0 comes with Iceberg 1.4.3" 这一版本绑定关系——1.4.x 分支本身即对应 Iceberg 1.4.x 版本线，文档内容与 1.4.x 的 Hive 集成能力是匹配的，回迁后文档与代码能力一致，无需额外适配。
