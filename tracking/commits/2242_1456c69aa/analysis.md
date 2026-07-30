# 提交 2242：Build: Bump io.delta:delta-standalone_2.12 from 3.3.1 to 3.3.2

## 提交信息

- **序号**：2242 / 4088
- **哈希**：1456c69aa17fedc26717d67c1dc64c5888b8c986
- **短哈希**：1456c69aa
- **日期**：2025-06-16 10:35:02 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.delta:delta-standalone_2.12 from 3.3.1 to 3.3.2
- **PR/Issue**：#13202

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Delta Lake 的独立模块 `delta-standalone_2.12` 从 3.3.1 升级到 3.3.2。与 delta-spark 不同，delta-standalone 提供不依赖 Spark 运行时的 Delta Lake 读写能力，Iceberg 在测试中使用该模块进行表格式转换和兼容性测试。此次升级为 patch 级别更新，包含 bug 修复。

## 如何达成设计目的

- 在 Gradle 版本目录文件中修改 delta-standalone 的版本号。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 delta-standalone 依赖版本。

**工作逻辑**：将 `delta-standalone = "3.3.1"` 修改为 `delta-standalone = "3.3.2"`。

## 总结

常规依赖升级提交，将 delta-standalone_2.12 从 3.3.1 升级到 3.3.2，与 delta-spark 升级保持同步，获取最新的 bug 修复。
