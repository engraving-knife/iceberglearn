# 提交 1071：Flink: FLIP-27 IcebergSource builder missed a couple of configs compared to old FlinkSource: expose locality and plan parallelism (#10957)

## 提交信息

- **序号**：1071 / 4088
- **哈希**：43bbf08adc63c0a5a5edb26934f62d9994c25af1
- **短哈希**：43bbf08ad
- **日期**：2024-08-19 15:29:47 -0700
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Flink: FLIP-27 IcebergSource builder missed a couple of configs compared to old FlinkSource: expose locality and plan parallelism (#10957)
- **PR/Issue**：#10957

## 总体目的

Iceberg 的 Flink 集成同时存在两套 Source API：

1. 旧的 `FlinkSource`（基于 `SourceFunction` / 较老的 source 接口）；
2. 新的 `IcebergSource`（基于 Flink 1.12+ 引入的 FLIP-27 现代 `Source` 接口，对应 `org.apache.flink.api.connector.source.Source`）。

`IcebergSource` 的 `Builder` 类对外暴露了 `exposeLocality(boolean)` 与 `planParallelism(int)` 两个配置方法——前者控制读取时是否暴露数据本地性信息（让 Flink 调度器尽量把读取子任务调度到数据所在节点），后者控制计划阶段（plan）用于扫描表/拆分分片的线程池并行度。Builder 内部也确实保存了这两个值：`exposeLocality` 存为字段，`planParallelism(int)` 则把值写入 `flinkConfig` 的 `FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE` 键。

但在 `IcebergSource.Builder` 的构造终态方法（构造 `ScanContext` 之前的 `contextBuilder.resolveConfig(table, readOptions, flinkConfig)` 之后），并没有把这两个值透传给 `ScanContext.Builder contextBuilder`。结果就是：即使用户调用了 `.exposeLocality(true)` 或 `.planParallelism(4)`，这两个配置也不会进入 `ScanContext`，最终读取行为并不会按用户预期启用本地性或调整 plan 线程池大小。这是新 `IcebergSource` 相对旧 `FlinkSource` 的功能回退（regression）。

本提交的目的是补上这两行遗漏的"透传"代码，让 `IcebergSource` 在这两项配置上与旧 `FlinkSource` 行为对齐。

## 如何达成设计目的

实现方式非常直接：在 `IcebergSource.Builder` 中，紧跟在 `contextBuilder.resolveConfig(table, readOptions, flinkConfig)` 之后，补两行：

```java
contextBuilder.exposeLocality(
    SourceUtil.isLocalityEnabled(table, flinkConfig, exposeLocality));
contextBuilder.planParallelism(
    flinkConfig.get(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE));
```

设计要点：

- `exposeLocality` 不是简单地把 Builder 字段透传，而是通过 `SourceUtil.isLocalityEnabled(table, flinkConfig, exposeLocality)` 做一次"合并解析"——综合考虑表属性、flinkConfig 配置、Builder 显式入参三者的优先级，得到最终的本地性开关。这与旧 `FlinkSource` 的处理路径一致，避免出现"Builder 入参覆盖了表属性/flinkConfig 中明确禁用本地性"的错误覆盖；
- `planParallelism` 直接从 `flinkConfig` 读取 `TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE`。这是因为 `Builder.planParallelism(int)` 方法本身就把传入的 int 写到了 flinkConfig 的这个键下，所以从 flinkConfig 读回就能同时覆盖两种来源：用户调用了 `Builder.planParallelism(int)`，或者用户直接在 flinkConfig 里设置了 `table.exec.iceberg.worker-pool-size`。这样统一读取入口，逻辑更简洁；
- 该修改在 v1.18、v1.19、v1.20 三个 Flink 版本模块的 `IcebergSource.java` 中完全一致地应用（diff 内容相同），保证三个支持的 Flink 版本同步修复。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`

**修改目的**：在 FLIP-27 `IcebergSource.Builder` 构造 `ScanContext` 之前，把 `exposeLocality` 与 `planParallelism` 透传给 `ScanContext.Builder`，修复这两项配置被静默忽略的回归。

**工作逻辑**：在 `contextBuilder.resolveConfig(table, readOptions, flinkConfig);` 之后新增 4 行（2 条语句）：

```java
contextBuilder.exposeLocality(
    SourceUtil.isLocalityEnabled(table, flinkConfig, exposeLocality));
contextBuilder.planParallelism(
    flinkConfig.get(FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE));
```

后续 `project(...)`、`contextBuilder.build()` 等流程不变，但 `ScanContext` 现在能拿到正确的本地性与 plan 线程池大小配置。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`

**修改目的**：与 v1.18 完全相同，同步修复 v1.19 模块。

**工作逻辑**：diff 内容与 v1.18 一字不差，在 `resolveConfig` 之后补同样的 4 行。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/IcebergSource.java`

**修改目的**：与 v1.18、v1.19 完全相同，同步修复 v1.20 模块。

**工作逻辑**：diff 内容与上两个模块一字不差，在 `resolveConfig` 之后补同样的 4 行。

## 小结

- **成效**：修复了 FLIP-27 `IcebergSource.Builder` 中 `exposeLocality(boolean)` 与 `planParallelism(int)` 两项配置被静默忽略的回归，使其与旧 `FlinkSource` 行为对齐；用户现在调用这两个 builder 方法（或在 flinkConfig 中设置 `table.exec.iceberg.worker-pool-size`）能真正生效。修复同步应用到 v1.18、v1.19、v1.20 三个 Flink 版本模块。
- **影响范围**：仅修改 3 个 `IcebergSource.java` 文件（v1.18/v1.19/v1.20 各 1 个），每个文件新增 4 行（2 条语句），共 12 行新增、0 行删除；不影响 sink、catalog、core 等其他模块；不改变任何公开 API 签名，仅修正已有 API 的实际行为。
- **回迁到 1.4.x 的注意事项**：这是一次 bug 修复，回迁到 1.4.x 风险很低，建议回迁。需注意：(1) 1.4.x 维护的 Flink 版本模块可能与 main 不同（1.4.x 时期可能是 v1.15/v1.16/v1.17/v1.18），需要确认对应模块下的 `IcebergSource.java` 是否存在相同的"透传遗漏"——如果 1.4.x 的 `IcebergSource.Builder` 同样在 `resolveConfig` 之后缺少这两行，则可直接 cherry-pick 对应模块的改动；(2) 需确认 1.4.x 上 `SourceUtil.isLocalityEnabled(table, flinkConfig, exposeLocality)` 方法签名与 `FlinkConfigOptions.TABLE_EXEC_ICEBERG_WORKER_POOL_SIZE` 配置键都已存在（这两个依赖项应早于本提交就有）；(3) 该修复不引入新 API，对用户透明，仅让原本就该生效的配置真正生效，向后兼容。
