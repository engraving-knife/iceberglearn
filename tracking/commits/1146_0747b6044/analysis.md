# 提交 1146：Kafka Connect: Terminate commits on coordinator stop (#10814)

## 提交信息

- **序号**：1146
- **哈希**：0747b604413fa6d3c663ec850dbe6f50a647ca04
- **短哈希**：0747b6044
- **日期**：2024-09-10（Tue Sep 10 15:54:20 2024 -0700）
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：Kafka Connect: Terminate commits on coordinator stop (#10814)
- **PR/Issue**：#10814

## 总体目的

Iceberg Kafka Connect Sink Connector 中，`Coordinator` 是负责集中式 commit 的协调器：它在一个独立线程 `CoordinatorThread` 中循环 `process()`，每轮从控制 topic 拉取各 task 上报的写入结果，然后对每张表执行 Iceberg commit（`AppendFiles` 或 `RowDelta`）。当 connector 被 Kafka Connect 框架停止时，原有逻辑会调用 `Coordinator.stop()` 关闭线程池并等待终止。

问题在于：`stop()` 仅关闭了 executor，但**正在进行的 commit 流程并不会感知到停止信号**。如果 `process()` 已经进入 `commit()` 方法的 Iceberg 提交阶段（已加载表、已收集 dataFiles/deleteFiles），即便外部调用了 `stop()`，commit 仍会继续执行 `table.newAppend()...commit()`。这会导致：
1. connector 已被认为停止，但实际仍在后台向 Iceberg 表提交数据，造成"幽灵提交"；
2. commit 完成后会向控制 topic 写入提交完成事件，但此时 producer/consumer 可能已被关闭，引发资源泄漏或异常；
3. 在 rebalance/重启场景下，新的 coordinator 可能与旧 coordinator 同时尝试 commit，造成重复提交或冲突。

本提交通过引入显式的 `terminate()` 方法与 `terminated` 标志位，在 coordinator 停止时尽快中断 commit 流程：在 `commit()` 真正执行 Iceberg 提交前检查 `terminated`，若已终止则抛出 `ConnectException` 中止本次提交。同时把原 `stop()` 改名为 `terminate()`，并移除对父类 `super.stop()` 的调用（让父类 `Channel.stop()` 中 producer/consumer/admin 的关闭交由其它路径处理），并把若干 `RuntimeException` 替换为 Kafka Connect 标准的 `ConnectException`，让框架能正确识别与重试。

## 如何达成设计目的

1. **引入 `terminated` 标志**：在 `Coordinator` 中新增 `private volatile boolean terminated;`，使用 `volatile` 保证多线程可见性（`CoordinatorThread` 与调用 `stop()` 的线程不同）。
2. **新增 `terminate()` 方法**：将原 `stop()` 改名为 `terminate()`，先设置 `terminated = true`，再 `exec.shutdownNow()`，再 `awaitTermination` 等待线程池退出。超时或被中断时抛 `ConnectException`（而非原来的 `RuntimeException`），让 Connect 框架能识别为可重试错误。**不再调用 `super.stop()`**——父类 `Channel.stop()` 中关闭 producer/consumer/admin 的动作改由其它停止路径负责，避免在 commit 仍可能进行时过早关闭这些资源。
3. **commit 前置检查**：在 `commit()` 方法收集完 dataFiles/deleteFiles、即将进入 Iceberg 提交分支之前，插入 `if (terminated) throw new ConnectException("Coordinator is terminated, commit aborted");`。这样一旦 `terminate()` 被调用，正在进行的 `commit()` 会在执行实际提交前主动中止，避免"幽灵提交"。
4. **`CoordinatorThread.terminate()` 联动**：`CoordinatorThread` 原本的 `terminate()` 只设置自身 `terminated` 标志让循环退出，现在追加 `coordinator.terminate()` 调用，让"线程停止"与"coordinator 停止"联动触发。
5. **小清理**：把 `CoordinatorThread` 中的 `coordinator` 字段改为 `final`、删去循环退出后 `coordinator = null;` 的赋值（字段为 final 后不能再赋值，且该 null 化无实际意义——线程结束后对象自然可回收）；并将 `terminated = true` 统一改为 `this.terminated = true` 以提高可读性。
6. **`RecordConverter` 异常类型规范化**：把 `convertDateValue`、`convertTimeValue`、`convertTimestampValue`、`convertTimestamptzValue` 四个方法中"无法转换时抛 `RuntimeException`"统一改为抛 `ConnectException`，让 Kafka Connect 框架能把这类数据转换错误识别为 connector 级异常（便于框架按策略重试或标记 task 失败），而不是被当作未知运行时异常吞掉。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/Coordinator.java`

**修改目的**：引入 `terminated` 标志与 `terminate()` 方法，让 commit 流程能感知停止信号。

**工作逻辑**：

1. 新增 import：`org.apache.kafka.connect.errors.ConnectException`。
2. 新增字段 `private volatile boolean terminated;`（在 `commitState` 字段之后）。`volatile` 保证 `CoordinatorThread` 读、`terminate()` 写的可见性。
3. 在 `commit()` 方法中，紧跟 `deleteFiles` 列表构建之后、空文件检查之前，插入：
   ```java
   if (terminated) {
     throw new ConnectException("Coordinator is terminated, commit abortted");
   }
   ```
   这确保一旦 `terminate()` 被调用，正在 `commit()` 中的请求会在执行 `table.newAppend()...commit()` 之前主动抛出，避免向 Iceberg 表写入"已被停止"的数据。
4. 将原 `void stop()` 方法改名为 `void terminate()`，并调整实现：
   - 开头新增 `this.terminated = true;`，让标志位先于 `exec.shutdownNow()` 设置，确保即使 commit 正在执行也能在下一次检查点感知；
   - `exec.shutdownNow()` 不变；
   - 把 `awaitTermination` 超时抛出的 `RuntimeException("Timed out waiting for coordinator shutdown")` 改为 `ConnectException(...)`；
   - 把中断时抛出的 `RuntimeException("Interrupted while waiting for coordinator shutdown", e)` 改为 `ConnectException(...)`；
   - 删除方法末尾的 `super.stop();` 调用。父类 `Channel.stop()` 负责关闭 `producer`/`consumer`/`admin`，这些资源的关闭改由 connector 的其它停止路径处理，避免在 commit 仍可能进行时过早关闭导致提交过程中访问已关闭的资源。
   - 注释也相应从 "ensure coordinator tasks are shut down, else cause the sink worker to fail" 调整为 "wait for coordinator termination, else cause the sink task to fail"。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/channel/CoordinatorThread.java`

**修改目的**：让线程终止时联动调用 `coordinator.terminate()`，并做小幅代码清理。

**工作逻辑**：

1. 将字段 `private Coordinator coordinator;` 改为 `private final Coordinator coordinator;`。该字段在构造方法赋值后无需再变，改为 final 后可在 `run()` 末尾删除 `coordinator = null;` 这一无意义赋值（final 字段不能再次赋值，且对象生命周期由 GC 管理，手动置 null 无收益）。
2. `run()` 方法中两处 `terminated = true;`（启动失败分支与 process 异常分支）统一改为 `this.terminated = true;`，仅是可读性优化。
3. `run()` 方法末尾的 `coordinator.stop();` 调用保留（注释 "coordinator error during stop, ignoring" 不变），但删除其后的 `coordinator = null;`。
4. `terminate()` 方法由：
   ```java
   void terminate() {
     terminated = true;
   }
   ```
   改为：
   ```java
   void terminate() {
     this.terminated = true;
     coordinator.terminate();
   }
   ```
   即线程级 `terminate()` 现在会联动调用 `coordinator.terminate()`，让 coordinator 的 `terminated` 标志、`exec.shutdownNow()` 与 `awaitTermination` 一并触发。这是本次修复的关键联动点——此前 `CoordinatorThread.terminate()` 只让循环退出，但 `coordinator` 内部的 executor 仍可能在跑 commit；现在通过联动确保两边的终止信号同步。

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordConverter.java`

**修改目的**：将数据转换失败时的异常类型从 `RuntimeException` 改为 `ConnectException`。

**工作逻辑**：

1. 新增 import：`org.apache.kafka.connect.errors.ConnectException`。
2. 在四个转换方法中，把无法识别的值类型时抛出的 `RuntimeException` 改为 `ConnectException`，消息内容保持不变：
   - `convertDateValue`：`throw new RuntimeException("Cannot convert date: " + value);` → `throw new ConnectException("Cannot convert date: " + value);`
   - `convertTimeValue`：`throw new RuntimeException("Cannot convert time: " + value);` → `throw new ConnectException("Cannot convert time: " + value);`
   - `convertTimestampValue`（针对 `TimestampType`）：`throw new RuntimeException("Cannot convert timestamptz: " + value + ", type: " + value.getClass());` → `throw new ConnectException(...)`
   - `convertTimestampValue`（针对 `TimestamptzType` 重载）：`throw new RuntimeException("Cannot convert timestamp: " + value + ", type: " + value.getClass());` → `throw new ConnectException(...)`

   这样 Connect 框架能将这些异常识别为 connector 级别的可重试/可标记错误，便于运维监控与故障定位。

## 小结

- **成效**：修复了 Kafka Connect Sink Coordinator 在停止时仍可能继续向 Iceberg 表提交数据的"幽灵提交"问题。通过 `terminated` 标志 + commit 前置检查 + `CoordinatorThread.terminate()` 联动调用 `coordinator.terminate()`，确保一旦 connector 被停止，正在进行的 commit 会在实际提交前主动中止；同时把多处 `RuntimeException` 规范化为 `ConnectException`，让框架能正确识别与处理。
- **影响范围**：仅 `Coordinator.java`（+12/-7）、`CoordinatorThread.java`（+5/-5）、`RecordConverter.java`（+5/-4）三个文件，共 +22/-16 行。变更集中在 Kafka Connect 模块，影响 Sink Connector 的停止与异常处理路径。
- **回迁到 1.4.x 的注意事项**：
  - 这是**重要的行为修复**——若 1.4.x 包含 Kafka Connect 模块且存在相同的"停止后仍提交"问题，**强烈建议回迁**，否则在 rebalance/停止/重启场景下可能出现重复提交或资源泄漏。
  - 回迁前需确认 1.4.x 的 `Coordinator` / `CoordinatorThread` / `Channel` 类结构与本提交时一致。若 1.4.x 的 `Channel.stop()` 行为有差异（例如 producer/consumer/admin 的关闭责任划分不同），需评估"删除 `super.stop()` 调用"是否会留下资源未关闭的隐患——本提交假定这些资源的关闭已由其它路径接管，回迁时需验证该假设在 1.4.x 中是否成立。
  - `RecordConverter` 的异常类型规范化是独立的小改进，可单独回迁，无副作用。
  - 回迁后建议在 1.4.x 的集成测试中验证：停止 connector 时，正在进行的 commit 是否被正确中止，且控制 topic 不会因 producer 已关闭而出现写入异常。
