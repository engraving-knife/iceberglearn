# 提交 1196：Build: Bump io.delta:delta-standalone_2.12 from 3.2.0 to 3.2.1 (#11228)

## 提交信息

- **序号**：1196 / 4088
- **哈希**：152f02de523a3868edfe8d2fbe64229225629051
- **短哈希**：152f02de5
- **日期**：2024-09-30（Mon Sep 30 12:47:12 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump io.delta:delta-standalone_2.12 from 3.2.0 to 3.2.1 (#11228)
- **PR/Issue**：#11228

## 总体目的

Delta Lake 是 Databricks 主导的开源表格式，Iceberg 在迁移工具（如 `iceberg-delta` 扩展）中会读取 Delta 的日志与元数据，因此依赖 `io.delta:delta-standalone_2.12`。本提交由 dependabot 触发，将 `delta-standalone` 从 `3.2.0` 升级到 `3.2.1`，引入 Delta 3.2.1 的补丁修复，保证迁移工具在读取 Delta 表时使用最新稳定版本。

## 如何达成设计目的

修改 `gradle/libs.versions.toml` 中 `delta-standalone` 的版本声明，从 `3.2.0` 改为 `3.2.1`。Gradle 会将该版本应用到所有通过 `libs.delta.standalone` 别名引用 `io.delta:delta-standalone_2.12` 的模块。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Delta Standalone 从 3.2.0 升级到 3.2.1。

**工作逻辑**：找到 `delta-standalone = "3.2.0"` 这一行，改为：

```toml
delta-standalone = "3.2.1"
```

该声明仅作用于 `io.delta:delta-standalone_2.12` 工件。同目录下的 `delta-spark = "3.2.0"`（提交 1196 时仍是 3.2.0，由提交 1198 单独升级）保持不变。

## 小结

- **成效**：Delta Standalone 升级到 3.2.1，获取 3.2.0 到 3.2.1 间的修复，提升 Delta 表读取的稳定性与兼容性。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更；无源代码改动。
- **回迁到 1.4.x 的注意事项**：1.4.x 若包含 Delta 迁移模块（`iceberg-delta` 等），回迁此升级**风险低**。Delta 3.2.x 系列向后兼容。需确认 1.4.x 的 catalog 中 `delta-standalone` 别名存在且 main 一致；建议跑一遍 Delta 迁移相关测试。
