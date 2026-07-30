# 提交 3917：Build: Bump io.grpc:grpc-netty-shaded from 1.81.0 to 1.82.0 (#16902)

## 提交信息

- **序号**：3917 / 4088
- **哈希**：d0a05b0f8228ef2fdcf8e5caf6bf682e5ddad613
- **短哈希**：d0a05b0f8
- **日期**：2026-06-21 00:06:15 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.grpc:grpc-netty-shaded from 1.81.0 to 1.82.0 (#16902)
- **PR/Issue**：#16902

## 总体目的

这是由 Dependabot 发起的依赖升级，将 gRPC Java 的 Netty shaded 传输模块从 1.81.0 升级到 1.82.0。gRPC 是 Google 开源的高性能 RPC 框架，`grpc-netty-shaded` 提供了基于 Netty 的 shaded（重打包以避免依赖冲突）传输实现。Iceberg 的 Kafka Connect 运行时模块强制（force）该依赖版本，以确保与其他 gRPC 组件（如 gRPC core、protobuf 等子模块）的版本一致性。

此次升级为 semver-minor 更新，目的是获取上游 gRPC 框架的改进与错误修复，保持运行时依赖的现代化。

## 如何达成设计目的

通过修改 Kafka Connect 运行时模块的 `build.gradle` 中强制依赖声明，将版本号从 1.81.0 提升到 1.82.0。使用 `force` 关键字是为了在依赖解析过程中覆盖传递性依赖所引入的其它版本，保证最终打包到 Kafka Connect 运行时镜像中的 gRPC 版本统一。

## 修改详情

### `kafka-connect/build.gradle` (+1/-1 lines)

**修改目的**：升级 Kafka Connect 运行时中强制的 gRPC Netty shaded 版本。

**工作逻辑**：
在 `:iceberg-kafka-connect:iceberg-kafka-connect-runtime` 项目的依赖配置块中，将 `force 'io.grpc:grpc-netty-shaded:1.81.0'` 修改为 `force 'io.grpc:grpc-netty-shaded:1.82.0'`。这与同一批次中（#16907）的 runtime-deps.txt 文件中记录的运行时依赖版本保持一致。

## 总结

这是一次常规的 gRPC 传输依赖升级，确保 Kafka Connect 运行时分发物使用最新稳定的 gRPC 1.82.0 版本。通过 `force` 指令保证版本统一，避免因传递性依赖引入旧版本造成运行时不一致。
