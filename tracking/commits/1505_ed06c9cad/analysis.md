# 提交序号 1505 短哈希 ed06c9cad 分析

## 提交信息
- 哈希：ed06c9cad8ecfd6cc1c0b9e11e11d509428ba0db
- 日期：2024-12-18
- 作者：Manu Zhang <OwenZhang1990@gmail.com>
- 消息：Core, Spark 3.5: Fix test failures due to timeout (#11654)

## 总体目的

本提交用于修复由于超时导致的测试失败。这些测试在并发提交（concurrent commit）场景下会触发 Iceberg 的提交重试机制，默认的重试退避等待时间（最小等待 `commit.retry.min-num-wait-ms`、最大等待 `commit.retry.max-num-wait-ms`）数值较大，加之锁获取超时（`lock-acquire-timeout-ms`）的存在，使得在测试环境下整个重试链路耗时过长，容易超过测试框架的超时阈值而失败。

问题的本质是：测试本身关注的是"并发提交时能否正确处理冲突与重试"这一逻辑正确性，而非"重试等待时长"这一时间维度。默认的重试等待参数是为生产环境设计的（避免对底层存储造成过大压力），但在 CI 测试环境中，过长的等待只会拖慢测试并触发超时失败，并不能带来额外的测试价值。因此本提交通过在这些并发测试中显式调小重试等待参数、并禁用不必要的锁获取额外重试，使测试既保留对并发冲突处理逻辑的覆盖，又能在合理时间内完成。

这种做法在测试工程中是常见的：将关注点聚焦在被测逻辑上，把与被测逻辑无关但会影响测试时长的参数调到对测试友好的取值。

## 如何达成设计目的

本提交通过在三个测试类中显式设置更激进的重试参数来达成目的。具体做法是在建表属性或 `ALTER TABLE` 语句中加入 `commit.retry.min-num-wait-ms=10`、`commit.retry.max-num-wait-ms=1000`，并在需要时设置 `lock-acquire-timeout-ms=0` 来禁用锁获取失败后的额外重试。这样重试之间只等待 10ms 起、最多 1000ms，远小于默认值，使得并发冲突场景下的重试能够快速推进。

### 修改详情

#### core/src/test/java/org/apache/iceberg/hadoop/TestHadoopCommits.java

该测试类验证 Hadoop 表的提交行为，其中包含并发提交场景。本次修改：

1. 新增 import：`LOCK_ACQUIRE_TIMEOUT_MS`、`COMMIT_MAX_RETRY_WAIT_MS`、`COMMIT_MIN_RETRY_WAIT_MS`。

2. 在创建并发测试用的表时，原先只设置 `COMMIT_NUM_RETRIES` 为线程数，现在额外设置：
   - `COMMIT_MIN_RETRY_WAIT_MS = "10"`：最小重试等待 10ms。
   - `COMMIT_MAX_RETRY_WAIT_MS = "1000"`：最大重试等待 1000ms。
   - `LOCK_ACQUIRE_TIMEOUT_MS = "0"`：禁用锁获取失败后的额外重试，注释说明"Disable extra retry on lock acquire failure since commit will fail anyway"（既然提交最终会失败，就没必要在锁获取上再做额外重试）。

这样并发提交测试中失败的一方会快速重试或快速失败，不会因为长等待而触发测试超时。

#### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestDelete.java

该测试类验证 Spark 3.5 的 DELETE 操作扩展。本次修改针对一个使用 `snapshot` 隔离级别的并发删除测试：

1. 新增 import：`COMMIT_MAX_RETRY_WAIT_MS`、`COMMIT_MIN_RETRY_WAIT_MS`。

2. 原先的 `ALTER TABLE ... SET TBLPROPERTIES('DELETE_ISOLATION_LEVEL' 'snapshot')` 语句存在一个语法瑕疵（键值之间缺少等号），本次一并修正为标准写法 `'%s'='%s'`，并在同一语句中追加设置 `COMMIT_MIN_RETRY_WAIT_MS=10` 和 `COMMIT_MAX_RETRY_WAIT_MS=1000`。这样在该并发删除测试中，提交冲突后的重试等待被压缩到 10ms~1000ms，避免超时。

#### spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestMerge.java

该测试类验证 Spark 3.5 的 MERGE 操作扩展，修改模式与 `TestDelete` 完全对应：

1. 新增 import：`COMMIT_MAX_RETRY_WAIT_MS`、`COMMIT_MIN_RETRY_WAIT_MS`。

2. 将原先的 `ALTER TABLE ... SET TBLPROPERTIES('MERGE_ISOLATION_LEVEL' 'snapshot')` 修正为 `'%s'='%s'` 写法，并追加 `COMMIT_MIN_RETRY_WAIT_MS=10`、`COMMIT_MAX_RETRY_WAIT_MS=1000`，使并发 MERGE 测试的重试等待同样被压缩。

## 小结

本提交通过在三个并发提交测试中显式调小重试退避参数、禁用锁获取额外重试，修复了因默认等待时间过长而导致的测试超时失败。其意义在于：在保留对并发冲突处理逻辑测试覆盖的前提下，让测试运行更快、更稳定，减少 CI 中的偶发超时失败。此外，顺带修正了 `TestDelete` 和 `TestMerge` 中 `ALTER TABLE TBLPROPERTIES` 语句键值缺少等号的语法瑕疵，使 SQL 更规范。整体上是一个测试稳定性与正确性的双重改进，不影响任何产品代码行为。
