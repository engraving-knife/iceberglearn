# 提交 2898：Build: Allow overriding the default test parallelism of 1 and update parallelism for Flink test (#13675)

## 提交信息

- **序号**：2898 / 4088
- **哈希**：51d5b0963b8aeb86794bad35e8a882cf33655df4
- **短哈希**：51d5b0963
- **日期**：2025-11-20 10:02:39 +0100
- **作者**：Maximilian Michels
- **提交说明**：Build: Allow overriding the default test parallelism of 1 and update parallelism for Flink test (#13675)
- **PR/Issue**：#13675

## 总体目的

本提交为 Iceberg 的 Gradle 构建引入可覆盖的测试并行度配置，并将 Flink CI 流水线的测试并行度从默认的 1（串行）提升为 `auto`（自动按 CPU 核数计算），以加速 Flink 测试套件的执行。

Iceberg 项目默认将测试并行度设为 1，即测试任务串行执行。这种保守默认有助于避免测试间的资源竞争、端口冲突以及因并发引发的 flaky 测试，保证 CI 结果的稳定性。但 Flink 集成测试套件规模较大、执行耗时较长，在 CI 上串行运行会拉长反馈周期、消耗较多 CI 资源。本提交的动机是：在不改变项目默认串行行为的前提下，提供一种通过系统属性 `-DtestParallelism` 按需开启并行执行的能力，并率先在 Flink CI 中启用自动并行度，从而缩短 Flink 测试的 CI 运行时间，同时为其他模块未来按需启用并行测试预留了通用的配置入口。

## 如何达成设计目的

在根 `build.gradle` 的 `subprojects` 块中新增一段逻辑：读取 Java 系统属性 `testParallelism`，若为 `auto` 则取运行时可用处理器数的一半向上取整作为并行 worker 数，否则将属性值解析为整数；随后对所有 `Test` 类型任务设置 `maxParallelForks` 为该值。当未设置该系统属性时（`null`），不做任何改动，沿用各任务默认行为（即串行）。同时在 Flink CI 工作流的 Gradle 命令中追加 `-DtestParallelism=auto` 以启用该机制。

## 修改详情

### `build.gradle` (+13/-0 lines)

**修改目的**：新增基于系统属性 `testParallelism` 的测试并行度覆盖机制。

**工作逻辑**：在 `subprojects` 块内（紧接已有逻辑之后）新增代码：先通过 `System.getProperty('testParallelism')` 读取系统属性；若不为 `null`，则计算 `numTestWorkers`——当值（忽略大小写）为 `"auto"` 时，取 `Math.ceil(Runtime.getRuntime().availableProcessors() / 2.0) as int`，即 CPU 核数的一半向上取整（例如 4 核取 2，避免测试占用全部 CPU 影响运行器其他进程）；否则将字符串转为整数（`testParallelism as int`，允许用户指定确切并行度）。最后通过 `tasks.withType(Test).configureEach { maxParallelForks = numTestWorkers }` 对所有测试任务统一设置最大并行 fork 数。`maxParallelForks` 控制 Gradle 在单个测试任务内并行启动多少个测试进程。当 `testParallelism` 未设置时整段逻辑被跳过，保持原有默认串行行为不变，确保向后兼容。

### `.github/workflows/flink-ci.yml` (+1/-1 lines)

**修改目的**：在 Flink CI 的 Gradle 测试命令中启用自动测试并行度。

**工作逻辑**：将 Flink CI job 中执行 `:iceberg-flink:iceberg-flink-${{ matrix.flink }}:check` 等任务的 `./gradlew` 命令末尾追加 `-DtestParallelism=auto`，使构建脚本读取到该系统属性后按 CPU 核数的一半设置 `maxParallelForks`，从而让 Flink 测试在 CI 上并行执行。其余参数（`-DsparkVersions=`、`-DkafkaVersions=`、`-DflinkVersions=`、`-Pquick=true`、`-x javadoc`）保持不变。

## 总结

本提交在不改变项目默认串行测试行为的前提下，为 Gradle 构建增加了通过 `-DtestParallelism` 系统属性覆盖测试并行度的通用能力（支持 `auto` 自动计算或指定整数），并率先在 Flink CI 中启用 `auto` 以加速 Flink 测试套件执行。改动小而通用，既缩短了 Flink CI 反馈周期，又为其他模块按需开启并行测试提供了统一入口，同时保持了对默认行为的完全兼容。
