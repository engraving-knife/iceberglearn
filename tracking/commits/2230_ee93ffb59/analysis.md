# 提交 2230：Build: Bump io.netty:netty-buffer from 4.2.1.Final to 4.2.2.Final

## 提交信息

- **序号**：2230 / 4088
- **哈希**：ee93ffb591a7a1e64179e2de38f1edf899b34d52
- **短哈希**：ee93ffb59
- **日期**：2025-06-11 20:09:06 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.2.1.Final to 4.2.2.Final
- **PR/Issue**：#13273

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Netty 的 `netty-buffer` 模块从 4.2.1.Final 升级到 4.2.2.Final。Netty 是一个异步事件驱动的网络应用框架，`netty-buffer` 提供了字节缓冲区（ByteBuf）的实现，被 Iceberg 用于网络 IO 相关的底层操作。此次升级为 patch 级别（semver-patch）的版本更新，通常包含 bug 修复和小的改进，不涉及破坏性变更。定期升级依赖有助于获取最新的安全修复和稳定性改进。

## 如何达成设计目的

- 在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `netty-buffer` 的版本号从 `4.2.1.Final` 修改为 `4.2.2.Final`。
- 这是 Dependabot 自动检测到的新版本，通过修改版本声明即可让所有引用该依赖的模块自动使用新版本。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 netty-buffer 依赖版本至 4.2.2.Final。

**工作逻辑**：该文件是 Gradle 的版本目录（Version Catalog），集中声明项目所有依赖的版本号。修改 `netty-buffer = "4.2.2.Final"` 后，所有通过 `libs.netty.buffer` 引用该依赖的模块在构建时会自动拉取新版本。

## 总结

这是一个常规的依赖升级提交，由 Dependabot 自动完成，将 netty-buffer 从 4.2.1.Final 升级到 4.2.2.Final，获取最新的 patch 级别修复和改进。
