# 提交 0958：Build: Bump io.netty:netty-buffer from 4.1.111.Final to 4.1.112.Final (#10726)

## 提交信息

- **序号**：0958 / 4088
- **哈希**：8eb75db09002611d1f45e2fb769c974baf2fa1c1
- **短哈希**：8eb75db09
- **日期**：2024-07-22（Mon Jul 22 09:18:40 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.111.Final to 4.1.112.Final (#10726)
- **PR/Issue**：#10726

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交。Netty 是 Java 生态中广泛使用的异步事件驱动网络应用框架，`netty-buffer` 模块提供 Netty 的字节缓冲区（ByteBuf）实现。Iceberg 在 S3FileIO 等 I/O 路径中（通过 AWS SDK 的异步 HTTP 客户端，该客户端底层依赖 Netty）间接或直接使用 Netty 进行网络通信与缓冲区管理。

本次提交将 `io.netty:netty-buffer`（及其兼容版本 `netty-buffer-compat`）从 4.1.111.Final 升级到 4.1.112.Final（semver patch 版本升级），属于 patch 级别的小版本升级，通常包含 bug 修复、安全补丁和性能改进。Netty 的 patch 升级经常包含安全修复，因此及时跟进对降低安全风险有积极意义。

## 如何达成设计目的

实现方式为修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将其中 `netty-buffer` 与 `netty-buffer-compat` 两个版本声明同步从 `4.1.111.Final` 改为 `4.1.112.Final`，保持两者版本一致。所有引用这两个版本号的模块在构建时自动同步升级。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 netty-buffer 及其兼容版本 netty-buffer-compat 从 4.1.111.Final 同步升级到 4.1.112.Final。

**工作逻辑**：修改版本目录中的两行：

```diff
-netty-buffer = "4.1.111.Final"
-netty-buffer-compat = "4.1.111.Final"
+netty-buffer = "4.1.112.Final"
+netty-buffer-compat = "4.1.112.Final"
```

- `netty-buffer`：标准版 ByteBuf 实现，供 AWS SDK 异步 HTTP 客户端等使用；
- `netty-buffer-compat`：兼容版（提供与旧版 `io.netty.buffer` 包兼容的 API），通常用于兼容仍依赖旧包路径的下游库。

两个版本必须保持一致，避免在同一构建中引入不一致的 Netty 缓冲区实现导致类加载冲突。

## 小结

- **成效**：完成 netty-buffer 与 netty-buffer-compat 从 4.1.111.Final 到 4.1.112.Final 的同步 patch 版本升级，使网络 I/O 缓冲区依赖保持最新修复版本，可能包含安全补丁。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 一个文件，2 行改动。影响所有使用 Netty 的模块（如 `iceberg-aws` 的 S3FileIO 网络 I/O 路径）的构建产物依赖版本，但不改变 Iceberg 自身代码逻辑。
- **回迁到 1.4.x 的注意事项**：纯依赖升级，**适合回迁**，风险较低。Netty 4.1.x 系列内 patch 升级通常向后兼容。回迁到 1.4.x 建议运行 `iceberg-aws` 模块的 S3 集成测试以验证网络 I/O 路径无回归。需特别注意 1.4.x 分支上是否有其他模块因传递依赖引入不同版本的 netty-buffer，避免版本冲突。
