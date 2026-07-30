# 提交 3596：Flink 2.1: Remove flink-metrics-dropwizard from runtime (#16093)

## 提交信息

- **序号**：3596 / 4088
- **哈希**：bac514ab4e72f0a75bf045c63a66009a0115022e
- **短哈希**：bac514ab4
- **日期**：2026-04-26 10:58:51 -0600
- **作者**：Ryan Blue
- **提交说明**：Flink 2.1: Remove flink-metrics-dropwizard from runtime (#16093)
- **PR/Issue**：#16093

## 总体目的

这个提交从 Flink 2.1 的运行时 jar 包中移除了 `flink-metrics-dropwizard` 依赖及其相关的 dropwizard metrics-core 依赖。

Iceberg 的 Flink runtime jar 是一个 fat jar（uber jar），用于将 Iceberg 与 Flink 集成时打包所有必要的依赖。之前这个 runtime jar 中包含了 `flink-metrics-dropwizard`，它是 Flink 提供的基于 Dropwizard Metrics 库的指标实现。然而，将指标实现打包到 runtime jar 中并不是必需的，因为指标系统的配置通常由 Flink 集群环境统一管理。

将这个依赖从 runtime jar 中移除可以减少 jar 包体积，避免与 Flink 集群环境中已有的指标库产生冲突（类加载冲突或版本冲突），同时让 runtime jar 更加聚焦于 Iceberg 自身所需的核心依赖。

## 如何达成设计目的

通过两个文件的修改实现目标：
1. 从 `build.gradle` 中移除 `flink-metrics-dropwizard` 的 `implementation` 依赖声明。
2. 同步更新 `runtime-deps.txt` 文件，移除对应的 `flink-metrics-dropwizard` 和 `metrics-core` 两条记录。

`runtime-deps.txt` 是 Iceberg 用于声明 runtime jar 中所包含依赖的清单文件，与 `build.gradle` 中的依赖声明需要保持一致。

## 修改详情

### `flink/v2.1/build.gradle` (+0/-3 lines)

**修改目的**：移除 runtime 项目对 flink-metrics-dropwizard 的依赖声明。

**工作逻辑**：
删除了以下三行代码：
```groovy
    // for dropwizard histogram metrics implementation
    implementation libs.flink21.metrics.dropwizard
```
这是之前用于引入 dropwizard 直方图指标实现的依赖。移除后，runtime jar 不再包含该库。

### `flink/v2.1/flink-runtime/runtime-deps.txt` (+0/-2 lines)

**修改目的**：同步移除 runtime 依赖清单中的相关条目。

**工作逻辑**：
移除了两行：
- `io.dropwizard.metrics:metrics-core:3.2.6` - dropwizard metrics 核心库
- `org.apache.flink:flink-metrics-dropwizard:2.1.0` - Flink 的 dropwizard 指标实现

`runtime-deps.txt` 用于记录 runtime jar 实际打包的第三方依赖列表，需要与 build.gradle 中的依赖保持同步。

## 总结

这个提交是一个依赖清理工作，从 Flink 2.1 runtime jar 中移除了不必要的指标库依赖。这有助于减小 jar 包体积，避免与 Flink 集群环境的指标配置产生冲突，使 runtime jar 更专注于 Iceberg 集成所需的核心依赖。这类清理对于维护一个干净、最小化的发布产物具有重要意义。
