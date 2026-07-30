# 提交 1738：Build: Bump io.netty:netty-buffer from 4.1.117.Final to 4.1.118.Final (#12287)

## 提交信息

- **序号**：1738 / 4088
- **哈希**：aed04d0fcbc27ca1b2ff2e6ea39d791ee81794ac
- **短哈希**：aed04d0fc
- **日期**：2025-02-17 09:35:43 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.117.Final to 4.1.118.Final (#12287)
- **PR/Issue**：#12287

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级 PR。Netty 是一个高性能的 Java 网络框架，`netty-buffer` 是其字节缓冲区模块，被 Iceberg 项目用于网络通信相关的功能。本次升级将 `netty-buffer` 从 4.1.117.Final 升级到 4.1.118.Final，属于补丁版本（semver-patch）升级，通常包含 bug 修复和安全补丁，不涉及 API 变更。

Dependabot 定期扫描项目依赖并自动创建版本升级 PR，帮助项目保持依赖的最新状态，及时获取安全修复和 bug 修复。

## 如何达成设计目的

提交修改了 Gradle 版本目录文件 `gradle/libs.versions.toml`，将 `netty-buffer` 的版本号从 `4.1.117.Final` 更新为 `4.1.118.Final`。这是一个单行修改，通过 Gradle 版本目录机制自动传播到所有引用该依赖的模块。

## 修改详情

### `gradle/libs.versions.toml`（修改, +1/-1 lines）

**修改目的**：升级 `netty-buffer` 依赖版本。

**工作逻辑**：将版本目录中 `netty-buffer = "4.1.117.Final"` 更新为 `netty-buffer = "4.1.118.Final"`。Gradle 版本目录（Version Catalog）是 Gradle 7.0+ 引入的依赖集中管理机制，所有模块通过引用 `libs.netty-buffer` 来使用该依赖，版本号的修改会自动传播到所有引用处。

## 小结

- **成效**：将 `netty-buffer` 依赖从 4.1.117.Final 升级到 4.1.118.Final，获取了最新的 bug 修复和补丁。
- **影响范围**：影响所有使用 `netty-buffer` 依赖的模块，但由于是补丁版本升级，不涉及 API 变更，对功能无影响。
- **回迁到 1.4.x 的注意事项**：此提交为依赖版本升级，回迁风险低。需确认 1.4.x 分支中使用的是相同版本的 netty-buffer 依赖。如果 1.4.x 已经使用了更高版本，则无需回迁。建议回迁以保持依赖版本一致。
