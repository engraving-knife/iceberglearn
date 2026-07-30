# 提交 1981：Build: Bump io.netty:netty-buffer from 4.1.119.Final to 4.2.0.Final (#12730)

## 提交信息

- **序号**：1981 / 4088
- **哈希**：55b5371e9feb04973335b569e4f24b15c4f30570
- **短哈希**：55b5371e9
- **日期**：2025-04-10 17:50:54 +0200
- **作者**：dependabot[bot]（Fokko 协同）
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.119.Final to 4.2.0.Final (#12730)
- **PR/Issue**：#12730

## 总体目的

本提交由 dependabot 自动生成，将直接生产依赖 `io.netty:netty-buffer` 从 `4.1.119.Final` 升级到 `4.2.0.Final`（semver 次版本升级）。Netty 4.2 是一个新的次版本线，带来改进与潜在的行为变化。由于 Iceberg 的 aws-bundle / azure-bundle / kafka-connect-runtime 等模块会打包 netty 相关组件，本次升级同步更新了这些模块的 LICENSE / NOTICE 文件中记录的 netty 版本号，以保持合规性。

## 如何达成设计目的

1. 修改 Gradle 版本目录中 `netty-buffer` 的版本声明。
2. 由 Fokko 协同更新各 bundle/runtime 模块的 LICENSE、NOTICE 文件中所有 netty 组件（netty-codec、netty-codec-http、netty-codec-http2、netty-common、netty-buffer、netty-handler、netty-resolver、netty-transport 等）的版本号引用，从 `4.1.119.Final` 更新为 `4.2.0.Final`。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 netty-buffer 版本。

**工作逻辑**：`netty-buffer = "4.1.119.Final"` → `netty-buffer = "4.2.0.Final"`。

### `aws-bundle/LICENSE`, `aws-bundle/NOTICE` (修改)

**修改目的**：同步 bundle 内打包的 netty 组件版本声明。**工作逻辑**：将各 netty 组件版本号由 4.1.119.Final 改为 4.2.0.Final。

### `azure-bundle/LICENSE`, `azure-bundle/NOTICE` (修改)

**修改目的**：同上，同步 azure bundle 的 netty 版本声明。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE`, `kafka-connect/kafka-connect-runtime/main/LICENSE` (修改)

**修改目的**：同上，同步 kafka-connect runtime 的 netty 版本声明。

## 总结

依赖升级提交，将 `io.netty:netty-buffer` 由 4.1.119.Final 升至 4.2.0.Final（次版本升级），并同步更新 aws-bundle、azure-bundle、kafka-connect-runtime 的 LICENSE/NOTICE 中所有 netty 组件版本号以维持许可证合规。共 7 个文件、88 行变更（多为版本字符串替换）。
