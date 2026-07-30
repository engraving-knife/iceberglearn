# 提交 3083：Kafka Connect: Fix CVE-2025-55163 in grpc-netty-shaded (#14985)

## 提交信息

- **序号**：3083 / 4088
- **哈希**：cde5b9f984344d134fc907b2ff3883499d7e54c3
- **短哈希**：cde5b9f98
- **日期**：2026-01-08
- **作者**：Robin Moffatt
- **提交说明**：Kafka Connect: Fix CVE-2025-55163 in grpc-netty-shaded (#14985)
- **PR/Issue**：#14985

## 总体目的

`grpc-netty-shaded` 是 gRPC Java 的一个可选依赖，提供内嵌了 Netty 网络栈的 shaded（重命名包以避免冲突）版本。在 Iceberg 的 Kafka Connect 模块中，该依赖通过传递依赖被引入（主要用于 GCP BigQuery 等需要 gRPC 通信的存储后端连接）。`grpc-netty-shaded` 版本 1.71.0 存在已知安全漏洞 CVE-2025-55163，本提交将其升级到 1.76.2 以修复该漏洞。

这是一个安全漏洞修复（CVE fix）类提交。`grpc-netty-shaded` 的版本号遵循语义化版本（SemVer），从 1.71.0 升级到 1.76.2 属于同一次大版本（1.x）内的 minor 版本升级（71 -> 76），按照 SemVer 约定应保持向后兼容，不会引入破坏性变更。预期影响是修复安全漏洞的同时保持现有功能正常。

此外，由于 Iceberg 的 GCP bundle 和 Kafka Connect runtime 模块在发布时会在 LICENSE 和 NOTICE 文件中声明所包含的第三方依赖及其版本，本次升级也同步更新了这些法律合规文件中的版本号声明。

## 如何达成设计目的

通过 Gradle 的依赖强制版本（force resolution）机制，在 `kafka-connect/build.gradle` 中将 `io.grpc:grpc-netty-shaded` 强制为 `1.76.2`，覆盖传递依赖解析出的旧版本。同时同步更新 `gcp-bundle` 和 `kafka-connect-runtime`（hive 和 main 两个变体）的 LICENSE/NOTICE 文件中该依赖的版本声明。

## 修改详情

### `kafka-connect/build.gradle` (+1/-0 lines)

**修改目的**：在 Kafka Connect 模块的 Gradle 构建中强制 `grpc-netty-shaded` 版本为 1.76.2。

**工作逻辑**：
在 `iceberg-kafka-connect-runtime` 子项目的依赖配置中，已有的 `force` 语句块（用于统一强制若干传递依赖的版本，如 `hadoop-shaded-guava:1.5.0`、`woodstox-core:6.7.0`、`commons-beanutils:1.11.0`）中新增一行 `force 'io.grpc:grpc-netty-shaded:1.76.2'`。Gradle 的 `force` 指令会在依赖解析时将该依赖的所有版本统一覆盖为指定版本，确保无论通过哪条传递依赖路径引入的 `grpc-netty-shaded` 都使用 1.76.2，从而修复 CVE-2025-55163。

### `gcp-bundle/LICENSE` (+1/-1 lines)

**修改目的**：更新 GCP bundle LICENSE 文件中 `grpc-netty-shaded` 的版本声明。

**工作逻辑**：
将该依赖声明行从 `Group: io.grpc  Name: grpc-netty-shaded  Version: 1.71.0` 更新为 `Version: 1.76.2`，许可证保持 Apache 2.0 不变。GCP bundle 是一个 fat jar，需要在 LICENSE 中列出所有包含的第三方库及其版本和许可证。

### `gcp-bundle/NOTICE` (+1/-1 lines)

**修改目的**：更新 GCP bundle NOTICE 文件中 `grpc-netty-shaded` 的版本声明。

**工作逻辑**：
将 NOTICE 声明从 `NOTICE for Group: io.grpc  Name: grpc-netty-shaded  Version: 1.71.0` 更新为 `Version: 1.76.2`。NOTICE 文件包含该依赖自身的 NOTICE 内容（此处为 Netty 项目的 NOTICE），版本号需要与实际打包的依赖一致。

### `kafka-connect/kafka-connect-runtime/hive/LICENSE` (+1/-1 lines)

**修改目的**：更新 Kafka Connect runtime（hive 变体）LICENSE 中 `grpc-netty-shaded` 版本声明。逻辑同 `gcp-bundle/LICENSE`，从 1.71.0 更新为 1.76.2。

### `kafka-connect/kafka-connect-runtime/hive/NOTICE` (+1/-1 lines)

**修改目的**：更新 Kafka Connect runtime（hive 变体）NOTICE 中 `grpc-netty-shaded` 版本声明。逻辑同 `gcp-bundle/NOTICE`，从 1.71.0 更新为 1.76.2。

### `kafka-connect/kafka-connect-runtime/main/LICENSE` (+1/-1 lines)

**修改目的**：更新 Kafka Connect runtime（main 变体）LICENSE 中 `grpc-netty-shaded` 版本声明。逻辑同上，从 1.71.0 更新为 1.76.2。

### `kafka-connect/kafka-connect-runtime/main/NOTICE` (+1/-1 lines)

**修改目的**：更新 Kafka Connect runtime（main 变体）NOTICE 中 `grpc-netty-shaded` 版本声明。逻辑同上，从 1.71.0 更新为 1.76.2。

## 总结

本提交修复了 Kafka Connect 模块中 `grpc-netty-shaded` 依赖的 CVE-2025-55163 安全漏洞，通过 Gradle `force` 指令将版本从 1.71.0 升级到 1.76.2（同大版本内的 minor 升级，向后兼容），并同步更新了 GCP bundle 和 Kafka Connect runtime 两个发布产物的 LICENSE/NOTICE 合规文件。`grpc-netty-shaded` 在项目中用于 gRPC 通信（如连接 GCP 存储后端），升级后预期在修复安全漏洞的同时不影响现有功能。
