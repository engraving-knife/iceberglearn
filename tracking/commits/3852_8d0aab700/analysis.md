# 提交 3852：Build: Bump Netty pin to 4.2.15.Final (#16749)

## 提交信息

- **序号**：3852 / 4088
- **哈希**：8d0aab70028a83f96e5034a4804e18d8c51b96a7
- **短哈希**：8d0aab700
- **日期**：2026-06-09 18:42:00 -0700
- **作者**：Neelesh Salian
- **提交说明**：Build: Bump Netty pin to 4.2.15.Final (#16749)
- **PR/Issue**：#16749

## 总体目的

本提交将 Iceberg 项目中 Netty 的版本固定（pin）从 4.2.14.Final 升级到 4.2.15.Final，同时扩展了版本固定的覆盖范围，从仅覆盖 4.1.x 扩展为同时覆盖 4.1.x 和 4.2.x 两个主要版本系列。

Netty 是一个异步事件驱动的网络应用框架，Iceberg 的某些模块（如 REST catalog 服务端和客户端）依赖 Netty 进行网络通信。Iceberg 使用 Gradle 的 dependency substitution 机制将所有传递依赖的 Netty 版本统一固定到指定版本，以确保使用不含已知 CVE 漏洞的版本。

本次升级的主要目的是修复多个 Netty 安全漏洞（CVE），包括新增的 CVE-2026-44249、CVE-2026-45416、CVE-2026-45674、CVE-2026-47691 等。

## 如何达成设计目的

通过两个文件的修改实现升级：

1. `gradle/libs.versions.toml`：更新 Netty 版本号从 `4.2.14.Final` 到 `4.2.15.Final`。
2. `build.gradle`：扩展版本固定的条件判断，从只匹配 `4.1.` 前缀扩展为同时匹配 `4.1.` 和 `4.2.` 前缀，并更新 `because` 说明包含新增的 CVE 编号。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 Netty 版本号。

**工作逻辑**：
```toml
netty-buffer = "4.2.15.Final"
```
从 `4.2.14.Final` 升级到 `4.2.15.Final`。

### `build.gradle` (+4/-3 lines)

**修改目的**：扩展版本固定覆盖范围并更新 CVE 说明。

**工作逻辑**：

1. 扩展版本匹配条件，同时覆盖 4.1.x 和 4.2.x：
```gradle
if (details.requested.group == 'io.netty'
    && (details.requested.version?.startsWith('4.1.') || details.requested.version?.startsWith('4.2.'))) {
  details.useVersion(libs.versions.netty.buffer.get())
```

2. 更新 `because` 说明，包含新增的 CVE：
```gradle
details.because("Fix Netty 4.1.x and 4.2.x CVEs (CVE-2026-42577, CVE-2026-42579, CVE-2026-42583, CVE-2026-42584, CVE-2026-42587, CVE-2026-44249, CVE-2026-45416, CVE-2026-45674, CVE-2026-47691)")
```

## 总结

这是一次安全相关的依赖升级，将 Netty 版本固定从 4.2.14.Final 升级到 4.2.15.Final，修复了多个新增的 CVE 漏洞。同时扩展了版本固定的覆盖范围，确保 4.2.x 系列的传递依赖也被统一固定。这对于使用 REST catalog 功能的用户尤其重要，因为 Netty 是其网络通信的基础依赖。
