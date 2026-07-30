# 提交 3652：Flink: Backport removal of optional flink-metrics-dropwizard dependency to v2.0 and v1.20 (#16230)

## 提交信息

- **序号**：3652 / 4088
- **哈希**：d7cb7994510c08d2b55352195773763d93243d9c
- **短哈希**：d7cb79945
- **日期**：2026-05-06 09:32:43 -0700
- **作者**：Kevin Liu
- **提交说明**：Flink: Backport removal of optional flink-metrics-dropwizard dependency to v2.0 and v1.20 (#16230)
- **PR/Issue**：#16230（backport #16155）

## 总体目的

这个提交是将 PR #16155（即本系列第 3648 号提交）从 Flink 2.1 backport 到 Flink 1.20 和 Flink 2.0 的版本。

该改动将 Flink 的可选依赖 `flink-metrics-dropwizard` 从 `iceberg-flink-runtime` 的打包中移除，改为在运行时通过反射动态加载。此前 runtime JAR 直接打包了 `flink-metrics-dropwizard` 及 `metrics-core`，但这些是 Flink 的可选模块，强制打包会导致依赖膨胀和潜在版本冲突。改造后，直方图指标通过反射动态加载 Dropwizard 类：存在则正常注册，不存在则跳过直方图（其他指标不受影响）。

## 如何达成设计目的

与原 PR #16155 完全一致的方案，应用于 Flink 1.20 和 2.0 两个版本：
1. 从 `flink/v1.20/build.gradle` 和 `flink/v2.0/build.gradle` 的 flink-runtime 模块移除 `implementation libs.flink*.metrics.dropwizard`。
2. 从对应版本的 LICENSE 和 runtime-deps.txt 中移除 Dropwizard Metrics 相关条目。
3. 重写 `IcebergStreamWriterMetrics`：使用 `DynClasses`/`DynConstructors` 反射加载 Dropwizard 类，加载失败时返回 null，直方图注册和使用时做 null 检查。
4. 新增测试验证无 Dropwizard 时的行为。

## 修改详情

以下文件在 `flink/v1.20/` 和 `flink/v2.0/` 两个目录下各有一份相同的改动。

### `build.gradle` (+0/-3 lines)

**修改目的**：从 flink-runtime 移除 dropwizard 依赖。

### `flink-runtime/LICENSE` (+0/-16 lines)

**修改目的**：移除 Dropwizard Metrics 和 Flink dropwizard 支持的 LICENSE 条目。

### `flink-runtime/runtime-deps.txt` (+0/-2 lines)

**修改目的**：从运行时依赖列表移除 `io.dropwizard.metrics:metrics-core` 和 `org.apache.flink:flink-metrics-dropwizard`。

### `flink/src/main/java/org/apache/iceberg/flink/sink/IcebergStreamWriterMetrics.java` (+89/-20 lines)

**修改目的**：改为反射动态加载 Dropwizard 直方图，支持可选依赖。

**工作逻辑**：
1. 移除对 `com.codahale.metrics.SlidingWindowReservoir`、`DropwizardHistogramWrapper` 的直接 import。
2. 新增静态字段 `DROPWIZARD`，通过 `loadDropwizardCtors()` 在类加载时反射加载三个构造器（reservoir、codahale histogram、wrapper），失败返回 null 并记录警告。
3. 新增 `DropwizardCtors` record 封装构造器。
4. 新增 `registerHistogram` 和 `newDropwizardHistogram` 方法：DROPWIZARD 为 null 时返回 null。
5. 构造函数中直方图字段可能为 null，`updateFlushResult` 中做 null 检查。

### `flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergStreamWriterMetrics.java` (+42 lines)

**修改目的**：新增测试验证无 Dropwizard 时的行为。

## 总结

这个提交是 PR #16155 的 backport，将 Flink 1.20 和 2.0 的 runtime JAR 中的可选依赖 `flink-metrics-dropwizard` 移除，改为运行时反射动态加载。这减少了 runtime JAR 的依赖膨胀和版本冲突风险，同时保持直方图指标在用户 classpath 包含该依赖时仍可用，缺失时优雅降级。至此该改进覆盖了 Flink 1.20、2.0、2.1 三个版本。
