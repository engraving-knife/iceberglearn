# 提交 2050：Core: Increase wait time of flaky test (#12714)

## 提交信息

- **序号**：2050 / 4088
- **哈希**：68b016cca6ba02781d3e42f43b6bceb414580816
- **短哈希**：68b016cca
- **日期**：2025-04-28 16:39:52 +0200
- **作者**：Manu Zhang
- **提交说明**：Core: Increase wait time of flaky test (#12714)
- **PR/Issue**：#12714

## 总体目的

`TestHadoopCommits` 中存在一个不稳定的（flaky）测试用例，该测试使用 `Awaitility` 等待多个线程在并发提交场景下达到某个屏障条件（barrier）。原先的最大等待时间为 10 秒，在某些 CI 环境、负载较高或调度延迟较大时该等待时间不足以让所有线程完成准备工作，导致测试偶发性失败。

本次提交将该等待时间从 10 秒增加到 60 秒，给并发线程足够的裕量完成屏障同步，从而消除测试的 flakiness，提升 CI 的稳定性与可重复性。

## 如何达成设计目的

修改非常聚焦：仅调整 `Awaitility.await()` 链式调用中 `.atMost(Duration.ofSeconds(...))` 的超时参数。保留原 10 毫秒的 poll 间隔不变，将最大总等待时间从 10 秒扩大到 60 秒。这样在大多数正常情况下不会影响测试耗时（因为 `Awaitility` 会在条件满足时立即返回），但为异常或高负载场景留出更宽裕的超时上限。

## 修改详情

### `core/src/test/java/org/apache/iceberg/hadoop/TestHadoopCommits.java` (修改, +1/-1 lines)

**修改目的**：将一个并发提交测试中等待屏障同步的超时时间从 10 秒提升到 60 秒，修复 flaky 测试。

**工作逻辑**：
该测试位于 `TestHadoopCommits` 类中，模拟多线程并发对 Iceberg 表进行 commit 的场景。每个线程在执行 `newFastAppend().appendFile(file).commit()` 之前，会先通过 `Awaitility.await()` 等待所有线程就绪，屏障 `barrier` 达到 `currentFilesCount * threadsCount` 时才继续。本次修改将该 `await` 的 `atMost` 从 `Duration.ofSeconds(10)` 改为 `Duration.ofSeconds(60)`，给线程调度与文件计数同步留出更多时间。

## 总结

通过延长并发测试中 `Awaitility` 的最大等待时间（10s → 60s），修复 `TestHadoopCommits` 中偶发失败的 flaky 测试。改动单一、风险低，不影响生产代码逻辑。
