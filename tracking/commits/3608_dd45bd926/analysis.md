# 提交 3608：Flink: Bundle flink-metrics-dropwizard in runtime jar (#16126)

## 提交信息

- **序号**：3608 / 4088
- **哈希**：dd45bd926bed9bfdca28aa1221d05d39e3dcfd59
- **短哈希**：dd45bd926
- **日期**：2026-04-28 09:06:08 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Bundle flink-metrics-dropwizard in runtime jar (#16126)
- **PR/Issue**：#16126

## 总体目的

这个提交恢复了之前在提交 3596（#16093）中从 Flink 2.1 runtime jar 移除的 `flink-metrics-dropwizard` 依赖。

Iceberg 使用 Dropwizard Metrics 库来实现 Histogram（直方图）指标，用于报告数据文件大小分布等统计信息。Flink 默认不包含 `flink-metrics-dropwizard` 这个可选依赖。提交 3596 将其从 runtime jar 中移除后，导致使用 runtime jar 的用户无法使用 Histogram 指标功能。

这个提交承认了之前的错误：移除该依赖会破坏 Histogram 指标功能，因此需要将其加回到 runtime jar 中。

## 如何达成设计目的

1. 在 `flink/v2.1/build.gradle` 的 runtime 项目中重新添加 `flink-metrics-dropwizard` 的 `implementation` 依赖。
2. 同步更新 `runtime-deps.txt` 添加对应的依赖条目。
3. 更新 LICENSE 文件，修正 "Codahale Metrics" 为 "Dropwizard Metrics"，并添加 Flink Dropwizard Metrics 支持的条目。
4. 在文档中说明 Histogram 指标需要 `flink-metrics-dropwizard` 依赖。

## 修改详情

### `flink/v2.1/build.gradle` (+4/-1 lines)

**修改目的**：在 runtime 项目中重新添加 flink-metrics-dropwizard 依赖。

**工作逻辑**：
1. 在 `iceberg-flink` 主项目中，将注释从 "for dropwizard histogram metrics implementation" 改为 "dropwizard histogram metrics (optional in Flink)"，保持为 `compileOnly`。
2. 在 `iceberg-flink-runtime` 项目中，添加：
```groovy
// To support dropwizard histogram metrics (not shipped by Flink by default)
implementation libs.flink21.metrics.dropwizard
```

### `flink/v2.1/flink-runtime/runtime-deps.txt` (+2/-0 lines)

**修改目的**：同步添加依赖清单条目。

**工作逻辑**：
添加回两条依赖：
- `io.dropwizard.metrics:metrics-core:3.2.6`
- `org.apache.flink:flink-metrics-dropwizard:2.1.0`

### `flink/v2.1/flink-runtime/LICENSE` (+9/-1 lines)

**修改目的**：更新 LICENSE 文件。

**工作逻辑**：
1. 将 "Codahale Metrics" 修正为 "Dropwizard Metrics"。
2. 新增 Flink Dropwizard Metrics 支持的条目：
```
This product bundles Apache Flink's optional support for Dropwizard Metrics.
Copyright: 2014-2026 The Apache Software Foundation
Project URL: https://flink.apache.org/
License: Apache License, Version 2.0
```

### `docs/docs/flink-writes.md` (+4/-0 lines)

**修改目的**：文档说明 Histogram 指标的依赖需求。

**工作逻辑**：
在 Histogram 指标说明后添加：
```
The `Histogram` metrics above require `flink-metrics-dropwizard` on the classpath, which is not shipped
by Flink by default. When using `iceberg-flink-runtime`, this dependency is already bundled. When using
the `iceberg-flink` artifact directly, add `org.apache.flink:flink-metrics-dropwizard` as a dependency.
```

## 总结

这个提交是提交 3596 的回退，恢复了 Flink 2.1 runtime jar 中的 `flink-metrics-dropwizard` 依赖。这是因为 Iceberg 的 Histogram 指标功能依赖该库，而 Flink 默认不包含它，移除会导致指标功能失效。同时更新了 LICENSE 和文档，使说明更加清晰。这提醒了在移除依赖时需要充分考虑功能影响。
