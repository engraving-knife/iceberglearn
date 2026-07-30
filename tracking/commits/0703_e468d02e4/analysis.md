# 提交 0703：升级 Netty Buffer 至 4.1.109.Final

## 提交信息
- **序号**：0703 / 4088
- **哈希**：e468d02e4a0d8131c7beb75fcbd3863a568de0bc
- **短哈希**：e468d02e4
- **日期**：2024-04-21
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.108.Final to 4.1.109.Final (#10191)
- **PR/Issue**：#10191

## 总体目的

本提交由 Dependabot 自动生成，将 Netty 的 `netty-buffer` 模块（及其兼容版本 `netty-buffer-compat`）从 `4.1.108.Final` 升级到 `4.1.109.Final`，属于 patch 版本级别的依赖升级。

Netty 是一个高性能异步事件驱动的网络应用框架，`netty-buffer` 模块提供了 `ByteBuf` 这一零拷贝、可扩展的字节缓冲区实现。Iceberg 项目中 `netty-buffer` 主要被 AWS SDK 间接依赖（AWS SDK 的异步 HTTP 客户端基于 Netty 实现），用于支撑 S3、DynamoDB 等 AWS 服务的异步客户端通信。

Dependabot 例行升级目的在于获取上游 bug 修复、性能优化和潜在安全补丁。`4.1.108.Final` 到 `4.1.109.Final` 是单个 patch 版本升级，风险很低。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `netty-buffer` 和 `netty-buffer-compat` 两个版本属性。Gradle 版本目录机制会将新版本自动应用到所有引用这两个属性的依赖声明中。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：将 `netty-buffer` 和 `netty-buffer-compat` 版本从 `4.1.108.Final` 升级到 `4.1.109.Final`。
**工作逻辑**：
- `netty-buffer = "4.1.108.Final"` → `netty-buffer = "4.1.109.Final"`
- `netty-buffer-compat = "4.1.108.Final"` → `netty-buffer-compat = "4.1.109.Final"`

两个属性保持版本一致是惯例：`netty-buffer-compat` 是 Netty 为旧版 API 兼容性提供的模块，其版本通常与主版本同步发布。同步升级可避免两个模块间的版本不匹配导致运行时冲突。

## 小结
- **成效**：成功达成目的，完成 Netty Buffer 的 patch 版本升级。
- **影响范围**：间接影响所有使用 AWS SDK 异步客户端的模块（如 `aws`、`s3`、`kafka-connect` 等），但因是 patch 升级且 `ByteBuf` API 高度稳定，运行时行为变化极小。
- **回迁到 1.4.x 的注意事项**：可直接回迁。需注意 1.4.x 分支上 Netty 其他模块（如 `netty-handler`、`netty-codec` 等）的版本一致性，避免模块间版本错位。如果 1.4.x 分支整体锁定了某个 Netty 版本，建议统一升级。
