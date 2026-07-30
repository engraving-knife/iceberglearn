# 提交 1198：Build: Bump io.delta:delta-spark_2.12 from 3.2.0 to 3.2.1 (#11225)

## 提交信息

- **序号**：1198 / 4088
- **哈希**：3ce09bc5f113c59e9c65db26185e95be56e63972
- **短哈希**：3ce09bc5f
- **日期**：2024-09-30（Mon Sep 30 15:02:17 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump io.delta:delta-spark_2.12 from 3.2.0 to 3.2.1 (#11225)
- **PR/Issue**：#11225

## 总体目的

`io.delta:delta-spark_2.12` 是 Delta Lake 与 Spark 集成的客户端库，Iceberg 的 Delta 迁移工具（如 `iceberg-delta` 扩展）在 Spark 环境下读取 Delta 表时会依赖该工件。本提交由 dependabot 触发，将 `delta-spark` 从 `3.2.0` 升级到 `3.2.1`，与提交 1196 升级 `delta-standalone` 配套，使 Delta 相关两个工件版本保持同步在 3.2.1。

## 如何达成设计目的

修改 `gradle/libs.versions.toml` 中 `delta-spark` 的版本声明，从 `3.2.0` 改为 `3.2.1`。Gradle 会将该版本应用到所有通过 `libs.delta.spark` 别名引用 `io.delta:delta-spark_2.12` 的模块。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Delta Spark 从 3.2.0 升级到 3.2.1。

**工作逻辑**：找到 `delta-spark = "3.2.0"` 这一行，改为：

```toml
delta-spark = "3.2.1"
```

此时 catalog 中 `delta-standalone = "3.2.1"`（由提交 1196 已升级）与 `delta-spark = "3.2.1"` 保持一致，确保 Delta 系列工件版本同步。其他依赖未改动。

## 小结

- **成效**：Delta Spark 升级到 3.2.1，与 `delta-standalone` 版本同步，获取 3.2.1 的修复，提升 Delta 表在 Spark 环境下的读取稳定性。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更；无源代码改动。
- **回迁到 1.4.x 的注意事项**：1.4.x 若包含 Delta 迁移模块，回迁此升级**风险低**。建议与 `delta-standalone` 升级（提交 1196）一并回迁，保持两个工件版本同步。需确认 1.4.x 的 catalog 别名一致，并跑一遍 Delta 相关集成测试。
