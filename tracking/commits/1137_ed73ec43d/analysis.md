# 提交 1137：Build: Bump io.netty:netty-buffer from 4.1.112.Final to 4.1.113.Final (#11097)

## 提交信息

- **序号**：1137 / 4088
- **哈希**：ed73ec43dd25c9033069ea1d3381a6d9229be53a
- **短哈希**：ed73ec43d
- **日期**：2024-09-09（Mon Sep 9 10:44:56 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.112.Final to 4.1.113.Final (#11097)
- **PR/Issue**：#11097

## 总体目的

Iceberg 在 `gradle/libs.versions.toml` 中维护 Netty 的 `netty-buffer` 模块版本，并同时维护一个 `netty-buffer-compat`（兼容版）别名。Netty 4.1.x 是广泛使用的网络/缓冲库，4.1.112 到 4.1.113 是一个 patch 版本升级，包含 bug 修复与改进。

本提交由 dependabot 自动发起，把 `netty-buffer` 与 `netty-buffer-compat` 两个版本别名从 `4.1.112.Final` 同步升到 `4.1.113.Final`，保持两者版本一致，获取该 patch 版本的修复。属于常规依赖滚动升级。

## 如何达成设计目的

修改 `gradle/libs.versions.toml` 中两个版本变量，把：

```toml
netty-buffer = "4.1.112.Final"
netty-buffer-compat = "4.1.112.Final"
```

改为：

```toml
netty-buffer = "4.1.113.Final"
netty-buffer-compat = "4.1.113.Final"
```

两个别名同步升级，确保引用 `netty-buffer` 或 `netty-buffer-compat` 的模块使用一致的 patch 版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Netty buffer 模块版本。

**工作逻辑**：在依赖版本目录中，把 `netty-buffer` 与 `netty-buffer-compat` 两行版本号从 `4.1.112.Final` 改为 `4.1.113.Final`。这两个别名分别指向 `io.netty:netty-buffer` 主版本与兼容版本，下游模块按需引用其一。同 minor 线内 patch 升级，向后兼容。共 2 行增、2 行删（即两行替换）。

## 小结

- **成效**：Netty buffer 模块（主版与兼容版）同步升级到 4.1.113.Final，获取该 patch 版本的 bug 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件、两行版本号变更，无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是依赖 patch 升级，风险较低。**可选回迁**——若 1.4.x 仍在维护并发布，可回迁以保持依赖补丁同步；否则可不动。Netty buffer 主要作为传递依赖出现（如 AWS SDK、S3FileIO 的异步 HTTP 客户端依赖 Netty），回迁会影响运行时传递依赖版本，建议做基本的 S3 读写回归。注意保持 `netty-buffer` 与 `netty-buffer-compat` 版本一致。
