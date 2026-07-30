# 提交 0104：AWS, Core: Use Awaitility instead of Thread.sleep()

## 提交信息

- **序号**：0104 / 4088
- **哈希**：7e9e02c0e3f1304be89d094b0eab238da46c34ab
- **短哈希**：7e9e02c0e
- **日期**：2023-10-30 12:33:11 +0100
- **作者**：Naveen Kumar
- **提交说明**：AWS, Core: Use Awaitility instead of Thread.sleep()
- **PR/Issue**：无

## 总体目的

本提交将 Iceberg 多个测试模块中用于"等待异步条件成立"的 `Thread.sleep()` 硬等待替换为 [Awaitility](https://github.com/awaitility/awaitility) 库提供的轮询断言机制，以提升测试的稳定性、速度与可读性。这是 Iceberg 测试基础设施现代化工作的一部分。

测试中使用 `Thread.sleep()` 是典型的反模式：等待时间要么被设得过长（拖慢测试套件、增加 CI 成本），要么过短（在环境抖动时导致 flaky 失败）。Awaitility 通过"轮询 + 条件断言 + 最大超时"的方式，让测试在条件满足时立即继续、仅在超时才失败，既缩短了平均测试时长，又显著降低了因固定睡眠时长不足而引起的偶发失败。本提交处理的等待场景分为两类：

1. **并发提交测试中的线程屏障（barrier）**：在 Hadoop/JDBC/Hive/Glue 的并发提交测试里，多个线程通过 `AtomicInteger barrier` 协调顺序提交，旧实现用 `while (barrier.get() < ...) { Thread.sleep(10); }` 自旋等待，本提交改为 `Awaitility.await().pollInterval(10ms).atMost(10s).until(() -> barrier.get() >= ...)`。
2. **AWS IAM / Lake Formation 一致性等待**：AWS 集成测试中创建 IAM 角色/策略或注册 Lake Formation 资源后需要等待权限/资源生效，旧实现用 `Thread.sleep(IAM_PROPAGATION_DELAY)`（通常 10 秒）固定等待，本提交改为通过 `Awaitility.await().untilAsserted(...)` 轮询调用 `iam.getRolePolicy(...)` 或 `lakeformation.describeResource(...)` 验证生效状态。

此外，本提交顺带将 Spark 多版本测试辅助类 `SparkSQLExecutionHelper` 中既有的 Awaitility 超时从 500ms 调整为 3 秒，以缓解 CI 上的偶发超时失败。整体改动覆盖 12 个文件、+108/-53 行。

对 Iceberg 演进的意义在于：系统性消除测试中的 `Thread.sleep()`，让并发测试与跨云集成测试更稳定、更快，从而改善开发者反馈循环和 CI 可靠性，并为后续移除内部 `AssertHelpers` 等历史工具（见提交 0105）铺路。

## 如何达成设计目的

设计思路分两步：首先在 `build.gradle` 中为 `iceberg-api`、`iceberg-aws`、`iceberg-hive-metastore` 三个模块的测试配置添加 `awaitility` 测试依赖（`iceberg-core` 与 `iceberg-hadoop` 等通过 `testArtifacts` 间接或自身已具备），然后对每个目标测试文件做"原地等价替换"：把 `Thread.sleep()` 形式的轮询/固定等待改写为 `Awaitility.await()` 链式调用，并在必要时新增 `java.time.Duration`、`org.awaitility.Awaitility`、`org.assertj.core.api.Assertions` 等 import，移除已废弃的 `throws Exception` 声明。

并发测试的改写保持原有屏障语义不变：旧代码等待 `barrier.get() >= numCommittedFiles * threadsCount`，新代码用 `final int currentFilesCount = numCommittedFiles;` 捕获循环变量后传入 lambda 做同样判断，确保多线程交替提交的顺序约束不被破坏。AWS 等待则把"睡眠 N 秒希望 IAM 已生效"升级为"调用 AWS API 主动验证"，更具语义化。

## 修改详情

### `build.gradle`

**修改目的**：为相关模块添加 Awaitility 测试依赖。

**工作逻辑**：在三个 `project(...)` 块的 `dependencies { testImplementation ... }` 中各新增一行 `testImplementation libs.awaitility`：
- `project(':iceberg-api')` 块（约第 301 行）
- `project(':iceberg-aws')` 块（约第 501 行）
- `project(':iceberg-hive-metastore')` 块（约第 722 行）

`libs.awaitility` 在 `libs.versions.toml` 中已声明（`awaitility = "4.2.0"`），此次仅把依赖引入到这三个模块的测试 classpath。

### `core/src/test/java/org/apache/iceberg/hadoop/TestHadoopCommits.java`

**修改目的**：将并发提交测试中的自旋睡眠改为 Awaitility 轮询。

**工作逻辑**：在多线程并发 `newFastAppend` 提交循环里，原先用 `while (barrier.get() < numCommittedFiles * threadsCount) { Thread.sleep(10); }` 等待其它线程完成上一轮提交。改写为先用 `final int currentFilesCount = numCommittedFiles;` 捕获循环变量（lambda 闭包要求 effectively final），再用 `Awaitility.await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(10)).until(() -> barrier.get() >= currentFilesCount * threadsCount);` 替换。轮询间隔与原先一致（10ms），新增最多 10 秒的兜底超时，避免死循环。新增 `java.time.Duration` 与 `org.awaitility.Awaitility` 的 import。

### `core/src/test/java/org/apache/iceberg/jdbc/TestJdbcTableConcurrency.java`

**修改目的**：将 JDBC Catalog 并发提交测试中的自旋睡眠改为 Awaitility 轮询。

**工作逻辑**：与 `TestHadoopCommits` 完全同构。原先 `Tasks.range(2)` 写法被改为 `int threadsCount = 2; Tasks.range(threadsCount)`，并把 `while + Thread.sleep(10)` 屏障替换为 `Awaitility.await().pollInterval(10ms).atMost(10s).until(() -> barrier.get() >= currentFilesCount * threadsCount)`。同样新增 `Duration` 与 `Awaitility` 的 import。

### `hive-metastore/src/test/java/org/apache/iceberg/hive/TestHiveTableConcurrency.java`

**修改目的**：将 Hive Metastore Catalog 并发提交测试中的自旋睡眠改为 Awaitility 轮询。

**工作逻辑**：与前两者同构。`Tasks.range(2)` 改为 `Tasks.range(threadsCount)`，屏障等待改为 `Awaitility.await().pollInterval(10ms).atMost(10s).until(...)`。新增 `Duration` 与 `Awaitility` 的 import。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogLock.java`

**修改目的**：将 Glue Catalog 锁的并发测试中的自旋睡眠改为 Awaitility 轮询。

**工作逻辑**：`Tasks.range(2)` 改为 `int threadsCount = 2; Tasks.range(threadsCount)`，原先 `while (barrier.get() < numCommittedFiles * 2) { Thread.sleep(10); }` 替换为 `Awaitility.await().pollInterval(Duration.ofMillis(10)).atMost(Duration.ofSeconds(10)).until(() -> barrier.get() >= currentFilesCount * threadsCount);`，并在循环变量捕获中使用 `final int currentFilesCount = numCommittedFiles;`。新增 `Duration` 与 `Awaitility` 的 import。

### `aws/src/integration/java/org/apache/iceberg/aws/TestAssumeRoleAwsClientFactory.java`

**修改目的**：将 AssumeRole 测试中等待 IAM 一致性的固定睡眠改为主动轮询验证。

**工作逻辑**：原 `private void waitForIamConsistency() throws Exception { Thread.sleep(10000); }` 改为无 `throws` 的版本，内部用 `Awaitility.await("wait for IAM role policy to update.").pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).ignoreExceptions().untilAsserted(() -> Assertions.assertThat(iam.getRolePolicy(GetRolePolicyRequest.builder()....build())).isNotNull())`。即先延迟 1 秒再开始轮询，最多等 10 秒，期间忽略偶发 AWS 异常，直到能成功取到角色策略。`testAssumeRoleGlueCatalog()` 方法签名相应去掉 `throws Exception`。新增 `Duration`、`Awaitility`、`GetRolePolicyRequest` 的 import。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/LakeFormationTestBase.java`

**修改目的**：将 Lake Formation 测试基类中等待 IAM 与资源注册生效的固定睡眠改为主动轮询验证。

**工作逻辑**：这是本提交中改动最大的文件（+39/-4 行左右）。原 `private static void waitForIamConsistency() throws Exception` 改为带参数的 `waitForIamConsistency(String roleName, String policyName)`，内部用 `Awaitility.await().pollDelay(1s).atMost(10s).untilAsserted(...)` 轮询 `iam.getRolePolicy(GetRolePolicyRequest.builder().roleName(roleName).policyName(policyName).build())`。两处调用点（`lfRegisterPathRole` 与 `lfPrivilegedRole` 创建后）随之传入角色名与策略名。此外，`registerResource(arn)` 方法末尾原先调用 `waitForIamConsistency()` 来等待 SLR 权限传播，改为内联 `Awaitility.await().pollDelay(1s).atMost(10s).ignoreExceptions().untilAsserted(...)` 轮询 `lakeformation.describeResource(DescribeResourceRequest)`，校验返回的 `resourceInfo().roleArn()` 与 `lfRegisterPathRoleArn` 一致——这比原先纯睡眠更能确认资源已真正注册生效。`startUp` 中注册 S3 测试桶路径后那次多余的 `waitForIamConsistency()` 被移除（该处并不涉及 IAM 角色）。新增 `Assertions`、`Awaitility`、`DescribeResourceRequest`、`GetRolePolicyRequest`、`Duration` 等 import。

### `aws/src/integration/java/org/apache/iceberg/aws/lakeformation/TestLakeFormationAwsClientFactory.java`

**修改目的**：移除该测试中私有 `waitForIamConsistency()` 方法，改用内联 Awaitility 等待 IAM 生效。

**工作逻辑**：删除原 `private void waitForIamConsistency() throws Exception { Thread.sleep(IAM_PROPAGATION_DELAY); }` 方法，在 `PutRolePolicy` 调用后内联 `Awaitility.await().pollDelay(1s).atMost(10s).untilAsserted(() -> Assertions.assertThat(iam.getRolePolicy(GetRolePolicyRequest.builder().roleName(roleName).policyName(policyName).build())).isNotNull())`。新增 `Duration`、`Assertions`、`Awaitility`、`GetRolePolicyRequest` 的 import。

### `spark/v3.{2,3,4,5}/spark/src/test/java/org/apache/iceberg/spark/source/SparkSQLExecutionHelper.java`（四个文件，改动相同）

**修改目的**：放宽既有 Awaitility 等待 Spark SQL 执行完成的超时上限，缓解 CI flaky。

**工作逻辑**：四个 Spark 版本（3.2/3.3/3.4/3.5）的同名辅助类中，原本已有 `Awaitility.await().atMost(Duration.ofMillis(500)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> assertThat(statusStore.execution(lastExecution.executionId()).get()).isNotNull());` 用于等待 Spark 执行状态可查询。本次将 `atMost` 从 500ms 提升到 3 秒（`Duration.ofSeconds(3)`），其余不变。该改动与"替换 Thread.sleep"主题相关但不完全一致——它修正的是既有 Awaitility 等待在繁忙 CI 上超时不足的问题。

## 小结

本提交系统性地用 Awaitility 轮询断言取代测试中的 `Thread.sleep()`，覆盖并发提交屏障与 AWS IAM/Lake Formation 一致性等待两类场景，并附带放宽 Spark 测试的 Awaitility 超时，显著提升了 Iceberg 测试套件的稳定性与执行效率。
