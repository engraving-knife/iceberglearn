# 提交 1389：Build: Bump io.netty:netty-buffer from 4.1.114.Final to 4.1.115.Final (#11569)

## 提交信息

- **序号**：1389 / 4088
- **哈希**：fc11dc456e87e6dcec77d6de9579cf85a6f8fa4c
- **短哈希**：fc11dc456
- **日期**：2024-11-17（Sun Nov 17 08:29:56 2024 +0100）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.114.Final to 4.1.115.Final
- **PR/Issue**：#11569

## 总体目的

Netty 是 Java 生态中广泛使用的高性能网络框架，`io.netty:netty-buffer` 提供 `ByteBuf` 等零拷贝字节缓冲区实现。Iceberg 仓库在 `gradle/libs.versions.toml` 中维护了两个 Netty 版本变量：`netty-buffer`（标准版）和 `netty-buffer-compat`（兼容版，提供与旧版 `ByteBuffer` 的互操作），用于支持依赖 Netty 的模块（如通过 AWS SDK、Azure SDK 间接使用 Netty 的场景）。

本提交是 Dependabot 发起的补丁版本升级（4.1.114.Final → 4.1.115.Final），同时升级标准版和兼容版。目的是引入 Netty 4.1.x 系列的 bug 修复与安全补丁。属于日常依赖维护，无 API 破坏性变更。

## 如何达成设计目的

Dependabot 自动检测到 `gradle/libs.versions.toml` 中 `netty-buffer` 与 `netty-buffer-compat` 均为 `4.1.114.Final`，且上游发布了 `4.1.115.Final`，遂创建 PR 同时升级两个变量。这是 `version-update:semver-patch` 类型升级，保持标准版与兼容版版本号同步。

## 修改详情

### `gradle/libs.versions.toml`（修改，2 行）

**修改目的**：同步升级 Netty buffer 标准版与兼容版。

**工作逻辑**：

```diff
-netty-buffer = "4.1.114.Final"
-netty-buffer-compat = "4.1.114.Final"
+netty-buffer = "4.1.115.Final"
+netty-buffer-compat = "4.1.115.Final"
```

两个变量在版本目录中紧邻声明。`netty-buffer` 用于直接依赖 Netty 标准缓冲区的场景；`netty-buffer-compat` 用于需要与 Netty 3.x `ChannelBuffer` 兼容的场景（部分第三方库仍依赖兼容包）。两者保持相同版本号可避免运行时出现不同版本的 Netty 缓冲区实现共存导致的类加载冲突。4.1.115.Final 是 Netty 4.1.x 稳定线的补丁版本，含 bug 修复与安全补丁。

## 小结

- **成效**：将 Netty buffer 标准版与兼容版从 4.1.114.Final 升级到 4.1.115.Final，引入上游补丁修复，属纯依赖维护。
- **影响范围**：仅 `gradle/libs.versions.toml` 两行变更，影响间接依赖 Netty 的模块。4.1.x 补丁版本向后兼容，不涉及 API 变更。
- **回迁到 1.4.x 的注意事项**：可直接 cherry-pick。需确认 1.4.x 分支的 `libs.versions.toml` 中 Netty 版本当前值；若已有其他 Dependabot 升级覆盖，按版本号较大者保留。Netty 补丁升级通常安全，但建议回迁后运行依赖 Netty 的模块测试验证。
