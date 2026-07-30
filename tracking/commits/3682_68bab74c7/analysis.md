# 提交 3682：Build: Bump io.grpc:grpc-netty-shaded from 1.80.0 to 1.81.0 (#16277)

## 提交信息

- **序号**：3682 / 4088
- **哈希**：68bab74c771e560fe775cca62a90c1e74a2260b1
- **短哈希**：68bab74c7
- **日期**：2026-05-10 20:27:50 -0700
- **作者**：Huaxin Gao
- **提交说明**：Build: Bump io.grpc:grpc-netty-shaded from 1.80.0 to 1.81.0 (#16277)
- **PR/Issue**：#16277

## 总体目的

这个提交将 gRPC 的 Netty shaded 传输模块从 1.80.0 升级到 1.81.0。gRPC 是一个高性能的 RPC 框架，`grpc-netty-shaded` 是其基于 Netty 的传输层实现（shaded 版本，将 Netty 依赖重定位以避免版本冲突）。在 Iceberg 的 Kafka Connect 模块中，gRPC 用于与云服务进行通信（如 AWS S3 等服务的认证和通信）。

此次升级为次版本（minor version）升级，可能包含新功能、改进和 bug 修复。仅升级 `grpc-netty-shaded` 而非整个 gRPC BOM，说明这是针对特定依赖的版本调整，可能用于修复 CVE 或特定问题。

## 如何达成设计目的

通过修改两个文件来完成升级：
1. `kafka-connect/build.gradle` 中的 `force` 依赖版本约束
2. `kafka-connect/kafka-connect-runtime/runtime-deps.txt` 中的运行时依赖清单

## 修改详情

### `kafka-connect/build.gradle` (+1/-1 lines)

**修改目的**：升级 Kafka Connect 运行时模块的 gRPC 强制依赖版本。

**工作逻辑**：

```groovy
-        force 'io.grpc:grpc-netty-shaded:1.80.0'
+        force 'io.grpc:grpc-netty-shaded:1.81.0'
```

在 Kafka Connect 运行时模块的依赖配置中，使用 `force` 关键字强制指定 `grpc-netty-shaded` 的版本。这通常用于解决传递依赖冲突，确保所有 gRPC 组件使用统一的版本。

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+1/-1 lines)

**修改目的**：同步更新运行时依赖清单中的版本号。

**工作逻辑**：

```text
-io.grpc:grpc-netty-shaded:1.80.0
+io.grpc:grpc-netty-shaded:1.81.0
```

该文件列出了 Kafka Connect 运行时分发的所有依赖及其版本。注意清单中其他 gRPC 模块（如 `grpc-core`、`grpc-googleapis` 等）仍然保持在 1.80.0，仅 `grpc-netty-shaded` 单独升级到 1.81.0。这可能是因为只有该模块有特定的修复需求，或者是为了与上游版本约束保持一致。

## 总结

这是一个针对 Kafka Connect 模块的依赖维护提交，单独升级了 `grpc-netty-shaded` 模块的版本。这种针对性的版本升级通常是为了修复特定模块的 bug 或安全问题，同时最小化对其他 gRPC 组件的影响。
