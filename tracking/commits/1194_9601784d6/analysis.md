# 提交 1194：Build: Bump guava from 33.3.0-jre to 33.3.1-jre (#11230)

## 提交信息

- **序号**：1194 / 4088
- **哈希**：9601784d6141d5f46817cb2bc3d44c3e9a6bbec5
- **短哈希**：9601784d6
- **日期**：2024-09-30（Mon Sep 30 12:46:29 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump guava from 33.3.0-jre to 33.3.1-jre (#11230)
- **PR/Issue**：#11230

## 总体目的

Iceberg 通过 dependabot 自动管理依赖版本。本提交将 Google Guava 从 `33.3.0-jre` 升级到 `33.3.1-jre`，这是一个补丁版本（patch）升级，目的是引入 Guava 33.3.1-jre 中修复的缺陷与安全补丁，保持依赖处于最新稳定状态。Guava 是 Iceberg 核心模块广泛使用的工具库，因此即使补丁升级也需及时跟进。

dependabot 同时管理 `guava` 与 `guava-testlib` 两个工件（共享同一版本号），本次升级一并覆盖。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 中 `guava` 版本声明，从 `33.3.0-jre` 改为 `33.3.1-jre`。Gradle 的 version catalog 机制会自动将该版本应用到所有引用 `guava` 与 `guava-testlib` 的模块（通过 `libs.guava`、`libs.guava.testlib` 等别名引用），无需逐模块修改 build.gradle。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Guava 版本从 33.3.0-jre 升级到 33.3.1-jre。

**工作逻辑**：在 version catalog 中找到 `guava = "33.3.0-jre"` 这一行，改为：

```toml
guava = "33.3.1-jre"
```

该声明同时作用于 `com.google.guava:guava` 与 `com.google.guava:guava-testlib` 两个工件（在 catalog 的 `[libraries]` 段中以 `module = { group = "com.google.guava", name = "guava", version.ref = "guava" }` 形式引用）。其他依赖（`google-libraries-bom`、`hadoop2`、`hadoop3` 等）均未改动。

## 小结

- **成效**：Guava 升级到 33.3.1-jre，获取最新补丁修复；通过 version catalog 一处修改即覆盖所有模块。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更，新增 1 行、删除 1 行；无源代码改动。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支如需获取 Guava 33.3.1-jre 的修复，可直接回迁此版本号改动，**风险低**。需确认 1.4.x 的 Gradle 配置同样使用 version catalog 且 `guava` 别名与 main 一致；同时建议跑一次完整构建与测试以确认 Guava 33.3.1-jre 与 1.4.x 其他依赖（特别是 Hadoop、Flink、Spark 等）兼容。
