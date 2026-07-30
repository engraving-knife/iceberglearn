# 提交 4071：Build: Bump io.grpc:grpc-netty-shaded from 1.82.1 to 1.82.2 (#17292)

## 提交信息

- **序号**：4071 / 4088
- **哈希**：733e86c55998d5cf23472cfe99e47a609b86eac3
- **短哈希**：733e86c55
- **日期**：2026-07-18 22:27:55 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.grpc:grpc-netty-shaded from 1.82.1 to 1.82.2 (#17292)
- **PR/Issue**：#17292

## 总体目的

Dependabot 自动升级提交，将 `io.grpc:grpc-netty-shaded` 从 1.82.1 升级到 1.82.2（semver patch 补丁版本升级）。gRPC Netty Shaded 是 gRPC Java 的 Netty 传输层实现（ shaded 版本，避免依赖冲突），Iceberg 的 Kafka Connect 模块在构建脚本中通过 `force` 强制对齐该依赖版本。patch 版本升级包含 bug 修复，属于低风险维护升级。

## 如何达成设计目的

在 `kafka-connect/build.gradle` 中将 `force` 强制版本从 `1.82.1` 改为 `1.82.2`。

## 修改详情

### `kafka-connect/build.gradle` (+1/-1 lines)

**修改目的**：升级 grpc-netty-shaded 强制版本。

**工作逻辑**：
```groovy
force 'io.grpc:grpc-netty-shaded:1.82.2'
```
将强制版本从 `1.82.1` 改为 `1.82.2`。

## 总结

常规的 gRPC Netty 传输层补丁版本升级，通过 build.gradle 的 force 指令更新强制版本。patch 级别升级风险很低。
