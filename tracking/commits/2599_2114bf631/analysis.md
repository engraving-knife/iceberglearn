# 提交 2599：Fix versions in LICENSE and NOTICE files. (#14001)

## 提交信息

- **序号**：2599 / 4088
- **哈希**：2114bf631e49af532d66e2ce148ee49dd1dd1f1f
- **短哈希**：2114bf631
- **日期**：2025-09-05 10:53:00 -0700
- **作者**：JB Onofré
- **提交说明**：Fix versions in LICENSE and NOTICE files. (#14001)
- **PR/Issue**：#14001

## 总体目的

本次提交更新了各 bundle 模块和 kafka-connect 模块中 LICENSE 和 NOTICE 文件里记录的第三方依赖版本号，使其与实际使用的依赖版本保持一致。

Apache 项目要求在发布包中包含 LICENSE 和 NOTICE 文件，准确列出所有第三方依赖及其版本和许可证信息。当依赖版本升级后，这些文件需要同步更新，否则会导致发布物不符合 Apache 许可证合规要求。

本次更新涉及多个依赖的版本号变更，主要包括：
- io.netty 各组件：从 4.1.115.Final 更新到 4.1.124.Final
- software.amazon.awssdk 各组件：从 2.29.52 更新到 2.33.0
- 其他依赖的版本同步更新

## 如何达成设计目的

逐个检查各 bundle 模块的 LICENSE 和 NOTICE 文件，将其中记录的依赖版本号更新为当前构建实际使用的版本。涉及的模块包括 aws-bundle、azure-bundle、gcp-bundle 和 kafka-connect 的 hive/main 子模块。

## 修改详情

### `aws-bundle/LICENSE` (+97/-97 lines 变更规模)

**修改目的**：更新 AWS bundle 的依赖版本记录。

**工作逻辑**：将 LICENSE 中记录的各依赖版本号更新为当前实际版本，主要包括：
- io.netty 系列组件（netty-buffer, netty-codec, netty-codec-http, netty-codec-http2, netty-common, netty-handler, netty-resolver, netty-transport, netty-transport-classes-epoll, netty-transport-native-unix-common）从 4.1.115.Final 升级到 4.1.124.Final
- software.amazon.awssdk 系列组件（annotations, apache-client, arns, auth, aws-core, aws-json-protocol, aws-query-protocol, aws-xml-protocol, checksums 等）从 2.29.52 升级到 2.33.0

### `aws-bundle/NOTICE`

**修改目的**：同步更新 NOTICE 文件中的版本信息。

### `azure-bundle/LICENSE` 和 `azure-bundle/NOTICE`

**修改目的**：更新 Azure bundle 的依赖版本记录。

### `gcp-bundle/LICENSE` 和 `gcp-bundle/NOTICE`

**修改目的**：更新 GCP bundle 的依赖版本记录。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE` 和 `NOTICE`

**修改目的**：更新 Kafka Connect Hive runtime 的依赖版本记录。

### `kafka-connect/kafka-connect-runtime/main/LICENSE` 和 `NOTICE`

**修改目的**：更新 Kafka Connect main runtime 的依赖版本记录。

## 总结

这是一个合规性维护提交，确保各发布 bundle 的 LICENSE 和 NOTICE 文件准确反映当前依赖的实际版本。虽然不涉及代码逻辑变更，但对于 Apache 项目的发布合规性至关重要。版本号的不同步可能导致发布审计失败或法律合规问题。
