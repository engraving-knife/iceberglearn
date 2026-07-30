# 提交 0523：Flink 1.18: Fix continuous enumerator lost enumeration history state when restore from checkpoint

## 提交信息

- **序号**：0523 / 4088
- **哈希**：eab958b620a0d760c2834788f3780ddb94fcd7c2
- **短哈希**：eab958b62
- **日期**：2024-02-20 20:35:07 -0800
- **作者**：Reo <leinuowen@gmail.com>
- **提交说明**：Flink 1.18: Fix continuous enumerator lost enumeration history state when restore from checkpoint. (#9762)
- **PR/Issue**：#9762

## 总体目的

修复 Flink 1.18 Iceberg Source 的连续流式枚举器 `ContinuousIcebergEnumerator` 在从 checkpoint 恢复时丢失"枚举历史（enumeration history）"状态的 bug。`snapshotState`（写检查点时）会把 `enumeratorPosition` 与 `enumerationHistory.snapshot()` 一并持久化到 `IcebergEnumeratorState`；但构造器（从检查点恢复时）只读取并恢复了 `enumeratorPosition`，漏掉了 `enumerationHistory`。结果是作业 failover/重启后，枚举历史被清空，导致基于历史的反压/暂停分裂发现逻辑失效，可能引发过度枚举、重复分裂或下游反压失灵。

## 如何达成设计目的

修复方式极简：在构造器恢复分支中补一行 `this.enumerationHistory.restore(enumState.enumerationSplitCountHistory());`，与 `enumeratorPosition` 的恢复对称。设计上让"写检查点保存了什么，恢复时就还原什么"保持完整对称，避免状态半恢复。

要理解这一行的作用，需理清 `EnumerationHistory` 在连续枚举中的角色：

1. **`ContinuousIcebergEnumerator` 的工作模型**：`start()` 通过 `enumeratorContext.callAsync` 周期性调用 `discoverSplits()` 发现新分裂，再经 `processDiscoveredSplits` 分配给 reader。每次成功枚举一批分裂后，调用 `enumerationHistory.add(result.splits().size())` 记录本批分裂数，并把 `enumeratorPosition` 推进到 `result.toPosition()`。

2. **`EnumerationHistory` 的作用**：它维护一个固定大小的滑动窗口（`ENUMERATION_SPLIT_COUNT_HISTORY_SIZE`），记录最近若干次枚举产出的分裂数。`shouldPauseSplitDiscovery(pendingSplitCountFromAssigner)` 用这段历史判断是否应暂停分裂发现——当 assigner 中待消费分裂数过多（基于历史产出速率估算）时暂停，防止分裂堆积压垮下游。这是一种基于历史速率的反压机制。

3. **bug 后果**：恢复后 `enumerationHistory` 是空的（构造器新建了 `new EnumerationHistory(SIZE)` 但未填充历史），`shouldPauseSplitDiscovery` 在历史为空时无法做出有效判断，可能误判为"可以继续枚举"，导致恢复后短时间内疯狂枚举分裂、淹没下游。`enumeratorPosition` 虽正确恢复（不会重复扫描已处理位置），但反压信号丢失。

4. **修复对称性**：`snapshotState` 返回 `new IcebergEnumeratorState(enumeratorPosition.get(), assigner.state(), enumerationHistory.snapshot())`，三者都被持久化。修复前构造器只还原第 1 项；修复后补上第 3 项（第 2 项 `assigner.state()` 由 `super` 或 `assigner` 自身在别处恢复）。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/ContinuousIcebergEnumerator.java`

**修改目的**：在枚举器从检查点状态恢复时，完整还原 `enumerationHistory`。

**工作逻辑**：构造器中 `if (enumState != null)` 分支原本只有：
```java
this.enumeratorPosition.set(enumState.lastEnumeratedPosition());
```
修复后变为：
```java
this.enumeratorPosition.set(enumState.lastEnumeratedPosition());
this.enumerationHistory.restore(enumState.enumerationSplitCountHistory());
```

- `enumState` 是 `@Nullable IcebergEnumeratorState`，首次启动时为 `null`（不进入该分支），从 checkpoint/savepoint 恢复时非空。
- `enumState.enumerationSplitCountHistory()` 返回检查点中保存的分裂数历史列表（即 `enumerationHistory.snapshot()` 的产物）。
- `this.enumerationHistory` 是构造器中刚 `new` 出来的空历史对象，`restore(...)` 用持久化的历史数据填充它，使后续 `shouldPauseSplitDiscovery` 能基于恢复后的历史继续工作。
- 该改动只发生在 `enumState != null` 分支，对首次启动路径无影响，行为零回归。

## 小结

**成效**：补齐了 checkpoint 恢复路径上缺失的状态还原项，使连续枚举的反压机制在 failover 后依然有效，避免恢复瞬间因历史丢失导致的分裂枚举失控。属于正确性/稳定性修复。

**影响范围**：仅 Flink 1.18 模块的 `ContinuousIcebergEnumerator`，且仅在 checkpoint 恢复路径生效。对无 failover 的正常运行无影响。

**回迁到 1.4.x 的注意事项**：

1. 需确认 1.4.x 的 Flink 1.18 集成代码结构与本提交一致：`ContinuousIcebergEnumerator` 是否持有 `enumerationHistory` 字段、`IcebergEnumeratorState` 是否已包含 `enumerationSplitCountHistory()` 访问器。若 1.4.x 较旧，`EnumerationHistory`/`IcebergEnumeratorState` 可能尚不存在或不保存该字段——此时本修复不适用，需先回迁引入这些类的设计提交。
2. 若 1.4.x 同时维护 Flink 1.17/1.18/1.19 多版本，需检查同一 bug 是否存在于其他版本的 `ContinuousIcebergEnumerator`（通常多版本目录如 `flink/v1.17/...`、`flink/v1.19/...` 需同步修复，但本提交只改了 v1.18，可能是其他版本在各自分支单独处理）。
3. 一行改动，cherry-pick 冲突风险极低，但需保证 `enumState.enumerationSplitCountHistory()` 方法签名在目标分支存在。
