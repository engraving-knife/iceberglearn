# 提交 0305：Build: Bump guava from 32.1.3-jre to 33.0.0-jre (#9373)

## 提交信息

- **序号**：0305 / 4088
- **哈希**：c83308c118370c079815dc155c3998b4df49c07c
- **短哈希**：c83308c11
- **日期**：2023-12-24 11:08:08 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump guava from 32.1.3-jre to 33.0.0-jre (#9373)
- **PR/Issue**：#9373

## 总体目的

本提交是 Dependabot 自动生成的依赖升级，把 Google Guava 从 32.1.3-jre 升到 33.0.0-jre。Guava 是 Iceberg 核心依赖之一，广泛用于集合、缓存、并发、字符串处理等基础工具。值得注意的是这是 **major 版本升级**（32.x → 33.x），Dependabot 标注为 `update-type: version-update:semver-major`，比前两个 patch 升级（0303、0304）风险更高。

与 0303/0304 不同，Guava 是 Iceberg 运行时的核心依赖，且 Iceberg 大量使用 Guava 的 `relocated` 版本（`org.apache.iceberg.relocated.com.google.common.*`），即 Iceberg 把 Guava 类做包重定位后内嵌使用，以避免与用户 classpath 上的 Guava 版本冲突。因此本升级影响范围覆盖整个 Iceberg 编译与运行时。33.0.0-jre 是 Guava 的一个重要主版本，通常包含新 API、bug 修复、潜在的废弃 API 移除以及对 JDK 基线的调整。Iceberg 维护者接受此次 major 升级，说明经评估后 Iceberg 使用的 Guava API 在 33.x 下兼容（Iceberg 用的是 relocated jar，可锁定内部版本），升级可获取上游修复与改进。

Dependabot 提交信息显示同时升级了 `com.google.guava:guava` 与 `com.google.guava:guava-testlib` 两个制品（均从 32.1.3-jre 到 33.0.0-jre），并附上游 release notes 与 commits 对比链接。

## 如何达成设计目的

设计上极简：直接修改 `gradle/libs.versions.toml` 中 `guava` 的版本声明，从 `32.1.3-jre` 改为 `33.0.0-jre`。Iceberg 用 Gradle Version Catalog（`libs.versions.toml`）集中管理依赖版本，所有模块通过 catalog 引用 `guava`，因此一处修改即可全局生效。Dependabot 自动比对 Maven Central 上的版本后生成此 PR，无人工代码改动。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 Guava 版本声明从 32.1.3-jre 提升到 33.0.0-jre，全局生效于所有引用 Guava 的模块。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle Version Catalog 文件，集中声明项目所有依赖版本。本次仅把第 43 行附近的 `guava = "32.1.3-jre"` 改为 `guava = "33.0.0-jre"`，其余版本（如 `google-libraries-bom = "26.28.0"`、`hadoop2`、`hadoop3-client`、`httpcomponents-httpclient5` 等）不变。由于 `guava` 与 `guava-testlib` 通常共享同一版本号（通过 `version.ref = "guava"` 引用），故这一行改动同时升级两个制品。各模块 build 脚本通过 `libs.guava` / `libs.guava.testlib` 引用，编译时自动解析为新版本。

## 小结

本提交是一次 major 版本依赖升级，把核心运行时依赖 Google Guava 从 32.1.3-jre 升到 33.0.0-jre（同时覆盖 guava-testlib）。通过 Gradle Version Catalog 一行改动全局生效。相比 0303/0304 的 patch 升级，本升级影响面更大（覆盖 Iceberg 编译与运行时，含 relocated Guava），属于 Iceberg 主版本依赖的关键维护，旨在获取 Guava 33.x 的修复与改进并保持依赖新鲜。
