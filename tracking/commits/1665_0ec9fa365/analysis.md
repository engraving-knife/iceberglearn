# 提交 1665：Azure: Fix NOTICE and LICENSE in the azure-bundle (#12143)

## 提交信息

- **序号**：1665 / 4088
- **哈希**：0ec9fa3653cbe6f465d83d0f39ca5b86c353f235
- **短哈希**：0ec9fa365
- **日期**：2025-01-31（Sat Feb 1 02:16:39 2025 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Azure: Fix NOTICE and LICENSE in the azure-bundle (#12143)
- **PR/Issue**：#12143

## 总体目的

`azure-bundle` 是 Iceberg 为了方便用户在 Azure 环境下使用而打出的"胖包"（uber jar）分发模块，它把 Iceberg 自身代码与若干 Azure SDK 及其传递依赖打包到一起。Apache 项目对外发布的二进制制品必须满足许可证合规要求：在 `LICENSE` 中列出所有包含的第三方代码、版本、许可证；在 `NOTICE` 中复制所有要求"重分发时必须保留 NOTICE 文本"的第三方 NOTICE 内容。

随着之前的提交升级了 azure-bundle 内的 Azure SDK、Netty、Jackson 等依赖版本，以及移除了一些不再被打包的依赖，`LICENSE` 与 `NOTICE` 文件已与实际打包内容脱节：

1. `LICENSE` 中许多依赖版本号停留在旧版本（如 `azure-core-http-netty 1.13.5`、`netty-* 4.1.94.Final`、`jackson-* 2.13.5` 等），与新打包的版本不符；
2. `LICENSE` 中仍列着已不再打包的依赖（`jackson-dataformat-xml`、`woodstox-core`、`stax2-api`）；
3. `NOTICE` 文件此前几乎为空——只包含 Jackson 的简短 NOTICE，而 Netty 等组件的 NOTICE 文本（要求重分发时必须保留）被遗漏，存在合规风险。

本提交补齐这两份文件，使 azure-bundle 的许可证声明与实际打包内容一致并完整。

## 如何达成设计目的

- **LICENSE 文件**：逐条核对每个第三方组件的版本号，更新为当前实际打包的版本；删除不再打包的 `jackson-dataformat-xml`、`woodstox-core`、`stax2-api` 三个条目。其余条目的格式（Group/Name/Version/Project URL/License）保持不变。
- **NOTICE 文件**：
  - 把 Jackson 的 NOTICE 文本改为"管道符前缀"格式（每行加 `| `），以便在合并多份 NOTICE 时清晰分隔；
  - 新增 Netty 全套组件（`netty-buffer`、`netty-codec`、`netty-codec-dns`、`netty-codec-http`、`netty-codec-http2`、`netty-codec-socks`、`netty-common`、`netty-handler`、`netty-handler-proxy`、`netty-resolver`、`netty-resolver-dns`、`netty-resolver-dns-classes-macos`、`netty-resolver-dns-native-macos`、`netty-tcnative-boringssl-static`、`netty-tcnative-classes`、`netty-transport`、`netty-transport-classes-epoll`、`netty-transport-classes-kqueue`、`netty-transport-native-epoll`、`netty-transport-native-kqueue`、`netty-transport-native-unix-common`）的完整 NOTICE 文本——这是 Netty LICENSE 要求保留的"第三方 NOTICE"清单（JSR-166、Base64、Webbit、SLF4J、Apache Harmony、jbzip2、libdivsufsort、JCTools、JZlib、Compress-LZF、lz4、lzma-java、zstd-jni、jfastlz、Protocol Buffers、Bouncy Castle、Snappy、JBoss Marshalling、Caliper、Apache Commons Logging、Log4J、Aalto XML、HPACK 的多个实现、Apache Commons Lang、Maven Wrapper、dnsinfo.h、Brotli4j 等）；
  - 同时把 Jackson 与 Netty 的版本号在 NOTICE 头部对齐到新版本（`jackson-core/databind 2.17.2`、`netty-* 4.1.115.Final`、`netty-tcnative-* 2.0.69.Final`）。

## 修改详情

### `azure-bundle/LICENSE`（修改，+45/-59 行）

**修改目的**：把第三方依赖清单与新打包版本对齐，并删除已不再打包的组件。

**修改内容**：

- 升级版本号的依赖（左侧为旧，右侧为新）：
  - `com.azure:azure-core-http-netty` 1.13.5 → 1.15.7
  - `com.azure:azure-identity` 1.9.2 → 1.14.2
  - `com.azure:azure-json` 1.0.1 → 1.3.0
  - `com.azure:azure-storage-blob` 12.23.0 → 12.29.0
  - `com.azure:azure-storage-common` 12.22.0 → 12.28.0
  - `com.azure:azure-storage-file-datalake` 12.16.0 → 12.22.0
  - `com.azure:azure-storage-internal-avro` 12.8.0 → 12.14.0
  - `com.fasterxml.jackson.core:jackson-annotations/core/databind` 2.13.5 → 2.17.2
  - `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` 2.13.5 → 2.17.2
  - `com.microsoft.azure:msal4j` 1.13.8 → 1.17.2
  - `com.microsoft.azure:msal4j-persistence-extension` 1.2.0 → 1.3.0
  - `com.nimbusds:content-type` 2.2 → 2.3
  - `com.nimbusds:nimbus-jose-jwt` 9.30.2 → 9.40
  - `com.nimbusds:oauth2-oidc-sdk` 10.7.1 → 11.18
  - `io.netty:netty-*`（除 tcnative 外）4.1.93/94.Final → 4.1.115.Final
  - `io.netty:netty-tcnative-boringssl-static/classes` 2.0.61.Final → 2.0.69.Final
  - `io.projectreactor:reactor-core` 3.4.30 → 3.4.41
  - `io.projectreactor.netty:reactor-netty-core/http` 1.0.33 → 1.0.48
  - `net.minidev:accessors-smart` 2.4.9 → 2.5.1
  - `net.minidev:json-smart` 2.4.10 → 2.5.1
  - `org.ow2.asm:asm` 9.3 → 9.6
- 删除的依赖条目：
  - `com.fasterxml.jackson.dataformat:jackson-dataformat-xml` 2.13.5
  - `com.fasterxml.woodstox:woodstox-core` 6.4.0
  - `org.codehaus.woodstox:stax2-api` 4.2.1

### `azure-bundle/NOTICE`（修改，+301/-20 行）

**修改目的**：补齐 Netty 全套组件的 NOTICE 文本，并把 Jackson NOTICE 改为带分隔前缀的格式。

**修改内容**：

- 头部 Jackson 条目版本号从 `2.13.5` 更新为 `2.17.2`，并移除已不再打包的 `jackson-dataformat-xml`；
- Jackson NOTICE 正文每行前加 `| ` 前缀，便于在多份 NOTICE 合并时区分来源；
- 新增 Netty 一整段 NOTICE：先列出 22 个 Netty 组件（含版本号）的 `NOTICE for Group: ...` 头，再以 `| ` 前缀格式粘贴 Netty 项目自身的 NOTICE 全文，包括 Copyright 2014 The Netty Project、Apache 2.0 许可声明，以及 Netty 内部包含的所有第三方组件清单（JSR-166、Base64、Webbit、SLF4J、Apache Harmony、jbzip2、libdivsufsort、JCTools、JZlib、Compress-LZF、lz4、lzma-java、zstd-jni、jfastlz、Protocol Buffers、Bouncy Castle、Snappy、JBoss Marshalling、Caliper、Apache Commons Logging、Log4J、Aalto XML、HPACK 的 Twitter/Benfield/Tsujikawa 三种实现、Apache Commons Lang、Maven Wrapper、dnsinfo.h、Brotli4j），每条都标注 LICENSE 文件名与 HOMEPAGE。

## 小结

- **成效**：azure-bundle 的 `LICENSE` 与 `NOTICE` 与实际打包内容完全对齐，补齐了此前遗漏的 Netty NOTICE 全文，消除 Apache 发行版的合规风险，同时清理了已不再打包的三个 Jackson/woodstox/stax 条目。
- **影响范围**：仅 `azure-bundle` 模块的两个文本文件，不涉及任何源代码或构建逻辑变更，对运行时行为零影响。
- **回迁到 1.4.x 的注意事项**：纯文档/合规性变更，回迁安全且必要。回迁前需确认 1.4.x 分支上 azure-bundle 实际打包的依赖版本与本提交声明的版本一致——若 1.4.x 上的 Azure SDK / Netty / Jackson 版本与 main 不同，需相应调整版本号；若 1.4.x 仍打包了 `jackson-dataformat-xml`/`woodstox-core`/`stax2-api`，则不能删除这些条目。建议回迁后用 `mvn dependency:tree` 或等价手段核对一遍 azure-bundle 的实际依赖与 LICENSE/NOTICE 一致性。
