# 提交 3096：Build: Bump io.grpc:grpc-netty-shaded from 1.76.2 to 1.78.0 (#15024)

## 提交信息

- **序号**：3096 / 4088
- **哈希**：7fd8a2ddc94e32b665bc954a0b409705af36527b
- **短哈希**：7fd8a2ddc
- **日期**：2026-01-10
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.grpc:grpc-netty-shaded from 1.76.2 to 1.78.0 (#15024)
- **PR/Issue**：#15024

## 总体目的

该提交由 Dependabot 自动生成，将 `io.grpc:grpc-netty-shaded` 从 1.76.2 升级到 1.78.0。`grpc-netty-shaded` 是 gRPC Java 官方提供的 Netty 传输实现，使用 shaded（重打包）形式的 Netty，避免与运行环境中已有的 Netty 版本产生冲突。在 Iceberg 项目中，该依赖并非顶层 `gradle/libs.versions.toml` 中统一声明的版本，而是出现在 `iceberg-kafka-connect-runtime` 子模块的 `resolutionStrategy.force` 配置中。

`kafka-connect-runtime` 模块用于打包可独立运行的 Kafka Connect runtime 分发包。由于 Kafka Connect 生态会引入大量第三方传递依赖（Hive、Hadoop 等），这些依赖往往携带各自版本的 Netty、gRPC 等库，存在已知安全漏洞或版本不一致风险。该模块的 `resolutionStrategy.force` 列出了一批被强制锁定到安全版本的依赖（如 `jettison`、`snappy-java`、`commons-compress`、`woodstox-core`、`commons-beanutils` 以及 `grpc-netty-shaded`），用于在打包 runtime 分发物时统一规避已知 CVE。本次升级即把该强制锁定列表中的 grpc-netty-shaded 版本同步提升到上游新版本。

版本号从 1.76.2 跨越到 1.78.0，属于语义版本中的 minor 级别升级（`version-update:semver-minor`，跨了两个 minor 版本）。根据提交元数据，该依赖被归类为 `direct:production`。minor 升级通常包含新特性、改进和 bug 修复；对 shaded Netty 传输而言，主要价值在于跟进上游安全修复与依赖的 Netty 版本更新，预期对 Iceberg 自身代码无行为影响——因为 Iceberg 不直接调用 gRPC API，只是通过传递依赖把它带入 runtime 包，升级是为消除潜在安全风险。

## 如何达成设计目的

直接修改 `kafka-connect/build.gradle` 中 `iceberg-kafka-connect-runtime` 子模块 `resolutionStrategy.force` 列表里 `io.grpc:grpc-netty-shaded` 的版本号，从 `1.76.2` 改为 `1.78.0`。这是 Dependabot 针对 Gradle force 声明的标准升级方式。

## 修改详情

### `kafka-connect/build.gradle` (+1/-1 lines)

**修改目的**：升级 kafka-connect runtime 强制锁定的 grpc-netty-shaded 版本。

**工作逻辑**：
将第 79 行 `force 'io.grpc:grpc-netty-shaded:1.76.2'` 修改为 `force 'io.grpc:grpc-netty-shaded:1.78.0'`。该 force 语句位于 `project(':iceberg-kafka-connect:iceberg-kafka-connect-runtime')` 的 `configurations.all { resolutionStrategy { ... } }` 块内，作用是对 runtime 分发物中所有传递依赖解析出的 `io.grpc:grpc-netty-shaded` 强制使用指定版本，覆盖其他模块（Hive、Hadoop 等）带来的旧版本，从而消除已知漏洞。同一 force 列表中还锁定了 `jettison:1.5.4`、`snappy-java:1.1.10.8`、`commons-compress:1.28.0` 等其它安全相关版本，本次只动 grpc 一行。

## 总结

该提交是 Dependabot 自动生成的依赖升级，将 `iceberg-kafka-connect-runtime` 分发包中强制锁定的 `grpc-netty-shaded` 从 1.76.2 提升到 1.78.0（minor 级别）。该依赖仅用于 runtime 分发的依赖收敛与安全规避，Iceberg 代码不直接调用 gRPC，因此升级对项目逻辑无影响，主要价值是跟进上游安全修复与 Netty 版本更新。
