# 提交 3722：Build: Bump jetty from 12.1.8 to 12.1.9 (#16374)

## 提交信息

- **序号**：3722 / 4088
- **哈希**：4f5033a7f7f243440e845b4968db47b8b168dcdc
- **短哈希**：4f5033a7f
- **日期**：2026-05-16 23:17:05 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jetty from 12.1.8 to 12.1.9 (#16374)
- **PR/Issue**：#16374

## 总体目的

Dependabot 自动发起的依赖升级，将 Jetty 从 12.1.8 升级到 12.1.9。Jetty 是一个开源的 Java HTTP 服务器和 Servlet 容器，Iceberg 项目通过 Gradle 版本目录引用了多个 Jetty 模块（`jetty-compression-server`、`jetty-compression-gzip`、`jetty-ee10-servlet`），这些模块统一通过 `jetty` 版本变量控制版本号。本次为 patch 版本升级（12.1.8 → 12.1.9），通常包含 bug 修复与安全补丁，是常规的依赖维护。

## 如何达成设计目的

Dependabot 通过修改 `gradle/libs.versions.toml` 中 `jetty` 版本变量的值完成升级。由于多个 Jetty 模块共享同一个版本变量，一处修改即可同步升级所有相关模块。依赖类型为 `direct:production`，更新类型为 `version-update:semver-patch`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Jetty 版本变量。

**工作逻辑**：
将 `jetty = "12.1.8"` 修改为 `jetty = "12.1.9"`。该变量被 `jetty-compression-server`、`jetty-compression-gzip`、`jetty-ee10-servlet` 三个模块引用，修改后这三个依赖同步升级到 12.1.9。

## 总结

本提交是 Dependabot 自动完成的依赖升级，将 Jetty 服务器/Servlet 容器相关依赖从 12.1.8 升级到 12.1.9（patch 版本）。改动仅涉及 Gradle 版本目录中的版本变量声明，属于常规依赖维护，主要目的是获取最新版本的 bug 修复与安全补丁。
