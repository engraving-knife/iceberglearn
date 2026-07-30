# 提交 0983：mr：Fix ugi not correct in WORKER_POOL (#10661)

## 提交信息

- **序号**：0983 / 4088
- **哈希**：ec2c2e978e40a4bf7bf6a3a0bccbe7e3a68a0cc1
- **短哈希**：ec2c2e978
- **日期**：2024-07-26 15:30:00 +0200
- **作者**：liu yang
- **提交说明**：mr：Fix ugi not correct in WORKER_POOL (#10661)
- **PR/Issue**：#10661

## 总体目的

在 Hadoop MapReduce 集成（`mr` 模块）中，`IcebergInputFormat.getSplits()` 用于规划输入分片（InputSplit），期间会调用 `TableScan` 进行任务规划（plan）。规划过程中可能需要并发执行，因此原实现直接使用了 Iceberg 的全局共享静态线程池 `ThreadPools.WORKER_POOL`。

问题在于：Hadoop 通过 `UserGroupInformation`（UGI）维护当前用户的身份与权限上下文。在 Hive、MapReduce 等多用户场景中，作业可能在某个用户的 `doAs` 上下文中运行，但 `ThreadPools.WORKER_POOL` 是一个被所有调用方共享的静态线程池，其线程在首次创建时绑定了某个用户的 UGI。当其他用户的请求也使用同一个线程池时，工作线程中 `UserGroupInformation.getCurrentUser()` 会返回错误用户（即线程创建时的用户），而不是真正发起作业的用户。

这种 UGI 错误会导致权限校验、Kerberos 认证、文件系统访问等环节以错误身份执行，引发鉴权失败或越权问题。本提交的目的就是修复该问题，确保每次 `getSplits` 使用的线程池都绑定正确的用户身份。

## 如何达成设计目的

设计思路是放弃使用全局共享的 `ThreadPools.WORKER_POOL`，改为在每次 `getSplits` 调用时创建一个独立的工作线程池 `iceberg-plan-worker-pool`，并在使用完毕后关闭它。由于该线程池是在调用方所在的 UGI 上下文中创建的，其工作线程能够继承正确的用户身份，从而保证后续在 `TableScan` 中并发执行的所有任务都以正确用户身份运行。

具体实现：

1. 在 `getSplits(Table, Configuration)` 方法顶部，根据 `SystemConfigs.WORKER_THREAD_POOL_SIZE` 配置（可由 `Configuration` 覆盖，回退到默认 `ThreadPools.WORKER_THREAD_POOL_SIZE`）创建一个新的 `ExecutorService`。
2. 把原 `getSplits` 的核心逻辑抽取到一个新的私有方法 `planInputSplits(Table, Configuration, ExecutorService)` 中，由它接收工作线程池并使用。
3. 在 `getSplits` 中使用 `try/finally` 包裹对 `planInputSplits` 的调用，确保线程池在结束时一定被 `shutdown()`，避免线程泄漏。
4. 在 `TableScan` 构建链上调用 `scan.planWith(workerPool)`，把规划任务交给该线程池执行。

测试侧新增 `testWorkerPool` 测试，通过反射调用私有方法 `planInputSplits`，模拟两个不同 UGI（user1、user2）下创建工作线程池，并验证线程池中执行任务时返回的当前用户名是否符合预期：
- 在 user1 上下文中创建的线程池，即使后续被 user2 调用，仍返回 "user1"（线程池创建时已绑定）；
- 在 user2 上下文中创建的线程池返回 "user2"。

这验证了修复后线程池的 UGI 行为符合预期。

## 修改详情

### `mr/src/main/java/org/apache/iceberg/mr/mapreduce/IcebergInputFormat.java`

**修改目的**：避免使用全局共享的 `WORKER_POOL` 导致 UGI 错误，改为每次规划分片时新建独立工作线程池，确保线程池绑定正确的用户上下文。

**工作逻辑**：

1. **新增 import**：引入 `java.util.concurrent.ExecutorService`、`org.apache.iceberg.SystemConfigs`、`org.apache.iceberg.util.ThreadPools`。
2. **`getSplits` 方法重构**：
   - 加载 table 之后，调用 `ThreadPools.newWorkerPool("iceberg-plan-worker-pool", size)` 创建一个新线程池，线程数从 `Configuration` 中读取 `SystemConfigs.WORKER_THREAD_POOL_SIZE.propertyKey()`，缺省回退到 `ThreadPools.WORKER_THREAD_POOL_SIZE`。
   - 在 `try` 块中调用新私有方法 `planInputSplits(table, conf, workerPool)` 完成实际规划工作。
   - 在 `finally` 块中调用 `workerPool.shutdown()` 关闭线程池，确保不泄漏线程。
3. **抽取 `planInputSplits` 方法**：将原 `getSplits` 主体（构建 `TableScan`、配置扫描、构造 CombinedScanTask 等）移入新方法 `planInputSplits(Table table, Configuration conf, ExecutorService workerPool)`。
4. **使用 workerPool 进行规划**：在 `TableScan` 链上增加 `.planWith(workerPool)` 调用，让 Iceberg 在 planTasks 期间使用本线程池，从而保证规划阶段的所有并发任务都使用当前调用者的 UGI。

### `mr/src/test/java/org/apache/iceberg/mr/TestIcebergInputFormats.java`

**修改目的**：为该修复新增测试，验证每次创建的工作线程池都绑定正确的 UGI。

**工作逻辑**：

1. **新增 import**：引入 `java.lang.reflect.Method`、`java.security.PrivilegedAction`、`java.util.concurrent.ExecutorService`、`org.apache.hadoop.security.UserGroupInformation`、`org.apache.iceberg.util.ThreadPools`。
2. **新增 `testWorkerPool` 测试方法**：
   - 创建一张无分区表 `helper.createUnpartitionedTable()`；
   - 通过 `UserGroupInformation.createUserForTesting` 构造 user1 和 user2 两个测试用户；
   - 创建两个独立的 workerPool（各 1 线程），分别命名为 `iceberg-plan-worker-pool`；
   - 断言：在 user1 上下文中创建的 workerPool1 在被 user1 调用时返回 "user1"；
   - 断言：在 user1 上下文中创建的 workerPool1 即使用 user2 调用，仍然返回 "user1"（线程池创建时绑定）；
   - 断言：在 user2 上下文中创建的 workerPool2 在被 user2 调用时返回 "user2"；
   - 在 `finally` 中关闭两个线程池。
3. **新增 `getUserFromWorkerPool` 辅助方法**：
   - 通过反射获取 `IcebergInputFormat` 的私有方法 `planInputSplits(Table, Configuration, ExecutorService)` 并设置可访问；
   - 在 `user.doAs((PrivilegedAction<String>) () -> { ... })` 上下文中调用该方法；
   - 调用完成后立即向 workerPool 提交一个新任务 `UserGroupInformation.getCurrentUser().getUserName()`，并通过 `.get()` 同步获取返回值；
   - 这个返回值反映了 workerPool 中线程当前绑定的 UGI 用户名，从而验证线程池的 UGI 是否正确。

## 小结

- **成效**：修复了 MapReduce 集成中工作线程池 UGI 错误的问题，每次规划分片时新建独立线程池，使所有并发任务都使用正确用户身份执行，避免了 Kerberos 鉴权与权限校验失败等问题。
- **影响范围**：`mr` 模块中 `IcebergInputFormat` 的输入分片规划逻辑，以及对应测试。
- **回迁到 1.4.x 的注意事项**：建议回迁。该问题是 UGI 安全相关的正确性 bug，对 Hive/MapReduce 多用户部署影响较大；改动局限在 `mr` 模块内部，逻辑清晰，回迁风险较低。回迁时注意确认 `ThreadPools.newWorkerPool` 与 `SystemConfigs.WORKER_THREAD_POOL_SIZE.propertyKey()` 在 1.4.x 分支上的签名与 API 是否一致，避免出现方法签名差异。
