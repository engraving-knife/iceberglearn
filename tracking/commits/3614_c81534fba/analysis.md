# 提交 3614：Flink: Backport: Bundle flink-metrics-dropwizard in runtime jar (#16141)

## 提交信息

- **序号**：3614 / 4088
- **哈希**：c81534fba6a521f70dd1c054c4f688622c04c09d
- **短哈希**：c81534fba
- **日期**：2026-04-29 13:52:42 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport: Bundle flink-metrics-dropwizard in runtime jar (#16141)
- **PR/Issue**：#16141

## 总体目的

这个提交是将提交 3608（Flink: Bundle flink-metrics-dropwizard in runtime jar #16126）的修改反向移植到 Flink 1.20 和 Flink 2.0 版本。

提交 3608 在 Flink 2.1 中恢复了 `flink-metrics-dropwizard` 依赖到 runtime jar，以确保 Histogram 指标功能正常工作。本提交将相同的修改同步到 Flink 1.20 和 2.0 版本，确保所有 Flink 版本的 runtime jar 都包含该依赖。

注意：Flink 1.20 和 2.0 的 runtime jar 之前并没有移除该依赖（提交 3596 只移除了 Flink 2.1 的），所以这个 backport 主要是更新注释和 LICENSE 文件以保持一致性。

## 如何达成设计目的

更新 Flink 1.20 和 2.0 的 build.gradle 注释和 LICENSE 文件，使其与 Flink 2.1 保持一致。

## 修改详情

### `flink/v1.20/build.gradle` (+2/-2 lines)

**修改目的**：更新注释以与 Flink 2.1 保持一致。

**工作逻辑**：
1. 在 `iceberg-flink` 主项目中，将注释从 "for dropwizard histogram metrics implementation" 改为 "dropwizard histogram metrics (optional in Flink)"。
2. 在 `iceberg-flink-runtime` 项目中，将注释从 "for dropwizard histogram metrics implementation" 改为 "To support dropwizard histogram metrics (not shipped by Flink by default)"。

### `flink/v1.20/flink-runtime/LICENSE` (+9/-1 lines)

**修改目的**：更新 LICENSE 文件以与 Flink 2.1 保持一致。

**工作逻辑**：
1. 将 "Codahale Metrics" 修正为 "Dropwizard Metrics"。
2. 新增 Apache Flink's optional support for Dropwizard Metrics 的条目。

### `flink/v2.0/build.gradle` (+2/-2 lines)

**修改目的**：与 Flink 1.20 相同的注释更新。

### `flink/v2.0/flink-runtime/LICENSE` (+9/-1 lines)

**修改目的**：与 Flink 1.20 相同的 LICENSE 更新。

## 总结

这个提交是提交 3608 的 backport，将 Flink 2.1 中恢复 flink-metrics-dropwizard 的相关修改同步到 Flink 1.20 和 2.0 版本。主要变更的是注释和 LICENSE 文件的一致性更新，确保所有 Flink 版本的文档和许可证信息保持统一。
