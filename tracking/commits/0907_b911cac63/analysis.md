# 提交 0907：Build: Bump jetty from 9.4.54.v20240208 to 9.4.55.v20240627 (#10654)

## 提交信息

- **序号**：0907 / 4088
- **哈希**：b911cac63149d47ce1feec5c315de4b8457dffbb
- **短哈希**：b911cac63
- **日期**：2024-07-07
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jetty from 9.4.54.v20240208 to 9.4.55.v20240627 (#10654)
- **PR/Issue**：#10654

## 总体目的

Iceberg 在测试和 REST Catalog 服务端实现中使用 Eclipse Jetty（`jetty-server`、`jetty-servlet`）作为嵌入式 HTTP 服务器。dependabot 定期检查 Jetty 的新版本并提交 PR 升级。本次将 Jetty 9.4.x 系列从 `9.4.54.v20240208` 升级到 `9.4.55.v20240627`，引入 2024 年 2 月到 6 月期间 Jetty 9.4 维护分支的 bug 修复和安全补丁。Jetty 9.4.x 是 Jetty 9 的最后一个维护系列，保持其最新有助于修复已知的 HTTP 协议处理和安全问题。

由于 Jetty 通常以直接依赖（`direct:production`）形式用于测试和 REST 服务，本次升级属于常规安全维护。

## 如何达成设计目的

采用 Gradle version catalog 统一管理依赖版本。Iceberg 在 `gradle/libs.versions.toml` 中定义了 `jetty` 版本常量，通过该常量统一控制 `jetty-server` 和 `jetty-servlet` 两个模块的版本。升级时只需修改 toml 文件中 `jetty` 这一行版本号，两个 Jetty 模块版本同步更新，保证它们版本一致（Jetty 各模块版本必须匹配，否则运行时可能出现兼容性问题）。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Jetty 版本从 9.4.54.v20240208 升级到 9.4.55.v20240627。

**工作逻辑**：

```diff
-jetty = "9.4.54.v20240208"
+jetty = "9.4.55.v20240627"
```

该行位于 `[versions]` 段。Gradle 构建脚本通过 `jetty-server` 和 `jetty-servlet` 的 catalog alias 引用，其版本均绑定到 `jetty` 常量。Jetty 版本号采用 `主版本.次版本.v日期` 的命名约定，`v20240627` 表示该构建发布于 2024 年 6 月 27 日。由于仍在 9.4.x 大版本内，API 完全兼容，无需修改任何使用 Jetty API 的代码。

## 小结

- **成效**：将 Jetty 9.4.x 从 9.4.54.v20240208 升级到 9.4.55.v20240627，引入约 4 个月内的 bug 修复和安全补丁。
- **影响范围**：1 个文件 `gradle/libs.versions.toml`，1 行改动，无代码逻辑变更。`jetty-server` 和 `jetty-servlet` 两个模块同步升级。
- **回迁到 1.4.x 的注意事项**：建议回迁，尤其因为 Jetty 升级常含安全补丁。1.4.x 分支使用的 Jetty 版本若仍为 9.4.54 或更早，建议升级到 9.4.55 或更新版本以获取安全修复。Jetty 9.4.x 内 API 完全兼容，回迁风险极低。需注意 1.4.x 是否仍使用 Jetty 9.4.x（而非已迁移到 Jetty 11/12），若版本系列不同则不能直接 cherry-pick 版本号。
