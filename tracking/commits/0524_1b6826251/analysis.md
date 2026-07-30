# 提交 0524：Flink 1.18: Fix iceberg source plan parallelism not effective

## 提交信息

- **序号**：0524 / 4088
- **哈希**：1b6826251b8cc5f688fb7f86b57841abb5693b11
- **短哈希**：1b6826251
- **日期**：2024-02-20 20:47:25 -0800
- **作者**：Reo <leinuowen@gmail.com>
- **提交说明**：Flink 1.18: Fix iceberg source plan parallelism not effective. (#9761)
- **PR/Issue**：#9761

## 总体目的

修复 Flink 1.18 Iceberg Source 中"分裂规划（split planning）并发度"配置不生效的 bug。`FlinkReadConf.workerPoolSize()` 用于读取规划线程池大小（控制 Iceberg Source 在规划分裂时使用多少线程并行扫描元数据/规划分裂），但该方法在构建配置解析链时漏掉了 `.option(...)` 这一步，导致用户通过表级/读取级选项（即与 `table.exec.iceberg.worker-pool-size` 同名的 option key）设置的并发度被完全忽略，只读取 Flink 全局配置与默认值。结果是即便用户调大了 worker pool size，规划并发度仍停留在默认值，大表规划耗时无法通过该配置优化。

## 如何达成设计目的

修复方式是在 `workerPoolSize()` 的 `confParser` 链上补一行 `.option(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.key())`，使其与同文件其他配置项（`maxPlanningSnapshotCount`、`limit`、`includeColumnStats` 等）的解析结构对齐。

要理解这一行的意义，需理清 Iceberg Flink `confParser` 的三段式解析优先级（从高到低）：

1. **`.option(key)`**：从表级/读取级 option 中按 key 读取。这一层支持 per-table 或 per-read 的覆盖（例如通过 SQL hint、表属性、`FlinkReadOptions` 传入的同名 option）。
2. **`.flinkConfig(configOption)`**：从 Flink 全局配置（`TableConfig` / 全局 `Configuration`）读取，对应 `table.exec.iceberg.worker-pool-size` 这类全局参数。
3. **`.defaultValue(value)`**：兜底默认值。

`confParser` 按上述顺序取第一个非空值作为最终结果。修复前 `workerPoolSize()` 缺失第 1 步，意味着：
- 用户通过表属性或 SQL 设置 `table.exec.iceberg.worker-pool-size=N` 想覆盖单表规划并发度时，该设置进入 option 层但因 `.option(...)` 缺失而不会被读取；
- 只有写入 Flink 全局 `TableConfig` 的值（第 2 步）或默认值（第 3 步）才生效，per-table 覆盖能力丢失。

补上 `.option(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.key())` 后，option 层也参与解析，用户可在表级/读取级覆盖规划并发度，"plan parallelism" 配置真正生效。

注意此处 `.option(...)` 传入的是 `FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.key()`（即 Flink ConfigOption 的 key 字符串），而同文件大多数其他方法传入的是 `FlinkReadOptions.X`（一个 `FlinkReadOption` 对象）。这是因为 worker pool size 的 option key 复用了 Flink ConfigOption 的 key，二者同名，使得"表级 option"与"Flink 全局配置"共享同一个 key，只是解析入口不同。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/FlinkReadConf.java`

**修改目的**：补齐 `workerPoolSize()` 的配置解析链，使其支持表级/读取级 option 覆盖。

**工作逻辑**：修改前：
```java
public int workerPoolSize() {
  return confParser
      .intConf()
      .flinkConfig(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE)
      .defaultValue(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.defaultValue())
      .parse();
}
```

修改后：
```java
public int workerPoolSize() {
  return confParser
      .intConf()
      .option(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.key())
      .flinkConfig(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE)
      .defaultValue(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE.defaultValue())
      .parse();
}
```

- 新增的 `.option(...)` 调用插入在 `.intConf()` 之后、`.flinkConfig(...)` 之前，与 `maxPlanningSnapshotCount()`、`limit()` 等同构方法的链顺序一致（option → flinkConfig → defaultValue）。
- `confParser` 在 `parse()` 时按 option > flinkConfig > defaultValue 的优先级取值，因此表级 option 优先级最高，可覆盖全局配置。
- `workerPoolSize()` 的返回值被 Iceberg Source 用于构造规划线程池（通常在 `IcebergSource`/`ScanContext` 中决定并行规划分裂的线程数）。修复后，调大该配置可显著缩短大表分裂规划耗时。
- 该改动不影响默认行为（当 option 层未设置时，仍走 flinkConfig 与 defaultValue，与修复前等价），仅在用户显式设置表级 option 时产生差异，回归风险低。

## 小结

**成效**：恢复了 `workerPoolSize` 的 per-table/per-read 覆盖能力，用户可通过表属性或 SQL 设置规划并发度并真正生效，提升大表读取的规划性能。属于配置可用性修复。

**影响范围**：仅 Flink 1.18 模块的 `FlinkReadConf.workerPoolSize()`，影响分裂规划线程池大小的解析。对未显式设置该 option 的用户无行为变化。

**回迁到 1.4.x 的注意事项**：

1. 需确认 1.4.x 的 `FlinkReadConf` 是否存在同样的遗漏——若 1.4.x 的 Flink 1.18 集成代码同样缺失 `.option(...)`，则本修复应直接 cherry-pick。
2. 需确认 1.4.x 的 `confParser` API 支持 `.option(String key)` 重载（按 key 字符串读取 option）。若 1.4.x 的 confParser 实现只接受 `FlinkReadOption` 对象而不接受裸 key，则需调整调用形式。
3. 一行改动，cherry-pick 冲突风险低；但需检查 1.4.x 是否同时维护 Flink 1.17/1.19 等多版本目录，同一 bug 可能需在各版本目录分别修复（本提交仅改 v1.18）。
4. `FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE` 需在 1.4.x 中已定义；该 ConfigOption 通常较早就存在，回迁应无障碍。
