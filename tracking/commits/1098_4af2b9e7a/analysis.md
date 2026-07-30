# 提交 1098：Build: Bump jetty from 11.0.22 to 11.0.23 (#11003)

## 提交信息

- **序号**：1098 / 4088
- **哈希**：4af2b9e7aed3efbe8c2986b204a37007fb1b16f4
- **短哈希**：4af2b9e7a
- **日期**：2024-08-26 10:47:38 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jetty from 11.0.22 to 11.0.23 (#11003)
- **PR/Issue**：#11003

## 总体目的

本提交由 Dependabot 自动生成，将 Eclipse Jetty 从 11.0.22 升级到 11.0.23。Jetty 是一个嵌入式 HTTP 服务器与 Servlet 容器，在 Iceberg 中作为测试依赖（用于 REST Catalog 的测试服务器等场景）。本次升级同时更新 `jetty-server` 与 `jetty-servlet` 两个模块，二者版本由版本目录中的 `jetty` 属性统一管理。

这是一次 patch 级别升级（11.0.x 系列），通常包含 bug 修复与安全补丁。Jetty 11.x 系列要求 Java 11+，与 Iceberg 的 Java 基线兼容。

## 如何达成设计目的

修改 `gradle/libs.versions.toml` 中的 `jetty` 版本属性，从 `11.0.22` 改为 `11.0.23`。所有引用该属性的依赖（`jetty-server`、`jetty-servlet`）会自动继承新版本，无需逐个修改模块依赖声明。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Jetty 版本号。

**工作逻辑**：将 `jetty = "11.0.22"` 改为 `jetty = "11.0.23"`。该属性在构建脚本中被 `jetty-server` 与 `jetty-servlet` 两个依赖引用（通过 `libs.jetty` 或版本占位符），修改后二者版本统一提升到 11.0.23。

## 小结

- **成效**：将 Jetty 从 11.0.22 升级到 11.0.23，获得最新的 bug 修复与安全补丁。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 一行，影响 `jetty-server` 与 `jetty-servlet` 两个依赖（主要用于测试）。
- **回迁到 1.4.x 的注意事项**：属于测试依赖的 patch 升级，向后兼容，**可安全回迁到 1.4.x**。Jetty 11.0.x 系列内升级风险极低。需确认 1.4.x 的 Jetty 基线是否已在 11.0.x 系列；若 1.4.x 使用更早的 Jetty 版本，应升级到对应分支最新 patch。
