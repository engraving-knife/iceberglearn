# 提交 1975：Build: Bump io.delta:delta-spark_2.12 from 3.3.0 to 3.3.1 (#12729)

## 提交信息

- **序号**：1975 / 4088
- **哈希**：f39c1fa6f67a705a13dc2bc536f6002f38fc4cc7
- **短哈希**：f39c1fa6f
- **日期**：2025-04-08 15:25:22 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.delta:delta-spark_2.12 from 3.3.0 to 3.3.1 (#12729)
- **PR/Issue**：#12729

## 总体目的

本提交由 dependabot 自动生成，将项目依赖 `io.delta:delta-spark_2.12` 从 `3.3.0` 升级到 `3.3.1`（补丁版本升级）。这是一次常规的依赖版本维护，用于获取 Delta Lake 的最新补丁修复与改进。

## 如何达成设计目的

通过修改 Gradle 版本目录文件中 `delta-spark` 的版本声明完成升级。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 delta-spark 依赖版本。

**工作逻辑**：将 `delta-spark = "3.3.0"` 改为 `delta-spark = "3.3.1"`。同文件中 `delta-standalone` 此前已是 `3.3.1`，本次升级使两者版本一致。

## 总结

依赖升级提交，将 `io.delta:delta-spark_2.12` 由 3.3.0 升至 3.3.1（semver 补丁级），仅改动 `gradle/libs.versions.toml` 一行版本声明。
