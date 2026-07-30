# 提交 2375：Build: Bump org.xerial.snappy:snappy-java from 1.1.10.7 to 1.1.10.8 (#13601)

## 提交信息

- **序号**：2375 / 4088
- **哈希**：6bbbbf6f6f9ba6dc8cb273f8dcd00be1b5dfc399
- **短哈希**：6bbbbf6f6
- **日期**：2025-07-21 09:13:34 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.xerial.snappy:snappy-java from 1.1.10.7 to 1.1.10.8 (#13601)
- **PR/Issue**：#13601

## 总体目的

本提交是由 Dependabot 自动生成的依赖版本升级，将 snappy-java 压缩库从 1.1.10.7 升级到 1.1.10.8。Snappy 是一个快速的压缩/解压库，在 Iceberg 项目中被 Kafka Connect 模块使用，用于数据压缩处理。

此次升级为补丁版本（patch）升级，通常包含 bug 修复和安全补丁。值得注意的是，本次修改针对的是 Kafka Connect 模块的 build.gradle 文件中 `resolutionStrategy` 的 force 声明，用于强制覆盖传递依赖中的 snappy-java 版本，以确保使用安全版本。

## 如何达成设计目的

Dependabot 自动检测到 snappy-java 有新版本可用，通过修改 Kafka Connect 模块构建文件中的强制版本声明来完成升级。在 `resolutionStrategy` 中使用 force 声明是为了确保所有传递依赖也使用指定版本，避免因传递依赖引入存在已知漏洞的旧版本。

## 修改详情

### `kafka-connect/build.gradle` (+1/-1 lines)

**修改目的**：将 Kafka Connect 模块中强制指定的 snappy-java 版本从 1.1.10.7 升级到 1.1.10.8。

**工作逻辑**：在 `iceberg-kafka-connect-runtime` 子项目的 `resolutionStrategy` 块中，将 `force 'org.xerial.snappy:snappy-java:1.1.10.7'` 修改为 `force 'org.xerial.snappy:snappy-java:1.1.10.8'`。该 force 声明会覆盖所有传递依赖引入的 snappy-java 版本，确保使用最新安全版本。该块中还包含对 jettison、commons-compress、hadoop-shaded-guava、woodstox-core 等其他依赖的强制版本声明。

## 总结

本提交是一个常规的依赖版本升级，由 Dependabot 自动完成。仅修改 1 行配置，将 Kafka Connect 模块中强制指定的 snappy-java 版本从 1.1.10.7 升级到 1.1.10.8。该升级有助于获取最新的 bug 修复和安全补丁。
