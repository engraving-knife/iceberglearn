# 提交 2630：Build: Bump io.netty:netty-buffer from 4.2.5.Final to 4.2.6.Final (#14075)

## 提交信息

- **序号**：2630 / 4088
- **哈希**：f084616eddb40404db6bafc9c700a9c00bffccd4
- **短哈希**：f084616ed
- **日期**：2025-09-14 18:43:24 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.2.5.Final to 4.2.6.Final (#14075)
- **PR/Issue**：#14075

## 总体目的

这是由 dependabot 自动生成的依赖升级提交，目的是将 Netty 的 netty-buffer 模块从 4.2.5.Final 版本升级到 4.2.6.Final 版本。

Netty 是一个异步事件驱动的网络应用框架，Iceberg 项目在处理网络 IO 时（例如与远端存储、REST catalog 等交互）会使用到 netty-buffer 模块提供的字节缓冲区功能。保持依赖处于最新版本有助于获取上游的 bug 修复、性能优化和安全补丁。

本次升级属于 semver-patch（补丁版本）级别升级，按照语义化版本约定，补丁版本升级应保持向后兼容，主要包含 bug 修复和小的改进，理论上不会引入破坏性变更。

## 如何达成设计目的

通过修改 Gradle 的版本目录文件 `gradle/libs.versions.toml`，将其中 netty-buffer 的版本声明从 `4.2.5.Final` 改为 `4.2.6.Final`。Iceberg 使用 Gradle 的版本目录（Version Catalog）机制集中管理依赖版本，所有引用 netty-buffer 的模块会自动获取到新版本，无需逐个模块修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 netty-buffer 依赖版本声明。

**工作逻辑**：将 `netty-buffer = "4.2.5.Final"` 修改为 `netty-buffer = "4.2.6.Final"`。该文件是 Gradle 版本目录，集中定义项目所有依赖的版本号，模块通过别名引用这些版本，因此只需在此处修改一处即可全局生效。

## 总结

这是一次常规的依赖补丁版本升级，通过 dependabot 自动完成。升级 netty-buffer 到 4.2.6.Final 可获取上游的 bug 修复与安全改进，风险较低，对 Iceberg 现有功能无影响。
