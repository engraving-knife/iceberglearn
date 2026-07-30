# 提交 2548：Spark 4.0: Fix source location in stats file copy plan in RewriteTablePathSparkAction (#13881)

## 提交信息

- **序号**：2548 / 4088
- **哈希**：b82dac4858fb2d15929a797660252c50f8e00ed8
- **短哈希**：b82dac485
- **日期**：2025-08-22 11:33:02 -0700
- **作者**：Anurag Mantripragada
- **提交说明**：Spark 4.0: Fix source location in stats file copy plan in RewriteTablePathSparkAction (#13881)
- **PR/Issue**：#13881

## 总体目的

`RewriteTablePathSparkAction` 用于重写表的所有文件路径（从 source prefix 迁移到 target prefix）。在生成"复制计划"（copy plan）时，需要为每个文件指定 `(源路径, 目标路径)` 对，源路径用于后续从源位置读取文件并复制到目标位置。

bug 出现在 `statsFileCopyPlan` 方法中：生成统计文件（statistics files，`.stats` 文件）的复制计划时，源路径错误地使用了 `RewriteTablePathUtil.stagingPath(before.path(), sourcePrefix, stagingDir)`——即把源文件路径映射到了一个"暂存路径"，而不是文件实际的源路径。这会导致后续复制阶段尝试从 staging 目录读取 stats 文件，但 stats 文件实际并未被复制到 staging（只有 manifest 等文件才会走 staging 重写流程），从而引发文件找不到或复制失败。

修复方式：把源路径改回 `before.path()`（统计文件的实际源路径），目标路径保持 `after.path()`（已重写 prefix 的目标路径）。这样复制计划正确反映"从源位置读 stats 文件，写到目标位置"。

测试新增 `testStatisticsFileSourcePath`：创建带统计文件的表，执行 `rewriteTablePath`，读取文件列表，找到 `.stats` 文件条目，断言源路径以源表 location 开头、包含 `/metadata/`、不包含 `staging`，目标路径以目标 location 开头。

## 如何达成设计目的

- 在 `statsFileCopyPlan` 中把 `Pair.of(RewriteTablePathUtil.stagingPath(before.path(), sourcePrefix, stagingDir), after.path())` 改为 `Pair.of(before.path(), after.path())`，使源路径指向统计文件在源表中的真实位置。
- 测试通过 `computeTableStats` 生成一个 `.stats` 文件，执行路径重写后从 `result.fileListLocation()` 读取路径对列表，过滤出 `.stats` 条目，验证源路径不指向 staging 而指向源表 metadata 目录。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java` (+1/-3)

**修改目的**：修复 stats 文件复制计划的源路径。

**工作逻辑**：`statsFileCopyPlan` 中 `result.add(Pair.of(before.path(), after.path()))`，不再调用 `RewriteTablePathUtil.stagingPath(...)` 把源路径映射到 staging 目录。统计文件不需要走 staging 重写，直接从源位置复制到目标位置即可。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java` (+50)

**修改目的**：验证 stats 文件源路径正确。

**工作逻辑**：
- `testStatisticsFileSourcePath`：创建 v2 表，`computeTableStats` 生成一个统计文件，断言 `sourceTable.statisticsFiles()` 数量为 1。
- 执行 `rewriteTablePath`，`checkFileNum(3, 1, 1, 1, 7, result)` 校验各类文件数量。
- 从 `result.fileListLocation()` 读取路径对列表，过滤出源路径以 `.stats` 结尾的条目。
- 断言源路径以 `sourceTableLocation` 开头、包含 `/metadata/`、不包含 `staging`；目标路径以 `targetTableLocation` 开头。

## 总结

修复 `RewriteTablePathSparkAction.statsFileCopyPlan` 中统计文件源路径错误地指向 staging 目录的 bug，改为使用 `before.path()`（源表中的真实路径），确保复制阶段能正确从源位置读取 stats 文件。新增测试验证源路径不指向 staging 而指向源表 metadata 目录、目标路径正确重写。
