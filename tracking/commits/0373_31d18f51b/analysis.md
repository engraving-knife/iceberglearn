# 提交 0373：Flink: Upgrade Flink version from 1.18 to 1.18.1

## 提交信息

- **序号**：0373
- **哈希**：31d18f51b9e8590f7ca316463b080bd1153e8f9e
- **短哈希**：31d18f51b
- **日期**：2024-01-16（作者日期与提交日期均为 Tue Jan 16 12:51:50 2024 -0800）
- **作者**：Rodrigo <rmenesespinillos@apple.com>
- **提交说明**：Flink: Upgrade Flink version from 1.18 to 1.18.1 (#9486)
- **PR/Issue**：PR #9486

## 总体目的

这是一个版本升级提交，把 Iceberg 的 Flink 1.18 模块所依赖的 Flink 版本从 1.18.0 升到 1.18.1。Flink 1.18.1 是 1.18 主版本线上的第一个维护版本（bugfix release），按 Flink 社区的版本策略，1.18.x 系列在 API 和二进制兼容性上保持稳定，主要包含对 1.18.0 中发现的 bug 修复、安全补丁和小的稳定性改进。Iceberg 作为一个表格式库，其 Flink 集成模块需要跟随上游 Flink 的维护版本升级，以便用户在使用 Iceberg+Flink 组合时能享受到 Flink 最新的 bug 修复，避免遇到已知的 1.18.0 问题。

之所以这种升级很重要，是因为 Iceberg 通过 `gradle/libs.versions.toml` 中的 `flink118` 版本约束（`strictly = "[1.18, 1.19["`）锁定 Flink 版本范围，并把 `prefer` 值作为默认解析版本。当用户在自己的项目中引入 iceberg-flink 依赖时，Gradle 的依赖解析会受 `prefer` 影响，倾向于使用 1.18.0；升级 `prefer` 到 1.18.1 后，新用户和重新解析依赖的存量用户都会自动获得 1.18.1。同时由于 `strictly` 范围保持不变（仍是 `[1.18, 1.19[`），用户如果硬性要求 1.18.0 仍然可以解析到，但默认推荐的是 1.18.1，这是兼容性友好的升级方式。

## 如何达成设计目的

实现路径极简，只动两个文件共两行：(1) 在 `gradle/libs.versions.toml` 中把 `flink118` 的 `prefer` 值从 `1.18.0` 改为 `1.18.1`，`strictly` 范围保持 `[1.18, 1.19[` 不变；(2) 在 `TestFlinkPackage` 测试中把断言的期望版本字符串从 `"1.18.0"` 改为 `"1.18.1"`，确保 `FlinkPackage.version()` 在运行时返回的版本号与新依赖一致。这两处配合保证依赖升级和运行时版本检测同步。

## 修改详情

### gradle/libs.versions.toml

**修改目的**：把 Flink 1.18 模块默认解析的 Flink 版本从 1.18.0 升到 1.18.1。

**工作逻辑**：`libs.versions.toml` 是 Gradle 版本目录（version catalog）的声明文件，集中管理所有依赖版本。`flink118` 这一行定义了 Flink 1.18 模块的版本约束，使用 Gradle 的 rich version 语法：`strictly = "[1.18, 1.19["` 表示只接受 1.18.x 系列（左闭右开，排除 1.19），`prefer = "1.18.1"` 表示在满足 strictly 范围内优先选择 1.18.1。本次提交只改 `prefer`，不动 `strictly`，这意味着：(a) 新构建会解析到 1.18.1；(b) 仍允许用户在下游强制要求 1.18.0（在 strictly 范围内），保持向后兼容。这种升级方式是社区标准做法，风险低、影响可控。

### flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/util/TestFlinkPackage.java

**修改目的**：让 `FlinkPackage.version()` 的单元测试与新依赖版本保持同步。

**工作逻辑**：`TestFlinkPackage.testVersion()` 是一个简单断言测试，验证 `FlinkPackage.version()` 返回的字符串等于预期版本。该方法上的注释明确说 "This unit test would need to be adjusted as new Flink version is supported."，表明这是一个守卫测试，每次升级 Flink 版本时都要同步修改断言。把 `"1.18.0"` 改为 `"1.18.1"` 后，如果 `FlinkPackage` 内部对版本的检测逻辑与实际依赖版本不匹配，测试会立即失败，从而在 CI 阶段就暴露版本不一致问题，而不是等到运行时被用户发现。

## 小结

这是一个典型的依赖维护版本升级提交，模式简单但必不可少：动版本目录 + 同步守卫测试。值得注意的是 Iceberg 通过 `strictly + prefer` 的 rich version 约束实现"软升级"——既推进默认版本到最新 bugfix，又不破坏需要旧 bugfix 版本的用户。Flink 1.18.1 作为维护版本，主要包含 bug 修复而非新特性，因此升级风险低、收益直接体现在稳定性上。这种小幅但高频的依赖升级是开源项目维持与上游生态同步、保障用户能用到最新修复的关键工程实践。
