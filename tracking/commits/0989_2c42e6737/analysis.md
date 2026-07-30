# 提交 0989：Flink: Disabling flaky test TestIcebergSourceFailover.testBoundedWithSavepoint (#10802)

## 提交信息

- **序号**：0989 / 4088
- **哈希**：2c42e673757aeeede3a4d537eeb199254496e1c5
- **短哈希**：2c42e6737
- **日期**：2024-07-29 10:06:22 +0200
- **作者**：pvary
- **提交说明**：Flink: Disabling flaky test TestIcebergSourceFailover.testBoundedWithSavepoint (#10802)
- **PR/Issue**：#10802

## 总体目的

Iceberg Flink 集成在 `TestIcebergSourceFailover` 中有一组 failover 测试，覆盖 Flink IcebergSource 在 bounded（有界）流场景下从 checkpoint/savepoint 恢复的行为。其中 `testBoundedWithSavepoint` 在 CI 环境上反复出现不稳定（flaky）现象——同一份代码有时通过、有时失败，导致 CI 误报、维护成本高、阻塞其他 PR 合入。

flaky 测试在持续集成中危害较大：失败的测试本身可能是真问题，也可能只是测试时序/资源竞争导致；当反复出现"假阳性"失败后，开发者会形成"忽略红灯"的习惯，从而漏掉真实回归。本提交通过暂时禁用该测试来止血，避免阻塞 CI 与 PR 流程，留出时间后续单独调查根因并修复，再重新启用。

## 如何达成设计目的

通过 JUnit 5 的 `@Disabled` 注解把目标测试方法标记为禁用，并附带原因说明 "Disabled for now as it is flaky on CI"。`@Disabled` 是 JUnit 5 的标准注解，被标记的 `@Test` 方法在执行时会被 JUnit 平台跳过，并在测试报告中显示为 skipped。

由于 Iceberg 同时维护 Flink v1.17、v1.18、v1.19 三个版本的镜像代码，三份 `TestIcebergSourceFailover.java` 完全相同，因此该禁用同时应用到三份文件，保证三个版本的 CI 行为一致。

## 修改详情

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`

**修改目的**：禁用 v1.17 模块下 flaky 的 `testBoundedWithSavepoint` 测试。

**工作逻辑**：
- 新增 import `org.junit.jupiter.api.Disabled`；
- 在 `testBoundedWithSavepoint` 方法上加 `@Disabled("Disabled for now as it is flaky on CI")` 注解。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`

**修改目的**：对 v1.18 模块做相同禁用。

**工作逻辑**：与 v1.17 完全一致的两处改动。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`

**修改目的**：对 v1.19 模块做相同禁用。

**工作逻辑**：与 v1.17 完全一致的两处改动。

## 小结

- **成效**：通过 `@Disabled` 暂时禁用 `testBoundedWithSavepoint` 测试，消除 CI 上的 flaky 失败，恢复 PR 与 CI 流程的稳定性。这是临时止血措施，后续仍需单独调查该测试 flaky 的根因（可能与 Flink checkpoint/savepoint 时序、MiniCluster 资源竞争、IcebergSource split 分配有关）并修复后重新启用。
- **影响范围**：仅修改三个 Flink 版本（v1.17、v1.18、v1.19）下的 `TestIcebergSourceFailover.java` 测试类，每处 2 行改动；不影响任何生产代码或运行时行为。
- **回迁到 1.4.x 的注意事项**：如果 1.4.x 维护分支的 CI 上同样存在该 flaky 测试，建议回迁以避免误报阻塞 1.4.x 的常规维护工作。回迁时确认 1.4.x 上 Flink 模块版本范围（v1.17/v1.18 等），对相应文件施加同样的 `@Disabled`。同时应在维护文档或 issue 中跟踪"重新启用该测试"这一后续任务，避免永久禁用。
