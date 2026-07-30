# 提交 3981：Build: Bump io.grpc:grpc-netty-shaded from 1.82.0 to 1.82.1 (#17103)

## 提交信息

- **序号**：3981 / 4088
- **哈希**：f459f653559f16c8dd0a6187df64c3f858a3f1db
- **短哈希**：f459f6535
- **日期**：2026-07-05 00:29:41 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.grpc:grpc-netty-shaded from 1.82.0 to 1.82.1 (#17103)
- **PR/Issue**：#17103

## 总体目的

Dependabot 自动升级 gRPC Netty shaded 库从 1.82.0 到 1.82.1，补丁版本升级。该库在 Kafka Connect 模块中被强制引入。gRPC 是 Google 的 RPC 框架，netty-shaded 是其网络传输层的 shaded 版本。

## 如何达成设计目的

修改 `kafka-connect/build.gradle` 中的 `resolutionStrategy.force` 配置。

## 修改详情

### `kafka-connect/build.gradle` (+1/-1 lines)

**修改目的**：升级 grpc-netty-shaded 强制版本。

**工作逻辑**：
```gradle
force 'io.grpc:grpc-netty-shaded:1.82.1'  # 原为 1.82.0
```

## 总结

常规依赖升级，将 gRPC netty-shaded 从 1.82.0 升级到 1.82.1，仅影响 Kafka Connect 模块。
