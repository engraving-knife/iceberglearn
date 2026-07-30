# 提交 3521：Build: Bump jetty from 12.1.7 to 12.1.8 (#15951)

## 提交信息

- **序号**：3521 / 4088
- **哈希**：3a6863d2ff7493385fac5530677fb4078e366b82
- **短哈希**：3a6863d2f
- **日期**：2026-04-11 23:30:14 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jetty from 12.1.7 to 12.1.8 (#15951)
- **PR/Issue**：#15951

## 总体目的

Dependabot 自动生成的依赖升级 PR，将 Jetty 版本从 12.1.7 升级到 12.1.8。这是一个 semver patch 版本升级，主要包含 bug 修复与稳定性改进。

Jetty 在 Iceberg 项目中主要用于 REST catalog 的测试服务器（如 `TestBaseWithRESTServer`、`RESTCatalogServer` 测试夹具），涵盖 `jetty-compression-server`、`jetty-compression-gzip`、`jetty-ee10-servlet` 三个直接依赖。这些依赖都通过 `gradle/libs.versions.toml` 中的 `jetty` 版本引用统一管理，因此只需修改一处。

## 如何达成设计目的

由于所有 Jetty 子模块共享同一个版本号引用 `version.ref = "jetty"`，Dependabot 只需更新 `gradle/libs.versions.toml` 中 `jetty = "..."` 一行即可同步升级所有相关模块。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 Jetty 版本引用从 12.1.7 升级到 12.1.8。

**工作逻辑**：
```toml
-jetty = "12.1.7"
+jetty = "12.1.8"
```
该版本引用被 `jetty-compression-server`、`jetty-compression-gzip`、`jetty-ee10-servlet` 三个依赖别名共享，因此一次修改同步升级三个 artifact。

## 总结

Dependabot 自动升级 Jetty 至 12.1.8 patch 版本，属于例行依赖维护。由于版本号集中管理，仅改动一行即可同步升级三个相关 artifact，获取上游的 bug 修复与改进。
