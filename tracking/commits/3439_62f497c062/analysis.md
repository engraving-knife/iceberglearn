# 提交 3439：Build: Bump io.grpc:grpc-netty-shaded from 1.79.0 to 1.80.0 (#15723)

## 提交信息

- **序号**：3439 / 4088
- **哈希**：62f497c062d0dedf64193b187b8583b2ba6a3e49
- **短哈希**：62f497c062
- **日期**：2026-03-21 23:43:03 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.grpc:grpc-netty-shaded from 1.79.0 to 1.80.0 (#15723)
- **PR/Issue**：#15723

## 总体目的

这是一个由 Dependabot 自动生成的依赖版本升级提交。将 gRPC Java 的 `grpc-netty-shaded` 模块从 1.79.0 升级到 1.80.0，这是一个次版本（minor）升级，可能包含新功能和改进。

`grpc-netty-shaded` 是 gRPC Java 的 Netty 传输层实现，使用 shaded（重打包）版本的 Netty 以避免与项目中其他 Netty 依赖产生版本冲突。在 Iceberg 项目中，该依赖被 kafka-connect 模块使用。

## 如何达成设计目的

- Dependabot 自动检测到 grpc-netty-shaded 有新版本发布
- 在 kafka-connect 模块的 build.gradle 中更新版本号
- 通过 CI 测试验证升级兼容性

## 修改详情

### `kafka-connect/build.gradle` (+1/-1 lines)

**修改目的**：升级 grpc-netty-shaded 依赖版本号。

**工作逻辑**：
- 将 `io.grpc:grpc-netty-shaded` 的版本号从 `1.79.0` 修改为 `1.80.0`
- 这是 kafka-connect 模块构建配置中唯一变更的内容

## 总结

这是 Dependabot 自动生成的依赖升级提交，将 kafka-connect 模块使用的 gRPC Netty 传输层库从 1.79.0 升级到 1.80.0，属于次版本升级，可能包含新功能和改进。
