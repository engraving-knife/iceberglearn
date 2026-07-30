# 提交 4040：Build: Bump jetty from 12.1.10 to 12.1.11 (#17220)

## 提交信息

- **序号**：4040 / 4088
- **哈希**：39b7d1b2fa8c48cd511ce58ca7a16a7c1d3369de
- **短哈希**：39b7d1b2f
- **日期**：2026-07-15 18:51:11 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jetty from 12.1.10 to 12.1.11 (#17220)
- **PR/Issue**：#17220

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 Jetty 从 12.1.10 升级到 12.1.11（semver patch 补丁版本升级）。Jetty 是 Iceberg 使用的嵌入式 HTTP 服务器/Servlet 容器，本次升级涉及三个 Jetty 组件：

- `org.eclipse.jetty.compression:jetty-compression-server` 12.1.10 → 12.1.11
- `org.eclipse.jetty.compression:jetty-compression-gzip` 12.1.10 → 12.1.11
- `org.eclipse.jetty.ee10:jetty-ee10-servlet` 12.1.10 → 12.1.11

这些组件通过 `gradle/libs.versions.toml` 中的统一 `jetty` 版本变量引用，因此只需更新一处版本号即可同步升级所有 Jetty 组件。patch 版本升级通常包含 bug 修复和安全补丁，属于低风险维护升级。

## 如何达成设计目的

Iceberg 使用 Gradle 版本目录（version catalog）集中管理依赖版本。Jetty 的版本通过 `gradle/libs.versions.toml` 中的 `jetty = "..."` 变量统一声明，各 Jetty 模块引用该变量。Dependabot 识别到这是统一版本变量，因此只需将 `jetty` 的值从 `12.1.10` 改为 `12.1.11`，所有引用该变量的 Jetty 组件便会同步升级到 12.1.11。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Jetty 版本变量。

**工作逻辑**：
```toml
jetty = "12.1.11"
```
将版本目录中 `jetty` 变量从 `12.1.10` 改为 `12.1.11`。该变量被 `jetty-compression-server`、`jetty-compression-gzip`、`jetty-ee10-servlet` 等模块引用，更新后这些依赖全部升级到 12.1.11。

## 总结

这是一个常规的依赖补丁版本升级，通过版本目录统一变量一处修改即完成全部 Jetty 组件的同步升级，确保 Iceberg 使用的 Jetty 服务器组件保持最新补丁版本，获得上游的 bug 修复与安全改进。patch 级别升级风险很低。
