# 提交 1951：Build: Bump guava from 33.4.0-jre to 33.4.6-jre (#12686)

## 提交信息

- **序号**：1951 / 4088
- **哈希**：be2c1207581e91f458d5dd3b53610ef18a506dc4
- **短哈希**：be2c12075
- **日期**：2025-04-02 08:14:23 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump guava from 33.4.0-jre to 33.4.6-jre (#12686)
- **PR/Issue**：#12686

## 总体目的

本提交是 Dependabot 自动生成的依赖升级，将 Google Guava 从 `33.4.0-jre` 升级到 `33.4.6-jre`。Guava 是 Iceberg 广泛使用的基础库（包括 relocated guava），本次为同一小版本线内的 patch 升级，通常包含 bug 修复和小的改进。

## 如何达成设计目的

通过修改 Gradle 版本目录 `gradle/libs.versions.toml` 中的 guava 版本声明来完成升级。由于 Guava 被打包进 gcp-bundle 以及 kafka-connect 运行时 jar，相关模块的 LICENSE 文件中引用的 Guava 版本号也需同步更新，保持许可证信息与实际依赖一致。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 guava 版本声明。

**工作逻辑**：将 guava 的版本从 `33.4.0-jre` 改为 `33.4.6-jre`。

### `gcp-bundle/LICENSE` (修改, +1/-1 lines)

**修改目的**：同步 gcp-bundle 的 LICENSE 中 guava 版本号。

**工作逻辑**：将许可证文件中记录的 guava 版本由 33.4.0-jre 更新为 33.4.6-jre。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE` (修改, +1/-1 lines)

**修改目的**：同步 hive 运行时 LICENSE 中 guava 版本号。

**工作逻辑**：将许可证文件中记录的 guava 版本由 33.4.0-jre 更新为 33.4.6-jre。

### `kafka-connect/kafka-connect-runtime/main/LICENSE` (修改, +1/-1 lines)

**修改目的**：同步 main 运行时 LICENSE 中 guava 版本号。

**工作逻辑**：将许可证文件中记录的 guava 版本由 33.4.0-jre 更新为 33.4.6-jre。

## 总结

本提交是 Dependabot 发起的依赖升级，将 Google Guava 从 `33.4.0-jre` 升级到 `33.4.6-jre`，并同步更新了 gradle 版本目录与 gcp-bundle、kafka-connect 两个运行时模块的 LICENSE 文件中的版本声明。
