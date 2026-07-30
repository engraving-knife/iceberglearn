# 提交 0540：Flink 1.16, 1.17: Fix continuous enumerator lost enumeration history state when restore from checkpoint

## 提交信息

- **序号**：0540 / 4088
- **哈希**：487ff98cefa3ae49196b9559309c3b78448eb1fc
- **短哈希**：487ff98ce
- **日期**：2024-02-26 20:58:15 +0800
- **作者**：Reo
- **提交说明**：Flink 1.16, 1.17: Fix continuous enumerator lost enumeration history state when restore from checkpoint (#9812)
- **PR/Issue**：#9812（对应 main 分支 PR #9762，即 Flink 1.18 上的修复提交 eab958b620a0d760c2834788f3780ddb94fcd7c2，本提交将其回port 到 Flink 1.16/1.17）

## 总体目的

本提交修复 Iceberg Flink 流式源（continuous source）的 `ContinuousIcebergEnumerator` 在从 checkpoint 恢复时丢失"枚举历史状态"（enumeration history state）的 bug。

`ContinuousIcebergEnumerator` 是 Iceberg Flink 流式读取的协调器（enumerator），负责周期性地发现新增的 Iceberg snapshot 并将其拆分为 split 分发给 reader。为防止 assigner 中堆积过多未消费 split 导致内存膨胀和 checkpoint 状态过大，enumerator 内部维护了一个 `EnumerationHistory`——一个固定大小（默认 3）的环形缓冲区，记录最近若干次枚举发现的 split 数量，并据此在 `shouldPauseSplitDiscovery()` 中做限流决策：当 assigner 的 pending split 数已达到最近若干轮发现总量之和时，暂停本轮 split 发现。

`snapshotState()` 方法在 checkpoint 时正确地把 `enumerationHistory.snapshot()` 保存进了 `IcebergEnumeratorState`。但 enumerator 构造函数在从 `IcebergEnumeratorState` 恢复时，只恢复了 `enumeratorPosition`（最近枚举的 snapshot 位置），却遗漏了对 `enumerationHistory` 的恢复——`enumerationHistory` 始终被新建为空。这导致每次从 checkpoint 恢复后，限流历史清零，`shouldPauseSplitDiscovery()` 在 `count < history.length`（即不足 3 条记录）时直接返回 `false`，限流机制在恢复后的前几轮枚举中完全失效。在上游写入突发或 assigner 积压严重的场景下，恢复后可能立即发起多轮不受限的 split 发现，加剧内存压力和 checkpoint 膨胀，与限流设计初衷相悖。

本提交补上这一行遗漏的恢复调用，使限流历史能跨越 checkpoint 正确传递。

## 如何达成设计目的

修复方式极简：在构造函数的 `if (enumState != null)` 分支中，紧跟在 `this.enumeratorPosition.set(enumState.lastEnumeratedPosition());` 之后，补一行 `this.enumerationHistory.restore(enumState.enumerationSplitCountHistory());`。

之所以能这么直接，是因为：
1. **保存侧已就绪**：`snapshotState()` 早已通过 `new IcebergEnumeratorState(enumeratorPosition.get(), assigner.state(), enumerationHistory.snapshot())` 把历史写入状态对象，`IcebergEnumeratorState` 也早已持有 `enumerationSplitCountHistory` 字段和 `enumerationSplitCountHistory()` 访问器。即 checkpoint 里本来就存了这份历史，只是恢复侧没用。
2. **恢复方法已就绪**：`EnumerationHistory.restore(int[] restoredHistory)` 方法早已存在，能把外部数组拷贝进内部环形缓冲区，并处理了"恢复数组比当前容量大"的情况（只保留最新的 `history.length` 条）。
3. **线程安全已就绪**：`restore` 是 `synchronized` 方法，且构造函数执行时 enumerator 尚未 `start()`，不存在并发问题。

所以这是一个纯粹的"接线遗漏"修复——所有零件都在，只是恢复路径上漏接了一根线。

## 修改详情

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousIcebergEnumerator.java`

**修改目的**：从 checkpoint 状态恢复枚举历史，使限流机制在恢复后立即生效。

**工作逻辑**：在构造函数中新增一行：
```java
if (enumState != null) {
  this.enumeratorPosition.set(enumState.lastEnumeratedPosition());
  this.enumerationHistory.restore(enumState.enumerationSplitCountHistory());  // 新增
}
```

`enumState.enumerationSplitCountHistory()` 返回 `int[]`，是 checkpoint 时通过 `enumerationHistory.snapshot()` 导出的最近若干轮枚举的 split 计数。`EnumerationHistory.restore()` 将其拷入内部 `history` 数组并设置 `count`：
- 若恢复数组长度超过 `history.length`（当前为 3），只取最新的 `history.length` 条（通过 `startingOffset` 跳过旧的）；
- 否则全部拷入，`count` 设为实际拷入条数。

恢复后，`shouldPauseSplitDiscovery()` 在下一轮枚举时即可基于完整历史做限流判断（若恢复的历史已满 3 条），不再有恢复后的限流盲区。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousIcebergEnumerator.java`

**修改目的**：同上，Flink 1.17 模块的同一修复。

**工作逻辑**：与 v1.16 完全一致，同一行代码加在构造函数同一位置。两份文件改动前内容完全相同（`index b1dadfb9a..55451b105` 索引一致），改动后也完全相同。

## 小结

本提交修复了一个流式场景下的状态恢复遗漏：`ContinuousIcebergEnumerator` 从 checkpoint 恢复时未恢复 `EnumerationHistory`，导致限流机制在恢复后短期内失效，可能引发 split 过度发现、内存膨胀和 checkpoint 状态变大。修复仅一行代码，且无新增测试（原 main 分支修复 #9762 也未加测试），属于低风险高收益的补漏型修复。

**与 main 分支 #9762 的对比**：main 分支的修复提交 eab958b62（PR #9762）只改了 `flink/v1.18/` 一份文件，逻辑完全相同。本提交 #9812 是同一作者将同一修复同步到 `flink/v1.16/` 和 `flink/v1.17/` 两个模块。三个 Flink 版本的 `ContinuousIcebergEnumerator` 在该处逻辑一致，修复内容一字不差。

**回迁到 1.4.x 的注意事项**：1.4.x 若维护 Flink 1.16/1.17/1.18 模块，需确认各模块的 `ContinuousIcebergEnumerator` 构造函数是否都有此遗漏。若 1.4.x 基线已包含 `EnumerationHistory` 机制（即 `snapshotState` 已保存历史、`restore` 方法已存在），则可直接套用本一行修复。需特别注意：`EnumerationHistory` 类、`IcebergEnumeratorState.enumerationSplitCountHistory` 字段、`restore` 方法这三者必须同时存在才能生效；若 1.4.x 上 `IcebergEnumeratorState` 还没有 `enumerationSplitCountHistory` 字段（更早的版本），则不能直接套用，需先回迁整个 enumeration history 机制。由于本修复未附带测试，回迁后建议手动验证：启动一个流式作业，写入若干 snapshot 触发多轮枚举使 history 填满，手动触发 checkpoint 后从该 checkpoint 重启作业，确认恢复后首轮枚举的限流决策与重启前一致（即 history 已恢复）。
