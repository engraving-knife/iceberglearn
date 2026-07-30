# 提交 1197：Build: Bump junit-platform from 1.10.3 to 1.11.1 (#11227)

## 提交信息

- **序号**：1197 / 4088
- **哈希**：d00c4938adb43bb153f599334069c00fd1301d6a
- **短哈希**：d00c4938a
- **日期**：2024-09-30（Mon Sep 30 12:47:29 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump junit-platform from 1.10.3 to 1.11.1 (#11227)
- **PR/Issue**：#11227

## 总体目的

JUnit Platform 是 JUnit 5 的基础平台层，提供测试引擎 SPI、Suite API 等。Iceberg 在测试中通过 `org.junit.platform:junit-platform-suite-api` 与 `org.junit.platform:junit-platform-suite-engine` 来组织测试套件（如 `@Suite` 注解）。本提交由 dependabot 触发，将 `junit-platform` 从 `1.10.3` 升级到 `1.11.1`，这是一个 minor 版本升级（1.10 → 1.11），引入新特性并修复缺陷。

dependabot 同时升级 `junit-platform-suite-api` 与 `junit-platform-suite-engine` 两个工件（共享同一版本号）。

## 如何达成设计目的

修改 `gradle/libs.versions.toml` 中 `junit-platform` 的版本声明，从 `1.10.3` 改为 `1.11.1`。Gradle 会将该版本应用到所有引用 `libs.junit.platform.suite.api` 与 `libs.junit.platform.suite.engine` 别名的测试模块。注意 `junit = "5.10.1"`（JUnit Jupiter）保持不变，因为 Jupiter 与 Platform 版本号不同步。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 JUnit Platform 从 1.10.3 升级到 1.11.1。

**工作逻辑**：找到 `junit-platform = "1.10.3"` 这一行，改为：

```toml
junit-platform = "1.11.1"
```

该声明同时作用于 `org.junit.platform:junit-platform-suite-api` 与 `org.junit.platform:junit-platform-suite-engine`。其他依赖（`junit = "5.10.1"`、`kafka`、`kryo-shaded` 等）均未改动。

## 小结

- **成效**：JUnit Platform 升级到 1.11.1，获取 1.11.x 系列的新特性与修复；为测试套件机制提供更新的平台支持。
- **影响范围**：仅 `gradle/libs.versions.toml` 一行版本号变更；无源代码改动。属于测试基础设施升级，不影响发布产物。
- **回迁到 1.4.x 的注意事项**：1.4.x 若回迁此升级需**谨慎测试**。JUnit Platform 1.11 是 minor 升级，可能引入新的默认行为或弃用旧 API。需确认 1.4.x 的测试代码（特别是 `@Suite`、`@SelectPackages` 等套件注解）在 1.11.1 下仍能正常运行。同时注意 `junit = "5.10.1"` 与 Platform 1.11.1 的兼容性（JUnit 5.10.x 通常与 Platform 1.10.x 配对，1.11.x 更适合 JUnit 5.11.x；若 1.4.x 仍用 JUnit 5.10.1，建议同时升级或保守起见不回迁）。
