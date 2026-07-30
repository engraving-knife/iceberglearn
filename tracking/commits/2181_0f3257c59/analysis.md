# 提交 2181：Build: Bump kafka from 3.9.0 to 3.9.1 (#13145)

## 提交信息

- **序号**：2181 / 4088
- **哈希**：0f3257c59bdf98e8cce89d430ce4f70336017134
- **短哈希**：0f3257c59
- **日期**：2025-05-29 19:34:08 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump kafka from 3.9.0 to 3.9.1 (#13145)
- **PR/Issue**：#13145

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交。目的是将 Kafka 相关依赖从 3.9.0 版本升级到 3.9.1 版本。此次升级涉及四个 Kafka 组件：kafka-clients、connect-api、connect-json 和 connect-transforms。Iceberg 的 Kafka 集成模块使用这些库进行 Kafka Connect 相关的功能开发。3.9.1 是一个补丁版本升级，主要包含 Bug 修复和小的改进，风险较低。

## 如何达成设计目的

- 通过 Dependabot 自动检测到 Kafka 有新的补丁版本发布
- 在 `gradle/libs.versions.toml` 中将 Kafka 版本号从 3.9.0 更新为 3.9.1
- 这是一个 semver-patch 级别的升级，向后兼容

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：更新 Kafka 依赖版本号。

**工作逻辑**：将 Kafka 的版本声明从 3.9.0 改为 3.9.1，所有依赖 Kafka 的模块（kafka-clients、connect-api、connect-json、connect-transforms）将统一使用新版本。

## 总结

这是一个常规的依赖升级提交，将 Kafka 相关依赖从 3.9.0 升级到 3.9.1，属于补丁版本升级，风险较低。该升级影响 Iceberg 的 Kafka Connect 集成功能。
