# 提交 3648：Flink: Do not ship optional flink-metrics-dropwizard dependency (#16155)

## 提交信息

- **序号**：3648 / 4088
- **哈希**：680d850e9e2069919b6ed1207772b53dddcad963
- **短哈希**：680d850e9
- **日期**：2026-05-06 11:17:27 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Do not ship optional flink-metrics-dropwizard dependency (#16155)
- **PR/Issue**：#16155

## 总体目的

这个提交将 Flink 的可选依赖 `flink-metrics-dropwizard` 从 `iceberg-flink-runtime`（Flink 2.1）的打包中移除，改为在运行时通过反射动态加载。

此前，Iceberg 的 Flink sink 使用 Dropwizard 的 `Histogram` 来统计数据文件和删除文件的大小分布直方图。为了使用 `DropwizardHistogramWrapper`，`iceberg-flink-runtime` JAR 包直接打包了 `flink-metrics-dropwizard` 及其依赖 `metrics-core`。然而 `flink-metrics-dropwizard` 是 Flink 的可选模块，并非所有 Flink 部署都包含它。将一个可选依赖强制打包进 runtime JAR 会导致不必要的依赖膨胀，并可能与用户环境中已有的版本冲突。

本提交将直方图指标改为通过反射动态加载 Dropwizard 类：如果 classpath 中存在 `flink-metrics-dropwizard`，则正常注册直方图指标；如果不存在，则跳过直方图指标（返回 null），其他指标类型（Counter、Gauge）不受影响。这样 runtime JAR 不再需要打包该可选依赖。

## 如何达成设计目的

1. 从 `flink/v2.1/build.gradle` 的 `flink-runtime` 模块移除 `implementation libs.flink21.metrics.dropwizard`。
2. 从 `flink-runtime` 的 LICENSE 和 runtime-deps.txt 中移除 Dropwizard Metrics 相关条目。
3. 重写 `IcebergStreamWriterMetrics`：使用 `DynClasses`/`DynConstructors` 反射加载 `com.codahale.metrics.SlidingWindowReservoir`、`com.codahale.metrics.Histogram`、`org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper`，加载失败时记录警告并返回 null；直方图注册和使用时进行 null 检查。
4. 更新文档说明直方图指标需要用户自行添加 `flink-metrics-dropwizard` 到 classpath。

## 修改详情

### `flink/v2.1/build.gradle` (+0/-3 lines)

**修改目的**：从 flink-runtime 移除 dropwizard 依赖。

**工作逻辑**：移除 `implementation libs.flink21.metrics.dropwizard` 及其注释。

### `flink/v2.1/flink-runtime/LICENSE` (+0/-16 lines)

**修改目的**：移除 Dropwizard Metrics 和 Flink dropwizard 支持的 LICENSE 条目。

**工作逻辑**：删除两段关于 Dropwizard Metrics 和 Apache Flink optional dropwizard support 的版权声明。

### `flink/v2.1/flink-runtime/runtime-deps.txt` (+0/-2 lines)

**修改目的**：从运行时依赖列表移除 dropwizard 相关依赖。

**工作逻辑**：移除 `io.dropwizard.metrics:metrics-core:3.2.6` 和 `org.apache.flink:flink-metrics-dropwizard:2.1.0`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergStreamWriterMetrics.java` (+89/-20 lines)

**修改目的**：改为反射动态加载 Dropwizard 直方图，支持可选依赖。

**工作逻辑**：
1. 移除对 `com.codahale.metrics.SlidingWindowReservoir`、`DropwizardHistogramWrapper` 的直接 import，改为运行时反射加载。
2. 新增静态字段 `DROPWIZARD`，通过 `loadDropwizardCtors()` 在类加载时尝试加载三个构造器（reservoir、codahale histogram、wrapper），失败返回 null 并记录警告。
3. 新增 `DropwizardCtors` record 封装三个构造器。
4. 新增 `registerHistogram(MetricGroup, String)` 和 `newDropwizardHistogram()` 方法：若 DROPWIZARD 为 null 则返回 null，否则通过反射创建直方图。
5. 构造函数中改为调用 `registerHistogram`，直方图字段可能为 null。
6. `updateFlushResult` 中对直方图做 null 检查后再更新。
7. 新增 `@VisibleForTesting` 的 getter 方法用于测试。

```java
private static Histogram newDropwizardHistogram() {
  if (DROPWIZARD == null) {
    return null;
  }
  Object reservoir = DROPWIZARD.reservoirCtor.newInstance(HISTOGRAM_RESERVOIR_SIZE);
  Object codahaleHistogram = DROPWIZARD.histogramCtor.newInstance(reservoir);
  return DROPWIZARD.wrapperCtor.newInstance(codahaleHistogram);
}
```

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergStreamWriterMetrics.java` (+42 lines)

**修改目的**：新增测试验证无 Dropwizard 时的行为。

### `docs/docs/flink-writes.md` (+3/-3 lines)

**修改目的**：更新文档说明直方图指标的可选性。

**工作逻辑**：将原"runtime 已打包该依赖"的说明改为"用户需自行添加 `flink-metrics-dropwizard` 到 classpath，若不存在则直方图指标缺失，其他指标正常发布"。

## 总结

这个提交将 Flink 2.1 runtime JAR 中的可选依赖 `flink-metrics-dropwizard` 移除，改为通过反射在运行时动态加载。这减少了 runtime JAR 的依赖膨胀和潜在版本冲突，同时保持直方图指标在用户 classpath 包含该依赖时仍可用。实现上使用 Iceberg 的 `DynClasses`/`DynConstructors` 工具进行反射加载，并对加载失败和直方图为 null 的情况做了妥善处理，确保其他指标类型不受影响。
