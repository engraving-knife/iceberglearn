# 提交 3139：Spark: Backport adding branch support to rewrite_data_files procedure (#15067)

## 提交信息

- **序号**：3139 / 4088
- **哈希**：15485f5523d08aae2a503c143c51b6df2debb655
- **短哈希**：15485f552
- **日期**：2026-01-21
- **作者**：Harsh Sharma
- **提交说明**：Spark: Backport adding branch support to rewrite_data_files procedure (#15067)
- **PR/Issue**：#15067（回移自 #14964）

## 总体目的

本提交是 main 分支 PR #14964 的回移，目标为 Spark 1.4.x 维护分支（覆盖 `spark/v3.4`、`spark/v3.5`、`spark/v4.0` 三个版本）。要解决的问题是：Spark 的 `system.rewrite_data_files` 存储过程及其底层 `RewriteDataFilesSparkAction` 此前只能对表的 `main` 分支做数据文件压缩/重写，无法把重写结果提交到指定分支。在采用分支做 WAP（Write-Audit-Publish）或增量发布的场景下，用户希望直接在某个分支上做 compaction，且不污染 main 分支的快照。此前 action 的 `execute()` 固定以 `table.currentSnapshot().snapshotId()` 作为起始快照、commit manager 也固定提交到 main，因此分支化重写无从实现。

本提交为 `RewriteDataFilesSparkAction` 增加 `toBranch(String)` 方法与 `branch` 字段（默认 `SnapshotRef.MAIN_BRANCH` 保持兼容），在 `execute()` 中校验目标分支存在并以该分支快照作为重写起点，把 `branch` 传给 `RewriteDataFilesCommitManager` 使提交落到目标分支；同时在 `RewriteDataFilesProcedure` 中新增可选 `branch` 参数，并按“显式参数 > 表当前分支 > main 分支”的优先级解析目标分支，使存储过程调用方既能显式指定分支，也能复用 Spark 表会话当前分支。配套在三个 Spark 版本的扩展测试中新增 4 个用例覆盖分支重写、null 分支报错、带过滤的分支重写、分支压缩不影响 main 等场景。

## 如何达成设计目的

整体思路是把“目标分支”这一维度贯穿 action 与 procedure 两层：action 层新增 `branch` 状态与 `toBranch` setter，并把 `execute()` 中对 `currentSnapshot()` 的依赖改为 `table.snapshot(branch)`，同时把 branch 注入 commit manager；procedure 层新增 `BRANCH_PARAM`，解析时先取过程参数，回退到 `loadSparkTable(tableIdent).branch()`（Spark 表当前分支），再回退到 `SnapshotRef.MAIN_BRANCH`。由于 `RewriteDataFiles` 接口的 `filter`/`binPack`/`sort` 等方法返回的是父接口类型，v3.4/v3.5 通过显式 `(RewriteDataFilesSparkAction)` 强转回具体类型以调用 `toBranch`，v4.0 则依赖协变返回类型直接链式调用。涉及 9 个文件，分布在三个 Spark 版本目录。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (+14/-3 lines)

**修改目的**：让重写 action 支持提交到指定分支。

**工作逻辑**：
- 新增字段 `private String branch = SnapshotRef.MAIN_BRANCH;`，默认 main 分支以保持旧行为。
- 新增 `public RewriteDataFilesSparkAction toBranch(String targetBranch)`，校验 `targetBranch != null` 后赋值并返回 `this`，支持链式调用。
- `execute()` 中把原先 `long startingSnapshotId = table.currentSnapshot().snapshotId();` 改为先 `Preconditions.checkArgument(table.snapshot(branch) != null, "Cannot rewrite data files for branch %s: branch does not exist", branch)`，再 `long startingSnapshotId = table.snapshot(branch).snapshotId();`，即以目标分支的快照为重写起点。
- `commitManager(long)` 构造 `RewriteDataFilesCommitManager` 时追加 `branch` 参数，使提交落到目标分支而非 main。该改动与 v3.4/v3.5 一致。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteDataFilesProcedure.java` (+18/-6 lines)

**修改目的**：为存储过程新增 `branch` 参数并解析目标分支。

**工作逻辑**：
- 新增 `BRANCH_PARAM = optionalInParameter("branch", DataTypes.StringType)` 并加入 `PARAMETERS` 数组（位于末尾，向后兼容）。
- 解析时按优先级取分支：`branchParam = input.asString(BRANCH_PARAM, null)`；若为 null 则 `branchParam = loadSparkTable(tableIdent).branch()`（Spark 表当前分支）；若仍为 null 则 `branchParam = SnapshotRef.MAIN_BRANCH`。
- 把 action 类型由接口 `RewriteDataFiles` 收窄为具体 `RewriteDataFilesSparkAction`，调用 `actions().rewriteDataFiles(table).options(options).toBranch(branch)`。
- `checkAndApplyFilter` 与 `checkAndApplyStrategy` 的入参/返回类型由 `RewriteDataFiles` 改为 `RewriteDataFilesSparkAction`，便于链式调用 `toBranch` 后仍能继续 `filter`/`binPack`/`sort`；`binPack()` 后的局部变量也改为具体类型。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteDataFilesProcedure.java` (+26/-6 lines)

**修改目的**：在 Spark 3.4 同步分支支持，因接口方法返回父类型需显式强转。

**工作逻辑**：
与 v4.0 逻辑完全一致，但 `actions().rewriteDataFiles(table).options(options).toBranch(branch)` 返回的是 `RewriteDataFiles` 接口（`toBranch` 在 v3.4/v3.5 的 action 上返回类型仍是父接口，或 `filter`/`binPack` 等返回父接口），因此在调用 `checkAndApplyStrategy`、`checkAndApplyFilter` 后需要 `(RewriteDataFilesSparkAction)` 显式强转回具体类型。这正是该文件改动行数（32）多于 v4.0（24）的原因。`BRANCH_PARAM`、分支解析优先级、`toBranch` 调用与 v4.0 相同。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteDataFilesProcedure.java` (+18/-6 lines)

**修改目的**：在 Spark 3.5 同步分支支持。

**工作逻辑**：
与 v3.4/v4.0 对应文件逻辑一致，强转处理与具体版本相匹配。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (+14/-3 lines)

**修改目的**：v3.4 action 支持目标分支。

**工作逻辑**：与 v4.0 action 改动一致：`branch` 字段、`toBranch`、`execute()` 校验分支并以分支快照为起点、commit manager 传 `branch`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (+14/-3 lines)

**修改目的**：v3.5 action 支持目标分支。

**工作逻辑**：与 v3.4/v4.0 action 改动一致。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+207/-0 lines)

**修改目的**：为分支化重写提供端到端测试。

**工作逻辑**：
新增 4 个 `@TestTemplate` 用例：
- `testRewriteDataFilesOnBranch`：建表插入 10 条数据，`ALTER TABLE ... CREATE BRANCH testBranch`，记录 main 与分支快照 ID，`CALL ...rewrite_data_files(table=>..., branch=>'testBranch')`，断言重写 10 个文件并新增 1 个、分支数据不变、分支快照 ID 改变、main 快照 ID 不变。
- `testRewriteDataFilesToNullBranchFails`：`SparkActions.get(spark).rewriteDataFiles(table).toBranch(null)` 抛 `IllegalArgumentException("Invalid branch name: null")`。
- `testRewriteDataFilesOnBranchWithFilter`：对分区表创建分支，带 `where => 'c2 = "bar"'` 过滤重写，验证仅命中分区被重写、数据不变、main 不受影响。
- `testBranchCompactionDoesNotAffectMain`：验证分支压缩后 main 分支的文件数/快照不变，确认隔离性。

### `spark/v3.4/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+206/-0 lines)

**修改目的**：在 Spark 3.4 同步上述分支重写测试。

**工作逻辑**：与 v4.0 测试用例对应，仅按版本差异做极小调整（行数差 1）。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+206/-0 lines)

**修改目的**：在 Spark 3.5 同步上述分支重写测试。

**工作逻辑**：与 v3.4 测试基本一致。

## 总结

该回移提交为 Spark `rewrite_data_files` 存储过程与底层 action 增加了分支感知能力，使数据文件压缩可针对指定分支执行并提交到该分支而不影响 main，默认仍走 main 分支以保持兼容，并通过三个 Spark 版本的端到端测试覆盖了分支重写、过滤重写、隔离性与非法分支报错等关键场景，完善了 Iceberg 分支模型在 Spark compaction 工作流中的可用性。
