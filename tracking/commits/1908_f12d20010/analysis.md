# 提交 1908：Spark 3.4: Backport Spark actions changes in Spark rewrite_table_path procedure (#12568)

## 提交信息

- **序号**：1908 / 4088
- **哈希**：f12d200108cc95b38fe0edd9113f807b47648656
- **短哈希**：f12d20010
- **日期**：2025-03-24 08:11:12 +0100
- **作者**：slfan1989
- **提交说明**：Spark 3.4: Backport Spark actions changes in Spark rewrite_table_path procedure (#12006 #12172 #11929 #12282 #12569) (#12568)
- **PR/Issue**：#12568（backport #12006, #12172, #11929, #12282, #12569）

## 总体目的

这个提交将 5 个与 `RewriteTablePath`（表路径重写）相关的改进从 Spark 3.5/main 分支向后移植到 Spark 3.4。`RewriteTablePath` 是 Iceberg 的一个 action，用于将表的文件路径前缀从一个位置替换到另一个位置（例如从 S3 迁移到本地存储），常用于表数据迁移场景。

本次 backport 包含以下 5 个改进：

1. **#12006 - 排除已删除的内容文件**：在 `RewriteTablePathUtil` 的复制计划中排除已删除的内容文件，避免复制不再需要的文件。

2. **#12172 - 修复增量复制的 bug**：修复 `RewriteTablePath` 增量复制模式下的问题。原代码中 `jobDesc()` 方法的条件判断逻辑有误——`if (startVersionName != null)` 应为 `if (startVersionName == null)`，导致增量复制时显示了错误的 job 描述。

3. **#11929 - 支持统计文件**：此前 `RewriteTablePath` 不支持统计文件（statistics files），会直接抛出异常拒绝处理。此改进将统计文件纳入复制计划，使表路径重写时统计文件也能被正确迁移。同时区分了 `statisticsFiles`（支持）和 `partitionStatisticsFiles`（仍不支持）。

4. **#12282 - 修复 job 描述**：与 #12172 相关的 job 描述修复。

5. **#12569 - 改进断言**：改进测试中的断言，便于调试。

## 如何达成设计目的

整体设计思路是在 `RewriteTablePathSparkAction` 中：

1. 修复 `jobDesc()` 方法的条件判断（`!= null` → `== null`）
2. 将统计文件校验从 `statisticsFiles` 改为 `partitionStatisticsFiles`（前者现在被支持）
3. 将 `rewriteVersionFile` 方法的返回类型从 `Pair<String, String>` 改为 `Set<Pair<String, String>>`，使其能返回多个文件路径对（版本文件 + 统计文件）
4. 新增 `statsFileCopyPlan` 方法，将统计文件纳入复制计划
5. 更新测试用例

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (修改, +49 lines)

**修改目的**：修复增量复制 bug、支持统计文件、改进 job 描述。

**工作逻辑**：

1. **jobDesc() 修复**：将 `if (startVersionName != null)` 改为 `if (startVersionName == null)`，修正增量复制模式下的 job 描述判断逻辑。

2. **统计文件校验调整**：将 `endMetadata.statisticsFiles()` 的空检查改为 `endMetadata.partitionStatisticsFiles()` 的空检查。即原来的 "Statistic files are not supported yet" 改为 "Partition statistics files are not supported yet"，意味着普通统计文件现在被支持，只有分区统计文件仍不支持。

3. **rewriteVersionFile 方法重构**：返回类型从 `Pair<String, String>` 改为 `Set<Pair<String, String>>`。除了版本文件的路径对，还通过 `statsFileCopyPlan()` 方法将统计文件路径对添加到结果集中。

4. **新增 statsFileCopyPlan 方法**：接收重写前后的统计文件列表，验证两者数量和文件大小一致，然后为每个统计文件生成一个路径对（staging 路径 → 目标路径）。

5. **copyPlan 调用适配**：由于 `rewriteVersionFile` 返回类型从单个 Pair 改为 Set，调用处从 `.add()` 改为 `.addAll()`。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (修改, +289/-59 lines)

**修改目的**：新增和更新测试用例验证上述 5 个改进。

**工作逻辑**：大幅扩展测试覆盖：
- 验证统计文件在路径重写后被正确复制
- 验证增量复制模式的正确性
- 验证已删除内容文件的排除
- 改进断言以提供更好的调试信息

## 总结

本提交将 5 个 RewriteTablePath 相关的改进 backport 到 Spark 3.4，包括修复增量复制条件判断 bug、支持统计文件迁移、区分普通统计文件和分区统计文件的支持状态。核心改动是 `rewriteVersionFile` 方法返回类型扩展为 Set，使其能同时返回版本文件和统计文件的路径对。
