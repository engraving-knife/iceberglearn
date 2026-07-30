# 提交 1224：Build: Bump io.netty:netty-buffer from 4.1.113.Final to 4.1.114.Final (#11269)

## 提交信息

- **序号**：1224 / 4088
- **哈希**：d93677a3f31b89a18a16568a214ddf5ce1c8f372
- **短哈希**：d93677a3f
- **日期**：2024-10-12（Sat Oct 12 21:09:18 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.113.Final to 4.1.114.Final (#11269)
- **PR/Issue**：#11269

## 总体目的

这是由 Dependabot 自动生成的依赖版本升级提交。Netty 是一个高性能的异步事件驱动网络框架，`netty-buffer` 是其字节缓冲区模块，提供 `ByteBuf` 等核心数据结构。Iceberg 在部分模块（如 S3/HDFS 等存储后端的网络通信）中通过传递依赖使用 Netty。

本次将 `netty-buffer` 从 4.1.113.Final 升级到 4.1.114.Final，属于补丁版本（semver-patch）升级。值得注意的是，Iceberg 同时维护了两个 Netty buffer 版本变量：`netty-buffer`（主版本）和 `netty-buffer-compat`（兼容版本），本次将两者同步升级。

## 如何达成设计目的

直接修改 `gradle/libs.versions.toml` 中 `netty-buffer` 和 `netty-buffer-compat` 两个版本声明，同步从 `4.1.113.Final` 改为 `4.1.114.Final`。通过 Gradle 版本目录的 `version.ref` 机制，所有引用这两个版本号的库会自动同步升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：同步升级 netty-buffer 和 netty-buffer-compat 版本号。

**工作逻辑**：将第 71-72 行的版本声明从：

```toml
netty-buffer = "4.1.113.Final"
netty-buffer-compat = "4.1.113.Final"
```

改为：

```toml
netty-buffer = "4.1.114.Final"
netty-buffer-compat = "4.1.114.Final"
```

这两个版本变量分别被以下库条目引用：
- `io.netty:netty-buffer`（version.ref = `netty-buffer`）
- `io.netty:netty-buffer`（version.ref = `netty-buffer-compat`）

`netty-buffer` 是 Netty 5.x 风格的 buffer 模块，`netty-buffer-compat` 是 Netty 4.x 兼容版的 buffer 模块。两者同步升级可保证一致性，避免不同版本混用导致的 `ByteBuf` 兼容性问题。Netty 的 4.1.x 系列在补丁版本中通常修复内存泄漏、缓冲区分配和 GC 相关问题，升级风险较低。

## 小结

- **成效**：netty-buffer 和 netty-buffer-compat 同步从 4.1.113.Final 升级到 4.1.114.Final，获取补丁版本的 bug 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，2 行改动，无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支当前使用的 netty-buffer 版本较低（1.4.x 的 `libs.versions.toml` 中为 4.1.97.Final），且 `netty-buffer-compat` 版本更低（4.1.68.Final），两个版本不一致。回迁时需注意：1.4.x 的两个 netty buffer 版本本就不同步，若要回迁此提交，建议同时同步两个变量到 4.1.114.Final。但考虑到 1.4.x 与 main 之间 Netty 版本跨度较大，且 Netty 是网络通信底层库，升级可能影响 S3 等存储后端的连接行为，建议在回迁前充分运行集成测试验证。
