# 提交 1258：Flink: Add IcebergSinkBuilder interface allowed unification of most of operations on FlinkSink and IcebergSink Builders (#11305)

## 提交信息

- **序号**：1258 / 4088
- **哈希**：d4f0d7e8036488924edf9acdc2aed6fc3a3415e1
- **短哈希**：d4f0d7e80
- **日期**：2024-10-21（Mon Oct 21 06:35:03 2024 +0200）
- **作者**：Arek Burdach <arek.burdach@gmail.com>
- **提交说明**：Flink: Add IcebergSinkBuilder interface allowed unification of most of operations on FlinkSink and IcebergSink Builders (#11305)
- **PR/Issue**：#11305

## 总体目的

Iceberg 的 Flink 集成同时维护两套 Sink 实现：基于旧 `SinkFunction`/`StreamSink` 的 `FlinkSink`，以及基于 Flink SinkV2 API 的新版 `IcebergSink`。两者的 `Builder` 内部类提供几乎相同的一组配置方法（`table`、`tableLoader`、`setAll`、`tableSchema`、`overwrite`、`flinkConf`、`distributionMode`、`writeParallelism`、`upsert`、`equalityFieldColumns`、`toBranch`、`append` 等），但彼此无共同契约。这导致上层调用方（如 Flink SQL 的 sink 工厂）若想在新旧 Sink 间切换，必须写两套分支代码，且难以保证两套 Builder 行为一致。

本提交引入一个内部接口 `IcebergSinkBuilder<T>`，把两个 Builder 共有的大部分配置方法抽象为统一契约，并让 `FlinkSink.Builder` 与 `IcebergSink.Builder` 都实现该接口。接口还提供两个静态工厂 `forRow(...)` 与 `forRowData(...)`，根据 `useV2Sink` 布尔标志返回对应实现（`IcebergSink` 或 `FlinkSink`）的 Builder。Javadoc 明确这是「过渡桥梁」：在彻底移除旧 `FlinkSink` 之前，用统一接口让调用方按标志透明切换实现；移除后该接口会被直接使用 `IcebergSink` 替代。

这是为 Flink 集成从旧 Sink 向 SinkV2 平滑迁移的关键基础设施。本提交只作用于 Flink 1.20 模块。

## 如何达成设计目的

通过抽象出一个公共接口并让两个 Builder 实现它，达成「同一套 API 操作两种 Builder」：

- **接口抽象**：`IcebergSinkBuilder<T extends IcebergSinkBuilder<?>>` 定义通用配置方法（均返回泛型 `T` 以支持链式）。`append()` 因两实现返回类型不同（`DataStreamSink<Void>` vs `DataStreamSink<RowData>`），接口声明为 `DataStreamSink<?>` 兼容两者。
- **Builder 实现接入**：两个 Builder 加 `implements IcebergSinkBuilder<Builder>`，共有方法加 `@Override`（接口未要求新增逻辑，仅声明契约）。各自保留的非共有方法（如 `IcebergSink.Builder` 的 `uidSuffix` 等）不受影响。
- **工厂方法**：`forRow(input, tableSchema, useV2Sink)` 与 `forRowData(input, useV2Sink)` 根据 `useV2Sink` 选择返回 `IcebergSink.forRow(...)` 或 `FlinkSink.forRow(...)` 的 Builder，把「选哪个 Sink」的决策集中到一处。
- **@Internal 标注**：接口标记 `@Internal`，表明这是过渡期内部 API，不对外稳定承诺，符合「最终会被移除」的定位。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSinkBuilder.java`（新增）

**修改目的**：定义统一新旧 Sink Builder 的内部契约与工厂。

**工作逻辑**：`@Internal` 包级接口，泛型 `T extends IcebergSinkBuilder<?>`。声明以下方法（均返回 `T`）：`tableSchema`、`tableLoader`、`equalityFieldColumns`、`overwrite`、`setAll`、`flinkConf`、`table`、`writeParallelism`、`distributionMode`、`toBranch`、`upsert`，以及 `DataStreamSink<?> append()`。类级 Javadoc 说明这是 `FlinkSink`（旧）与 `IcebergSink`（新 SinkV2）之间的过渡抽象，移除旧实现后所有引用会被直接替换为 `IcebergSink`。

提供两个静态工厂：

```java
static IcebergSinkBuilder<?> forRow(DataStream<Row> input, TableSchema tableSchema, boolean useV2Sink) {
  if (useV2Sink) return IcebergSink.forRow(input, tableSchema);
  else return FlinkSink.forRow(input, tableSchema);
}

static IcebergSinkBuilder<?> forRowData(DataStream<RowData> input, boolean useV2Sink) {
  if (useV2Sink) return IcebergSink.forRowData(input);
  else return FlinkSink.forRowData(input);
}
```

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java`

**修改目的**：让旧 `FlinkSink.Builder` 实现统一接口。

**工作逻辑**：`public static class Builder` 改为 `public static class Builder implements IcebergSinkBuilder<Builder>`；对与接口共有的方法（`table`、`tableLoader`、`setAll`、`tableSchema`、`overwrite`、`flinkConf`、`distributionMode`、`writeParallelism`、`upsert`、`equalityFieldColumns`、`toBranch`、`append`）逐一加 `@Override`。方法体不变，`append()` 仍返回 `DataStreamSink<Void>`（协变返回类型兼容接口的 `DataStreamSink<?>`）。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java`

**修改目的**：让新 `IcebergSink.Builder` 实现统一接口。

**工作逻辑**：`public static class Builder` 改为 `implements IcebergSinkBuilder<Builder>`；对共有方法加 `@Override`，与 FlinkSink.Builder 对称。`distributionMode` 在 `IcebergSink.Builder` 中额外有 `RANGE` 模式不支持的前置校验（抛异常），与接口契约一致但实现更严。`append()` 返回 `DataStreamSink<RowData>`（协变兼容 `DataStreamSink<?>`）。

## 小结

- **成效**：引入内部接口 `IcebergSinkBuilder`，统一 `FlinkSink.Builder` 与 `IcebergSink.Builder` 的大部分配置方法契约，并提供 `forRow`/`forRowData` 工厂按 `useV2Sink` 标志透明切换实现；为上层调用方提供「一套 API 操作两种 Sink」的过渡桥梁，降低从旧 Sink 迁移到 SinkV2 的成本与分支代码复杂度。
- **影响范围**：仅 `flink/v1.20` 模块 3 个文件（1 新增接口 + 2 改动 Builder），共约 109 行新增（大部分是 `@Override` 标注与新增接口），无运行时逻辑变化——接口只是声明契约，Builder 方法体未改。属纯结构性重构，不改变任何 Sink 的写入行为。
- **回迁到 1.4.x 的注意事项**：这是 main 分支上为新旧 Sink 统一而引入的过渡抽象。1.4.x 若仍以旧 `FlinkSink` 为主且无 SinkV2 迁移计划，**无需回迁**——该接口本身不带来功能，仅为后续迁移服务。若 1.4.x 确要在新旧 Sink 间切换，可回迁接口与 Builder 的 `implements` 改动（纯增量、向后兼容，加 `@Override` 不破坏既有调用）；但需注意接口的 `@Internal` 定位与「最终移除」意图，回迁后应保持同样的临时性认知。此外本提交（#11305）合并后曾引发 range distribution bucketing 测试 flaky（见 #11347 / 本批序号 1253），回迁时若同步带上相关测试需关注稳定性。
