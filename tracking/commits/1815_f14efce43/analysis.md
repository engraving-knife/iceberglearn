# 提交 1815：Build: Bump io.netty:netty-buffer from 4.1.118.Final to 4.1.119.Final (#12440)

## 提交信息

- **序号**：1815 / 4088
- **哈希**：f14efce435980e0d46eadc7b8eb2f32ba22008d5
- **短哈希**：f14efce43
- **日期**：2025-03-03 12:59:00 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump io.netty:netty-buffer from 4.1.118.Final to 4.1.119.Final (#12440)
- **PR/Issue**：#12440

## 总体目的

这是一个由 dependabot 自动生成的依赖版本升级提交。该提交将 `io.netty:netty-buffer` 从 4.1.118.Final 升级到 4.1.119.Final，同时联动更新了 Netty 全套相关制品（netty-codec、netty-codec-http、netty-codec-http2、netty-common、netty-handler、netty-resolver、netty-transport、netty-transport-classes-epoll、netty-transport-native-unix-common 等十余个制品）的版本声明。

Netty 是 Java 生态中最主流的异步网络通信框架，Iceberg 在 AWS、Azure 等云厂商 bundle 模块以及 kafka-connect 运行时中通过传递依赖引入 Netty（主要源自 AWS SDK / Azure SDK 的 HTTP 客户端实现）。保持 Netty 处于最新版本有助于获得网络通信相关的缺陷修复与安全补丁，Netty 版本更新时常伴随安全漏洞修复。此次升级属于 semver-patch 级别（补丁版本升级），向后兼容。

此外，该提交还同步更新了 aws-bundle、azure-bundle、kafka-connect 运行时下大量 LICENSE 与 NOTICE 文件中记录的 Netty 制品版本号，以保持法务文件与实际依赖版本的一致性。这是 Fokko 在合并时协助补充的法务文件更新。

## 如何达成设计目的

dependabot 通过修改 `gradle/libs.versions.toml` 版本目录文件中的版本变量声明，将 `netty-buffer` 从 `4.1.118.Final` 改为 `4.1.119.Final`。由于 Netty 制品通常通过 BOM 或统一版本变量管理，升级该变量联动更新全部 Netty 制品。同时批量更新了各 bundle 模块和 kafka-connect 运行时下 LICENSE 与 NOTICE 文件中记录的所有 Netty 制品版本号。

## 修改详情

### gradle/libs.versions.toml (修改, 1 line)

修改了版本目录中的 `netty-buffer = "4.1.118.Final"` 为 `netty-buffer = "4.1.119.Final"`。该变量位于版本目录的 `[versions]` 块中，控制 Netty 全套制品的版本。

### aws-bundle/LICENSE (修改, 多行)

将 netty-codec、netty-codec-http、netty-codec-http2、netty-common、netty-handler、netty-resolver、netty-transport、netty-transport-classes-epoll、netty-transport-native-unix-common 等制品的版本从 4.1.118.Final 更新为 4.1.119.Final。

### aws-bundle/NOTICE (修改, 多行)

同步更新上述 Netty 制品的 NOTICE 版本声明，共涉及 10 个制品条目。

### azure-bundle/LICENSE (修改, 多行)

将 netty-buffer、netty-codec、netty-codec-dns、netty-codec-http、netty-codec-http2、netty-codec-socks、netty-common、netty-handler、netty-handler-proxy、netty-resolver、netty-resolver-dns、netty-resolver-dns-classes-macos、netty-resolver-dns-native-macos、netty-transport、netty-transport-classes-epoll、netty-transport-classes-kqueue、netty-transport-native-epoll、netty-transport-native-kqueue、netty-transport-native-unix-common 等制品的版本更新。

### azure-bundle/NOTICE (修改, 多行)

同步更新上述 Netty 制品的 NOTICE 版本声明，涉及更多 Netty 子模块。

### kafka-connect/kafka-connect-runtime/hive/LICENSE (修改, 多行)

将多个 Netty 制品的版本从 4.1.118.Final 更新为 4.1.119.Final。

### kafka-connect/kafka-connect-runtime/hive/NOTICE (未改动, 0 line)

该文件未在本次 diff 中修改。

### kafka-connect/kafka-connect-runtime/main/LICENSE (修改, 多行)

将多个 Netty 制品的版本从 4.1.118.Final 更新为 4.1.119.Final。

## 小结

这是一个低风险的依赖补丁版本升级，核心变更仅版本目录一行，附带大量 LICENSE/NOTICE 文件的法务声明同步。回迁到 1.4.x 分支时，核心的 libs.versions.toml 变更可直接 cherry-pick；但 LICENSE/NOTICE 文件的变更需注意 1.4.x 分支的 bundle 模块和 kafka-connect 模块结构可能不同，需核对路径与 Netty 子模块集合是否匹配。由于是补丁升级且涉及安全修复，建议回迁。
