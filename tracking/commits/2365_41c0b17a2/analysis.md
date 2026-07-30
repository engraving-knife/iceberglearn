# 提交 2365：kafka-connect: resolve CVE-2025-48734 (#13561)

## 提交信息

- **序号**：2365 / 4088
- **哈希**：41c0b17a20c522e4df519bcc429f413e6a2855e5
- **短哈希**：41c0b17a2
- **日期**：2025-07-17 08:57:45 -0700
- **作者**：liko
- **提交说明**：kafka-connect: resolve CVE-2025-48734 (#13561)
- **PR/Issue**：#13561

## 总体目的

这个提交通过强制升级 `commons-beanutils` 依赖版本来解决 CVE-2025-48734 安全漏洞。CVE-2025-48734 是 Apache Commons BeanUtils 库中的一个已知安全漏洞，需要在 kafka-connect 模块中通过升级依赖版本来修复。

背景：Iceberg 的 kafka-connect 模块（`iceberg-kafka-connect-runtime` 子项目）在其 Gradle 构建配置中通过 `force` 指令统一锁定若干传递依赖的版本，以解决版本冲突和安全问题。此前该模块已通过 force 锁定了 `commons-compress`、`hadoop-shaded-guava`、`woodstox-core` 等依赖版本。本提交新增对 `commons-beanutils` 的版本强制，将其升级到 1.11.0 以修复 CVE-2025-48734。

## 如何达成设计目的

在 kafka-connect 模块的 `build.gradle` 中，向 runtime 子项目的依赖解析 `force` 块中新增 `commons-beanutils:commons-beanutils:1.11.0`，强制所有传递依赖中的 commons-beanutils 版本统一升级到 1.11.0。

## 修改详情

### `kafka-connect/build.gradle` (+1/-0 lines)

**修改目的**：强制升级 commons-beanutils 到 1.11.0 以修复 CVE-2025-48734。

**工作逻辑**：在 `project(':iceberg-kafka-connect:iceberg-kafka-connect-runtime')` 的依赖解析配置中，向已有的 `force` 块（包含 `commons-compress:1.27.1`、`hadoop-shaded-guava:1.4.0`、`woodstox-core:6.7.0`）新增一行 `force 'commons-beanutils:commons-beanutils:1.11.0'`。这确保无论哪个传递依赖引入了 commons-beanutils，其版本都会被统一提升到 1.11.0，从而修复 CVE-2025-48734 漏洞。

## 总结

该提交通过在 kafka-connect 模块的 Gradle 构建配置中强制将 `commons-beanutils` 升级到 1.11.0，修复了 CVE-2025-48734 安全漏洞。这是依赖安全升级的标准做法，单行配置变更，无源代码改动。
