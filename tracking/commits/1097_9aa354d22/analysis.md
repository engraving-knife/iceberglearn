# 提交 1097：Build: Bump org.apache.commons:commons-compress from 1.27.0 to 1.27.1 (#11005)

## 提交信息

- **序号**：1097 / 4088
- **哈希**：9aa354d2224eb78c19c773227693df117954312b
- **短哈希**：9aa354d22
- **日期**：2024-08-25 14:00:52 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.apache.commons:commons-compress from 1.27.0 to 1.27.1 (#11005)
- **PR/Issue**：#11005

## 总体目的

本提交由 Dependabot 自动生成，将 `org.apache.commons:commons-compress` 从 1.27.0 升级到 1.27.1。commons-compress 是 Apache Commons 提供的压缩/解压缩库（支持 zip、gzip、bzip2 等格式），在 Iceberg 的 kafka-connect runtime 中作为传递依赖被引入，并通过 `resolutionStrategy.force` 强制锁定版本以解决依赖冲突。

这是一次 patch 级别升级，通常包含 bug 修复。本次升级通过 `force` 策略将 kafka-connect runtime 解析依赖时强制的 commons-compress 版本提升到 1.27.1，确保使用最新的修复版本。

## 如何达成设计目的

修改 `kafka-connect/build.gradle` 中 `iceberg-kafka-connect-runtime` 项目的 `resolutionStrategy.force` 配置，将 `org.apache.commons:commons-compress` 的强制版本从 1.27.0 改为 1.27.1。`force` 策略会在依赖解析时覆盖所有传递依赖引入的 commons-compress 版本，统一使用指定版本，避免不同传递依赖引入不一致版本导致的冲突。

## 修改详情

### `kafka-connect/build.gradle`

**修改目的**：升级 kafka-connect runtime 强制的 commons-compress 版本。

**工作逻辑**：在 `project(':iceberg-kafka-connect:iceberg-kafka-connect-runtime')` 的 `resolutionStrategy` 块中，将 `force 'org.apache.commons:commons-compress:1.27.0'` 改为 `force 'org.apache.commons:commons-compress:1.27.1'`。该块同时强制了 jettison 1.5.4、snappy-java 1.1.10.6、hadoop-shaded-guava 1.2.0 等版本，用于统一 kafka-connect runtime 的传递依赖版本。

## 小结

- **成效**：将 kafka-connect runtime 强制的 commons-compress 版本从 1.27.0 升级到 1.27.1，获得最新 patch 修复。
- **影响范围**：仅修改 `kafka-connect/build.gradle` 一行，影响 `iceberg-kafka-connect-runtime` 项目的依赖解析。
- **回迁到 1.4.x 的注意事项**：属于依赖 patch 升级，向后兼容，**可安全回迁到 1.4.x**（若 1.4.x 包含 kafka-connect 模块）。需确认 1.4.x 的 commons-compress 基线版本是否在 1.27.x 系列；若 1.4.x 使用更早版本，应升级到对应分支的最新 patch 而非强制对齐 1.27.1。
