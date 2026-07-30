# 提交 2520：Build: Bump jetty from 11.0.25 to 11.0.26 (#13838)

## 提交信息

- **序号**：2520 / 4088
- **哈希**：389c04cfb0d97c34d8321439f501f9b1c0f7d72d
- **短哈希**：389c04cfb
- **日期**：2025-08-18 09:36:22 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jetty from 11.0.25 to 11.0.26 (#13838)
- **PR/Issue**：#13838

## 总体目的

这是一个由 Dependabot 自动生成的依赖升级提交，将 Jetty 服务器和 Servlet 库从 11.0.25 升级到 11.0.26。

Jetty 是一个开源的 Java HTTP 服务器和 Servlet 容器。Iceberg 项目使用 Jetty 的两个模块：
- `org.eclipse.jetty:jetty-server`：HTTP 服务器组件
- `org.eclipse.jetty:jetty-servlet`：Servlet 容器组件

这两个模块主要用于 REST Catalog 的测试服务器（RESTCatalogServer），为 Iceberg REST API 提供运行时的 HTTP 服务支持。

此次升级属于补丁版本更新（semver-patch），通常包含 bug 修复和安全补丁。

## 如何达成设计目的

Dependabot 自动检测到 Jetty 有新版本发布，在 `gradle/libs.versions.toml` 中将 Jetty 版本号从 11.0.25 更新为 11.0.26。由于使用的是版本变量（version alias），两个模块（jetty-server 和 jetty-servlet）会同时升级到新版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 Jetty 依赖版本号。

**工作逻辑**：将 Gradle 版本目录文件中 `jetty` 的版本号从 `11.0.25` 改为 `11.0.26`，使 `jetty-server` 和 `jetty-servlet` 两个模块同时使用新版本。

## 总结

这是一个常规的依赖维护提交，将 Jetty HTTP 服务器和 Servlet 容器升级到最新补丁版本。Jetty 主要用于 REST Catalog 测试环境，保持其最新版本有助于获取安全补丁和 bug 修复。
