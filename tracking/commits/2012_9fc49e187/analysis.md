# 提交 2012：AWS, Azure, Kafka: Fix versions in LICENSE and NOTICE (#12831)

## 提交信息

- **序号**：2012 / 4088
- **哈希**：9fc49e187069c7ec2493ac0abf20f73175b3df89
- **短哈希**：9fc49e187
- **日期**：2025-04-17 16:28:37 +0200
- **作者**：JB Onofré
- **提交说明**：AWS, Azure, Kafka: Fix versions in LICENSE and NOTICE (#12831)
- **PR/Issue**：#12831

## 总体目的

这个提交修复了 AWS、Azure 和 Kafka Connect 三个 bundle 模块中 LICENSE 和 NOTICE 文件里记录的第三方依赖版本号，使其与实际打包的依赖版本保持一致。

Apache 项目在发布时需要附带 LICENSE 和 NOTICE 文件，准确列出所有第三方依赖及其版本、许可证信息。当依赖版本因升级或降级发生变化时，这些文件需要同步更新。此前这些文件中记录的部分依赖版本与实际不符——例如 netty 组件被错误地标记为 4.2.0.Final（实际为 4.1.115.Final），aws-crt 版本不准确，Google Cloud 相关库版本过时等。本提交将这些版本号修正为与实际依赖一致的正确值。

## 如何达成设计目的

通过逐一审查和修正 8 个 LICENSE/NOTICE 文件中记录的依赖版本号来实现。变更涉及三个模块：aws-bundle、azure-bundle 和 kafka-connect-runtime（含 hive 和 main 两个子目录）。主要修正的依赖包括 netty 组件版本统一、azure-identity 升级、Google Cloud 系列库版本更新、grpc 版本更新等。

## 修改详情

### `aws-bundle/LICENSE` (修改, +11/-11 lines)

**修改目的**：修正 AWS bundle 中 netty 组件和 aws-crt 的版本号。

**工作逻辑**：
- netty-buffer 从 4.1.112.Final 升级到 4.1.115.Final。
- 其余 9 个 netty 组件（netty-codec、netty-codec-http、netty-codec-http2、netty-common、netty-handler、netty-resolver、netty-transport、netty-transport-classes-epoll、netty-transport-native-unix-common）从 4.2.0.Final 降级修正为 4.1.115.Final。
- aws-crt 从 0.33.6 修正为 0.33.3。

### `aws-bundle/NOTICE` (修改, +10/-10 lines)

**修改目的**：同步更新 NOTICE 文件中 netty 组件的版本号，与 LICENSE 保持一致。

### `azure-bundle/LICENSE` (修改, +20/-20 lines)

**修改目的**：修正 Azure bundle 中依赖版本号。

**工作逻辑**：
- azure-identity 从 1.14.2 升级到 1.15.0。
- 大量 netty 组件从 4.2.0.Final 修正为 4.1.115.Final（codec、codec-http、codec-http2、common、handler、handler-proxy、resolver、transport 等）或 4.1.112.Final（codec-dns、resolver-dns、resolver-dns-classes-macos、resolver-dns-native-macos）。

### `azure-bundle/NOTICE` (修改, +19/-19 lines)

**修改目的**：同步更新 NOTICE 文件中对应依赖的版本号。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE` (修改, +63/-63 lines)

**修改目的**：修正 Kafka Connect Hive 运行时中第三方依赖版本号。

**工作逻辑**：
- Google API 系列：api-common 2.43.0→2.46.1，gax 2.60.0→2.63.1，gax-grpc/gax-httpjson 2.60.0→2.63.1。
- Google Cloud Storage：gapic/grpc/proto-google-cloud-storage-v2 2.48.1→2.50.0。
- Google Auth：credentials/oauth2-http 1.31.0→1.33.1。
- Google Cloud Core：2.50.0→2.53.1。
- Guava：33.4.6-jre 降级为 33.4.0-jre。
- Google HTTP Client：1.45.3→1.46.3。
- Protobuf：4.29.0→4.29.4。
- gRPC：1.69.0→1.70.0。
- commons-codec：1.17.2→1.18.0。

### `kafka-connect/kafka-connect-runtime/hive/NOTICE` (修改, +1/-1 lines)

**修改目的**：同步更新 NOTICE 文件中的版本号。

### `kafka-connect/kafka-connect-runtime/main/LICENSE` (修改, +63/-63 lines)

**修改目的**：与 hive/LICENSE 相同的版本修正，应用于 main 目录。

### `kafka-connect/kafka-connect-runtime/main/NOTICE` (修改, +2/-1 lines)

**修改目的**：同步更新 NOTICE 文件中的版本号。

## 总结

本提交是对三个 bundle 模块 LICENSE 和 NOTICE 文件的版本号修正，确保文档中记录的第三方依赖版本与实际打包的版本一致。主要修正包括将 netty 组件从错误的 4.2.0.Final 修正为 4.1.115.Final/4.1.112.Final，以及更新 Google Cloud、gRPC 等库的版本号。这对于 Apache 发布合规性至关重要。
