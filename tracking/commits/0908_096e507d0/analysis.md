# 提交 0908：Build: Bump kafka from 3.7.0 to 3.7.1 (#10653)

## 提交信息

- **序号**：0908 / 4088
- **哈希**：096e507d04985b3c59a16552c7ec1cece083d4c1
- **短哈希**：096e507d0
- **日期**：2024-07-07
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump kafka from 3.7.0 to 3.7.1 (#10653)
- **PR/Issue**：#10653

## 总体目的

Iceberg 在 Kafka connector 和相关测试中使用 Apache Kafka 客户端库（`kafka-clients`、`connect-api`、`connect-json`）。dependabot 定期检查 Kafka 的新版本并提交 PR 升级。本次将 Kafka 从 `3.7.0` 升级到 `3.7.1`，这是 Kafka 3.7 系列的第一个 patch 版本，包含 bug 修复和改进。Kafka 客户端库以直接依赖（`direct:production`）形式引入，升级有助于修复客户端已知问题。

## 如何达成设计目的

采用 Gradle version catalog 统一管理依赖版本。Iceberg 在 `gradle/libs.versions.toml` 中定义了 `kafka` 版本常量，通过该常量统一控制 `kafka-clients`、`connect-api`、`connect-json` 三个模块的版本。升级时只需修改 toml 文件中 `kafka` 这一行版本号，三个 Kafka 模块版本同步更新，保证它们版本一致（Kafka 客户端和 connect API 模块版本必须匹配，否则运行时可能出现兼容性问题）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Kafka 版本从 3.7.0 升级到 3.7.1。

**工作逻辑**：

```diff
-kafka = "3.7.0"
+kafka = "3.7.1"
```

该行位于 `[versions]` 段。Gradle 构建脚本中 `kafka-clients`、`connect-api`、`connect-json` 的 catalog alias 均引用 `kafka` 版本常量。Kafka 3.7.0 → 3.7.1 是 patch 版本升级，API 完全兼容，无需修改任何使用 Kafka API 的代码。该升级主要修复 3.7.0 中发现的客户端问题。

## 小结

- **成效**：将 Apache Kafka 客户端库从 3.7.0 升级到 3.7.1，引入 3.7 系列首个 patch 版本的 bug 修复。
- **影响范围**：1 个文件 `gradle/libs.versions.toml`，1 行改动，无代码逻辑变更。`kafka-clients`、`connect-api`、`connect-json` 三个模块同步升级。
- **回迁到 1.4.x 的注意事项**：可以回迁。Kafka patch 版本升级向后兼容，风险低。1.4.x 分支若使用 Kafka 3.7.0 建议升级到 3.7.1 以获取 bug 修复。需注意 1.4.x 分支的 Kafka 大版本（3.7.x）是否与此一致；若 1.4.x 使用不同大版本（如 3.6.x），则不能直接 cherry-pick 版本号，应升级到对应大版本的最新 patch。Kafka connect 相关测试需在升级后回归验证。
