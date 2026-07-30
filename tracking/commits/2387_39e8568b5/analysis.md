# 提交 2387：Build, Flink: Add timeout and print more logs (#13627)

## 提交信息

- **序号**：2387 / 4088
- **哈希**：39e8568b511601a32671fb3cd3174054c664448d
- **短哈希**：39e8568b5
- **日期**：2025-07-23 12:06:02 +0200
- **作者**：pvary
- **提交说明**：Build, Flink: Add timeout and print more logs (#13627)
- **PR/Issue**：#13627

## 总体目的

本提交针对 Flink 测试中的不稳定问题（flaky tests）进行改进，主要做了两件事：为 Flink Upsert 测试添加超时限制，以及在 CI 环境中启用更详细的测试日志输出。

`TestFlinkUpsert` 测试类可能存在偶尔卡住或运行时间过长的问题，导致 CI 构建挂起。通过添加 `@Timeout(60)` 注解，确保测试在 60 秒内必须完成，否则自动失败。这有助于快速发现卡住的测试，避免 CI 资源被长时间占用。

同时，在 CI 环境中启用更详细的测试日志（包括 started、passed、skipped、failed 事件），有助于在 CI 上诊断测试失败原因。在本地开发环境中保持仅输出失败事件的最小日志，避免干扰。

## 如何达成设计目的

设计思路是通过 JUnit 5 的 `@Timeout` 注解为测试添加超时限制，通过 Gradle 的 `testLogging` 配置根据环境差异化设置日志详细程度。关键设计点如下：

1. **测试超时**：在 `TestFlinkUpsert` 类上添加 `@Timeout(60)` 注解，使该类的所有参数化测试用例都在 60 秒超时限制下运行。
2. **环境感知日志**：在 `build.gradle` 的 `testLogging` 配置中，通过检查 `System.getenv('CI')` 环境变量判断是否在 CI 环境中运行。CI 环境下输出所有事件（started、passed、skipped、failed），非 CI 环境仅输出失败事件。

## 修改详情

### `build.gradle` (+8/-1 lines)

**修改目的**：在 CI 环境中启用更详细的测试日志。

**工作逻辑**：修改 `subprojects` 块中的 `testLogging` 配置，将原来固定的 `events "failed"` 改为条件判断：
- 如果 `System.getenv('CI') != null`（CI 环境），输出 `"started", "passed", "skipped", "failed"` 四种事件。
- 否则（本地开发环境），仅输出 `"failed"` 事件。
`exceptionFormat "full"` 保持不变。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/TestFlinkUpsert.java` (+2/-0 lines)

**修改目的**：为 Flink 1.19 版本的 Upsert 测试添加 60 秒超时。

**工作逻辑**：新增 `import org.junit.jupiter.api.Timeout` 导入，在类上添加 `@Timeout(60)` 注解。该注解使类中所有测试方法（包括 `@TestTemplate` 参数化测试）在 60 秒后自动失败。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/TestFlinkUpsert.java` (+2/-0 lines)

**修改目的**：为 Flink 1.20 版本的相同测试添加超时，修改内容与 1.19 版本相同。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/TestFlinkUpsert.java` (+2/-0 lines)

**修改目的**：为 Flink 2.0 版本的相同测试添加超时，修改内容与 1.19 版本相同。

## 总结

本提交是测试基础设施改进，通过两个方面的修改提升 CI 流程的稳定性和可诊断性：为 Flink 三个版本（1.19、1.20、2.0）的 `TestFlinkUpsert` 测试添加 60 秒超时限制，避免卡住的测试导致 CI 挂起；在 CI 环境中启用更详细的测试日志输出（started/passed/skipped/failed），便于远程诊断测试失败。在本地开发环境中保持最小日志输出，不影响开发体验。修改涉及 4 个文件，13 行新增和 1 行删除。
