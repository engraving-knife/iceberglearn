# 提交 2240：Build: Bump io.delta:delta-spark_2.12 from 3.3.1 to 3.3.2

## 提交信息

- **序号**：2240 / 4088
- **哈希**：d6b52149de1d3344a23a811a361b1e50060658f6
- **短哈希**：d6b52149d
- **日期**：2025-06-16 09:25:36 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.delta:delta-spark_2.12 from 3.3.1 to 3.3.2
- **PR/Issue**：#13205

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Delta Lake 的 Spark 集成模块 `delta-spark_2.12` 从 3.3.1 升级到 3.3.2。Iceberg 项目在测试中使用 Delta Lake 进行表格式迁移和兼容性测试。此次升级为 patch 级别更新，包含 bug 修复和改进。

## 如何达成设计目的

- 在 Gradle 版本目录文件中修改 delta-spark 的版本号。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 delta-spark 依赖版本。

**工作逻辑**：将 `delta-spark = "3.3.1"` 修改为 `delta-spark = "3.3.2"`，所有引用该依赖的 Spark 模块将自动使用新版本。

## 总结

常规依赖升级提交，将 delta-spark_2.12 从 3.3.1 升级到 3.3.2，获取最新的 bug 修复和改进。
