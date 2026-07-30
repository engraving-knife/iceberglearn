# 提交 3118：Spark: Add branch support to rewrite_data_files procedure (#14964)

## 提交信息

- **序号**：3118 / 4088
- **哈希**：b4ef17cd16323e748cb97c559ec90acfa3fbf395
- **短哈希**：b4ef17cd1
- **日期**：2026-01-16 14:51:35 +0100
- **作者**：Harsh Sharma
- **提交说明**：Spark: Add branch support to rewrite_data_files procedure (#14964)
- **PR/Issue**：#14964

## 总体目的

Iceberg 的分支（branch）特性允许在同一个表上维护多条独立的快照线，常用于 WAP（Write-Audit-Publish）等场景。然而 Spark 提供的 `rewrite_data_files` 系统存储过程此前只能对表的 `main` 分支做数据文件压缩/重写，无法指定目标分支。这意味着用户若想对某个分支上的数据文件做合并压缩，只能先切换或额外操作，无法直接通过过程调用完成，限制了分支特性的实用性。

本提交为 `rewrite_data_files` 过程新增 `branch` 参数，并贯穿整个调用链——从过程层、Spark action 层一直到 core 的 commit manager——使数据文件重写结果可以提交到任意已存在的分支上，同时确保 main 分支（及其他分支）的快照不受影响。这正是分支语义所要求的隔离性。

## 如何达成设计目的

整体分三层贯通：(1) 在 core 的 `RewriteDataFilesCommitManager` 增加 `branch` 字段并在执行 `Rewrite` 操作时调用 `rewrite.toBranch(branch)` 指定提交目标分支；(2) 在 Spark action `RewriteDataFilesSparkAction` 增加 `toBranch()` 方法，将 `execute()` 中起始快照从 `currentSnapshot()` 改为基于 `table.snapshot(branch)`，并把 branch 传给 commit manager；(3) 在 Spark 过程 `RewriteDataFilesProcedure` 新增可选参数 `branch`，按"显式参数 > 表 branch > main"的优先级解析后调用 `toBranch(branch)`。最后补充 4 个测试用例覆盖分支压缩、过滤、null 校验与分支隔离。

## 修改详情

### `core/src/main/java/org/apache/iceberg/actions/RewriteDataFilesCommitManager.java` (+15/-0 lines)

**修改目的**：让 commit manager 支持把重写结果提交到指定分支。

**工作逻辑**：
新增 `private final String branch;` 字段。原四参数构造器委托给新的五参数构造器（多一个 `String branch`），新构造器中 `this.branch = branch`。关键改动在 commit 流程中：`snapshotProperties.forEach(rewrite::set)` 之后新增 `if (branch != null) { rewrite.toBranch(branch); }`，即当指定分支时把 `Rewrite` 操作的目标分支设置好再 `commit()`。这样重写产生的新快照会挂在指定分支而非 main。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteDataFilesSparkAction.java` (+13/-4 lines)

**修改目的**：让 Spark action 支持目标分支，并基于分支快照确定重写起点。

**工作逻辑**：
新增 `private String branch = SnapshotRef.MAIN_BRANCH;`（默认 main 分支，保持向后兼容）。新增 `toBranch(String targetBranch)` 方法，用 `Preconditions.checkArgument(targetBranch != null, "Invalid branch name: null")` 校验后设置 `this.branch`。`execute()` 中原先 `long startingSnapshotId = table.currentSnapshot().snapshotId();` 改为先校验 `table.snapshot(branch) != null`（分支不存在则抛 `IllegalArgumentException`），再 `table.snapshot(branch).snapshotId()` 取起点快照——这是保证重写作用于正确分支快照的关键。`commitManager(long startingSnapshotId)` 中构造 `RewriteDataFilesCommitManager` 时把 `branch` 作为第五个参数传入，使提交阶段也落到同一分支。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/procedures/RewriteDataFilesProcedure.java` (+25/-7 lines)

**修改目的**：为存储过程新增 `branch` 可选参数并解析优先级。

**工作逻辑**：
新增 `BRANCH_PARAM = optionalInParameter("branch", DataTypes.StringType)`，并加入 `PARAMETERS` 数组末尾。在 `call` 方法中按优先级解析分支：`branchParam = input.asString(BRANCH_PARAM, null)`；若为 null 则取 `loadSparkTable(tableIdent).branch()`（表自身绑定的分支）；若仍为 null 则回退 `SnapshotRef.MAIN_BRANCH`。随后把 action 类型从接口 `RewriteDataFiles` 收窄为具体类 `RewriteDataFilesSparkAction`，并链式调用 `.toBranch(branch)`。`checkAndApplyFilter` 与 `checkAndApplyStrategy` 的形参/返回类型也相应改为 `RewriteDataFilesSparkAction`，以保留 `toBranch` 链式调用（这些方法内部 `binPack()`、`filter()`、`sort()` 返回的也是该具体类型）。`binpack` 分支中将局部变量 `rewriteDataFiles` 重命名为 `binPackAction`，逻辑不变。

### `spark/v4.1/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRewriteDataFilesProcedure.java` (+206/-0 lines)

**修改目的**：覆盖分支重写的功能正确性与隔离性。

**工作逻辑**：
新增 4 个测试：
- `testRewriteDataFilesOnBranch`：建表插入 10 条数据后创建分支，调用 `rewrite_data_files(..., branch => 'testBranch')`，断言重写 10 个文件并产出 1 个文件；分支数据不变、分支快照更新、main 快照不变。
- `testRewriteDataFilesToNullBranchFails`：直接用 `SparkActions.get(spark).rewriteDataFiles(table).toBranch(null)`，断言抛 `IllegalArgumentException` 且消息为 `Invalid branch name: null`。
- `testRewriteDataFilesOnBranchWithFilter`：在分区表上对分支带 `where => 'c2 = "bar"'` 过滤重写，验证只重写匹配分区的文件，分支快照更新而 main 不变。
- `testBranchCompactionDoesNotAffectMain`：建分支后向 main 再插入数据使两分支分叉，对分支做压缩，验证 main 快照与数据不变、分支快照更新且新快照的 parent 是原分支快照（保证快照 lineage 正确）。

## 总结

本提交为 Spark 的 `rewrite_data_files` 过程补齐了分支支持，从 core commit manager、Spark action 到过程层贯通传递目标分支，并默认回退 main 保持兼容。配合 4 个测试覆盖功能与隔离性，使用户可以直接对任意分支做数据文件压缩而不影响 main，完善了 Iceberg 分支特性在 Spark 数据维护操作上的可用性。
