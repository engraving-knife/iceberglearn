# 提交 1592：Spark: Fix flaky tests `withSnapshotIsolation` (#11974)

## 提交信息

- **序号**：1592
- **哈希**：b128bba57f613f23ed773f0fa6330c1d2bbf8a39
- **短哈希**：b128bba57
- **日期**：2025-01-17（Fri Jan 17 01:03:55 2025 +0800）
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Spark: Fix flaky tests `withSnapshotIsolation` (#11974)
- **PR/Issue**：#11974

## 总体目的

`withSnapshotIsolation` 是 Spark 扩展测试中针对 `DELETE` 和 `MERGE` 操作以 snapshot 隔离级别运行的一组用例。这类用例需要构造一种“并发冲突”场景：在执行行级操作（DELETE/MERGE）的同时向表中插入新数据，迫使行级操作的 commit 与插入操作的 commit 在同一快照隔离级别下产生冲突（`CommitFailedException`），从而验证 Iceberg 在 snapshot 隔离级别下的重试行为。由于并发提交的时序具有不确定性，这类用例天然容易抖动（flaky）——在某些 CI 环境中冲突未发生或重试次数不足，导致断言失败。

本提交的目的，是通过增加 commit 重试次数上限来降低这两类用例的抖动率。具体做法是给表加上 `COMMIT_NUM_RETRIES`（提交重试次数）属性，将其从默认值（4）提高到 7，给重试机制更多机会成功完成提交，避免在 CI 环境因偶发性重试不足导致的失败。这是一次针对测试稳定性（test reliability）的修复，并非修改产品代码逻辑，对运行时行为无影响。

## 如何达成设计目的

修改思路是：在 `withSnapshotIsolation` 相关用例给表设置的 TBLPROPERTIES 中，除了原有的 `DELETE_ISOLATION_LEVEL`/`MERGE_ISOLATION_LEVEL`、`COMMIT_MIN_RETRY_WAIT_MS`、`COMMIT_MAX_RETRY_WAIT_MS` 之外，再追加 `COMMIT_NUM_RETRIES='7'`。这样在发生快照隔离冲突时，commit 重试次数上限被提升，更不容易因为重试耗尽而抛出 `CommitFailedException` 导致测试失败。

为了在代码中引用这一属性常量，还在两个测试文件中新增了对 `TableProperties.COMMIT_NUM_RETRIES` 的 `import static`。

### 修改详情

#### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java`

**修改目的**：在 `TestDelete` 中以 snapshot 隔离级别运行 DELETE 的测试里，提高 commit 重试上限以减少 flaky。

**工作逻辑**：
1. 在 import 区新增 `import static org.apache.iceberg.TableProperties.COMMIT_NUM_RETRIES;`，以便在 SQL 语句中引用该常量名。
2. 将原 `ALTER TABLE %s SET TBLPROPERTIES('%s'='%s', '%s'='%s', '%s'='%s')` 的三组键值对扩展为四组，新增 `COMMIT_NUM_RETRIES='7'`。原有的 `DELETE_ISOLATION_LEVEL='snapshot'`、`COMMIT_MIN_RETRY_WAIT_MS='10'`、`COMMIT_MAX_RETRY_WAIT_MS='1000'` 保持不变。

通过把重试次数提高到 7，测试在并发插入触发的 commit 冲突场景下有更多机会重试成功，从而降低测试的不稳定性。

#### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java`

**修改目的**：与 `TestDelete` 对称，在 `TestMerge` 中以 snapshot 隔离级别运行 MERGE 的测试里，提高 commit 重试上限以减少 flaky。

**工作逻辑**：
1. 在 import 区新增 `import static org.apache.iceberg.TableProperties.COMMIT_NUM_RETRIES;`。
2. 将 `ALTER TABLE %s SET TBLPROPERTIES(...)` 由三组键值对扩展为四组，新增 `COMMIT_NUM_RETRIES='7'`。原有的 `MERGE_ISOLATION_LEVEL='snapshot'`、`COMMIT_MIN_RETRY_WAIT_MS='10'`、`COMMIT_MAX_RETRY_WAIT_MS='1000'` 保持不变。

修改模式与 `TestDelete` 完全一致，确保 DELETE 与 MERGE 两条路径在 snapshot 隔离级别下的重试行为一致。

## 小结

- **成效**：通过显式设置 `COMMIT_NUM_RETRIES=7`（高于默认的 4），给 snapshot 隔离级别下的并发冲突重试留出更多余量，降低 `withSnapshotIsolation` 系列 DELETE/MERGE 测试在 CI 中的抖动率。
- **影响范围**：仅修改 Spark v3.5 扩展模块的两个测试文件（`TestDelete.java`、`TestMerge.java`），共 10 行新增 / 4 行修改，无产品代码或文档变更，对运行时行为零影响。
- **回迁到 1.4.x 的注意事项**：这是测试稳定性修复，1.4.x 若包含对应的 `withSnapshotIsolation` 测试且也存在 flaky 问题，则可考虑回迁以改善 CI；否则仅 main 分支测试需要。本质上不影响发布产物。
