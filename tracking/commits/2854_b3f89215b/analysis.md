# 提交 2854：Build: Bump software.amazon.awssdk:bom from 2.36.2 to 2.38.2 (#14541)

## 提交信息

- **序号**：2854 / 4088
- **哈希**：b3f89215bd35a82d210a4767a395d12ff655ce3d
- **短哈希**：b3f89215b
- **日期**：2025-11-09 15:58:18 -0800
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.36.2 to 2.38.2 (#14541)
- **PR/Issue**：#14541

## 总体目的

这个提交将 AWS SDK for Java 的 BOM 从 2.36.2 升级到 2.38.2，由 dependabot 发起、Yuya Ebihara 审核合并。AWS SDK 是 Iceberg 项目中 `iceberg-aws` 模块的核心依赖，用于与 S3、DynamoDB 等 AWS 服务交互。

这次升级跨了两个次版本（2.36 -> 2.37 -> 2.38），属于次版本号（minor）升级。值得注意的是，AWS SDK 2.38.x 版本对内部包结构进行了调整，具体表现为 `SignerConstant` 类的包路径发生了变化，从 `software.amazon.awssdk.auth.signer.internal.SignerConstant` 移动到了 `software.amazon.awssdk.http.auth.aws.signer.SignerConstant`。这表明新版本对签名相关的 API 进行了重构，将内部类迁移到了更规范的公共包路径下。

## 如何达成设计目的

修改涉及两个层面：

1. **版本声明更新**：在 `gradle/libs.versions.toml` 中将 `awssdk-bom` 从 2.36.2 升级到 2.38.2。
2. **适配 API 变更**：由于新版本中 `SignerConstant` 类的包路径发生变化，需要更新三个引用该类的 Java 文件的 import 语句，将旧的 `software.amazon.awssdk.auth.signer.internal.SignerConstant` 替换为新的 `software.amazon.awssdk.http.auth.aws.signer.SignerConstant`。

值得注意的是，旧的包路径包含 `internal`，表明该类原本是内部实现；新的包路径移除了 `internal` 并放在了 `http.auth.aws.signer` 下，说明 AWS SDK 团队将其提升为更正式的 API 位置。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本声明。

**工作逻辑**：将 `awssdk-bom` 变量从 `2.36.2` 修改为 `2.38.2`。BOM 作为版本清单管理所有 AWS SDK 组件的版本兼容性。

### `aws/src/main/java/org/apache/iceberg/aws/RESTSigV4AuthSession.java` (+1/-1 lines)

**修改目的**：适配 AWS SDK 2.38.x 中 SignerConstant 类的包路径变更。

**工作逻辑**：将 import 语句从 `software.amazon.awssdk.auth.signer.internal.SignerConstant` 改为 `software.amazon.awssdk.http.auth.aws.signer.SignerConstant`。`RESTSigV4AuthSession` 类用于在 REST 客户端中实现 SigV4 签名认证，`SignerConstant` 提供了签名相关的常量（如签名算法名称等）。

### `aws/src/test/java/org/apache/iceberg/aws/TestRESTSigV4Signer.java` (+1/-1 lines)

**修改目的**：适配 SignerConstant 包路径变更（测试代码）。

**工作逻辑**：同样更新 import 语句，将测试代码中对 `SignerConstant` 的引用指向新的包路径。

### `aws/src/integration/java/org/apache/iceberg/aws/s3/signer/TestS3RestSigner.java` (+1/-1 lines)

**修改目的**：适配 SignerConstant 包路径变更（集成测试代码）。

**工作逻辑**：更新集成测试中的 import 语句，确保 S3 REST 签名器的集成测试在新版 AWS SDK 下能正确编译和运行。

## 总结

这个提交将 AWS SDK BOM 从 2.36.2 升级到 2.38.2，同时适配了新版本中 `SignerConstant` 类的包路径变更。修改涉及版本目录文件和三个 Java 文件的 import 语句更新。这次升级体现了 AWS SDK 在 2.38.x 版本中对签名 API 的结构重构，将原本位于 `internal` 包的类迁移到了更规范的公共包路径下。
