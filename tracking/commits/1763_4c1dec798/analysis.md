# 提交 1763：Bump versions in `{LICENSE,NOTICE}` (#12337)

## 提交信息

- **序号**：1763 / 4088
- **哈希**：4c1dec7983ab4a5111aac87b3df373d5aa375650
- **短哈希**：4c1dec798
- **日期**：2025-02-20 09:09:51 +0100
- **作者**：Fokko Driesprong
- **提交说明**：Bump versions in `{LICENSE,NOTICE}` (#12337)
- **PR/Issue**：#12337

## 总体目的

本提交旨在更新各模块 LICENSE 和 NOTICE 文件中记录的第三方依赖版本号，使其与实际使用的依赖版本保持一致。

Apache 项目要求在发布物中包含 LICENSE 和 NOTICE 文件，其中 LICENSE 文件需要列出所有包含的第三方依赖及其版本号和许可证信息。当项目升级依赖版本后，这些文件需要同步更新。

本次更新涉及以下依赖版本升级：
- **Netty**：从 4.1.115.Final 升级到 4.1.118.Final（涉及 netty-codec、netty-codec-http、netty-codec-http2、netty-common、netty-handler、netty-resolver、netty-transport、netty-transport-classes-epoll、netty-transport-native-unix-common 等多个模块）
- **AWS SDK**：从 2.30.6 升级到 2.30.21（涉及 annotations、apache-client、arns、auth、aws-core、dynamodb、glue、iam 等大量 AWS SDK 模块）
- **Apache HttpComponents HttpClient 5**：从 5.4 升级到 5.4.2

## 如何达成设计目的

提交直接修改各模块的 LICENSE 和 NOTICE 文件中的版本号文本，将旧版本号替换为新版本号。修改是机械性的文本替换，不涉及任何代码逻辑变更。

## 修改详情

### `aws-bundle/LICENSE`（修改, ±45 lines）

**修改目的**：更新 AWS Bundle 的 LICENSE 文件中的依赖版本号。

**工作逻辑**：将 Netty 各模块版本从 4.1.115.Final 更新为 4.1.118.Final，将 AWS SDK 各模块版本从 2.30.6 更新为 2.30.21。涉及约 45 行版本号文本的替换。

### `aws-bundle/NOTICE`（修改, ±48 lines）

**修改目的**：更新 AWS Bundle 的 NOTICE 文件中的依赖版本号。

**工作逻辑**：与 LICENSE 文件类似的版本号替换，保持 NOTICE 文件与 LICENSE 一致。

### `azure-bundle/LICENSE`（修改, ±19 lines）

**修改目的**：更新 Azure Bundle 的 LICENSE 文件中的依赖版本号。

**工作逻辑**：将 Netty 相关依赖版本从 4.1.115.Final 更新为 4.1.118.Final。

### `azure-bundle/NOTICE`（修改, ±19 lines）

**修改目的**：更新 Azure Bundle 的 NOTICE 文件中的依赖版本号。

**工作逻辑**：与 LICENSE 文件类似的版本号替换。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE`（修改, ±15 lines）

**修改目的**：更新 Kafka-Connect Hive 运行时的 LICENSE 文件中的依赖版本号。

**工作逻辑**：版本号文本替换。

### `kafka-connect/kafka-connect-runtime/main/LICENSE`（修改, ±15 lines）

**修改目的**：更新 Kafka-Connect 主运行时的 LICENSE 文件中的依赖版本号。

**工作逻辑**：版本号文本替换。

### `open-api/LICENSE`（修改, ±1 line）

**修改目的**：更新 OpenAPI 模块的 LICENSE 文件中的 httpclient5 版本号。

**工作逻辑**：将 `org.apache.httpcomponents.client5:httpclient5` 的版本从 5.4 更新为 5.4.2。

## 小结

- **成效**：将各模块 LICENSE 和 NOTICE 文件中记录的第三方依赖版本号更新为最新实际使用的版本，确保合规性。
- **影响范围**：仅涉及许可证和声明文件，不影响任何代码逻辑。影响 aws-bundle、azure-bundle、kafka-connect、open-api 等模块的发布物。
- **回迁到 1.4.x 的注意事项**：此变更仅为文档合规性更新，回迁到 1.4.x 分支的意义不大——1.4.x 分支应使用其自身依赖版本对应的 LICENSE/NOTICE 文件。如果 1.4.x 分支的依赖版本与此提交中的版本一致，则可以参考回迁；否则应使用 1.4.x 分支实际的依赖版本号。此提交无代码依赖。
