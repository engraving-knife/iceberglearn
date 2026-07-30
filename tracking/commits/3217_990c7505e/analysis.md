# 提交 3217：Build: Bump io.grpc:grpc-netty-shaded from 1.78.0 to 1.79.0 (#15262)

## 提交信息

- **序号**：3217 / 4088
- **哈希**：990c7505ef49c3b90f08c1964ef864199759edd6
- **短哈希**：990c7505e
- **日期**：2026-02-07
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.grpc:grpc-netty-shaded from 1.78.0 to 1.79.0 (#15262)
- **PR/Issue**：#15262

## 总体目的

这是一次 dependabot 发起的依赖版本升级。`io.grpc:grpc-netty-shaded` 是 gRPC Java 客户端使用的、内嵌（shaded/重定位）了 Netty 网络栈的传输实现，提供基于 Netty 的 gRPC 通道而不与项目其它 Netty 依赖产生版本冲突。在 Iceberg 中，该依赖出现在 `kafka-connect` 运行时模块（`iceberg-kafka-connect-runtime`）的依赖强制（force）配置里，用于固定 Kafka Connect 运行时打包时使用的 gRPC Netty 传输版本，确保运行时类路径上 gRPC 的网络传输实现版本一致、可预测。

本次升级将其从 `1.78.0` 提升到 `1.79.0`，属于 `semver-minor`（次版本号）升级。按 gRPC Java 的版本约定，1.x 线上的 minor 升级会带来新特性、缺陷修复与依赖更新，但保持 API 兼容，通常不引入破坏性变更。升级动机是跟进上游修复与改进、保持依赖新鲜度，避免长期滞后累积兼容性风险。

## 如何达成设计目的

作为 dependabot 自动化升级，整体思路是在依赖声明处把版本号字符串从 `1.78.0` 改为 `1.79.0`。本次改动落在 `kafka-connect/build.gradle` 中 `iceberg-kafka-connect-runtime` 子项目的依赖 `force` 块内，是单点版本号替换，不涉及代码或配置逻辑变更。

## 修改详情

### `kafka-connect/build.gradle` (+1/-1 lines)

**修改目的**：将 Kafka Connect 运行时强制锁定的 `grpc-netty-shaded` 版本从 1.78.0 升到 1.79.0。

**工作逻辑**：
在 `project(':iceberg-kafka-connect:iceberg-kafka-connect-runtime')` 的依赖管理闭包中，存在一组 `force '...'` 声明，用于把若干传递依赖（如 `hadoop-shaded-guava`、`woodstox-core`、`commons-beanutils`、`grpc-netty-shaded`）统一钉到指定版本，避免 Kafka Connect 运行时打包时因各传递依赖版本不一致而产生冲突。本次仅将其中一行由 `force 'io.grpc:grpc-netty-shaded:1.78.0'` 改为 `force 'io.grpc:grpc-netty-shaded:1.79.0'`。该 force 仅作用于 kafka-connect 运行时打包的依赖解析，不影响其它模块；minor 版本升级在 gRPC 1.x 线上保持二进制兼容，预期对 Iceberg 自身代码无影响，主要收益是获得 1.79.0 自带的修复与改进。

## 总结

本提交由 dependabot 将 Kafka Connect 运行时模块强制锁定的 `io.grpc:grpc-netty-shaded` 从 1.78.0 升级到 1.79.0（次版本号升级，API 兼容），用于跟进 gRPC Netty 传输实现的上游修复与改进，保持依赖新鲜；改动为 `kafka-connect/build.gradle` 中单点版本号替换，不影响其它模块或运行时行为。
