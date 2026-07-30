# 提交 1134：Build: Bump jetty from 11.0.23 to 11.0.24 (#11096)

## 提交信息

- **序号**：1134 / 4088
- **哈希**：44eca04b657cd643b0e0cfbd626aa7f92f593f64
- **短哈希**：44eca04b6
- **日期**：2024-09-09（Mon Sep 9 07:53:58 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump jetty from 11.0.23 to 11.0.24 (#11096)
- **PR/Issue**：#11096

## 总体目的

Iceberg 在 `gradle/libs.versions.toml` 中统一管理依赖版本。Jetty（`org.eclipse.jetty:jetty-server` 和 `org.eclipse.jetty:jetty-servlet`）是项目使用的 Servlet 容器依赖，主要用于测试场景下的 HTTP/Servlet 相关测试（如 REST catalog、S3FileIO 测试服务器等）。Jetty 11.0.x 是一个长期维护的 patch 线，会持续发布 bug 修复与安全补丁。

本提交由 dependabot 自动发起，把 Jetty 从 `11.0.23` 升到 `11.0.24`（一个 patch 版本升级），以获取该版本包含的 bug 修复和潜在安全补丁，保持依赖处于较新的维护状态。属于常规的依赖版本滚动升级，无 API 变更。

## 如何达成设计目的

修改 `gradle/libs.versions.toml` 中 `jetty` 这一个版本变量，从 `"11.0.23"` 改为 `"11.0.24"`。该变量在仓库内被 `jetty-server` 和 `jetty-servlet` 两个依赖坐标共享引用，因此一处改动即可同时更新两者。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：升级 Jetty 版本。

**工作逻辑**：在依赖版本目录中，把：

```toml
jetty = "11.0.23"
```

改为：

```toml
jetty = "11.0.24"
```

该文件是 Gradle 版本目录（Version Catalog），`jetty` 是一个版本别名，下游的 `jetty-server`、`jetty-servlet` 库引用都通过 `${versions.jetty}` 取值，因此这一处修改会让所有引用 Jetty 的模块在下次构建时拉取 11.0.24。属于 patch 级升级，向后兼容。

## 小结

- **成效**：Jetty 依赖升级到 11.0.24，获取最新的 bug 修复与安全补丁，保持依赖健康度。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件、一行版本号变更，无代码逻辑变更。
- **回迁到 1.4.x 的注意事项**：这是依赖版本升级，对 1.4.x 而言属于低风险变更。**可选回迁**——若 1.4.x 仍在发布且希望保持依赖补丁同步，可回迁；若 1.4.x 已进入冻结期，可不动。回迁时需注意确认 1.4.x 使用的 Jetty 主版本同为 11.0.x（而非更早的 10.x 或 9.x），否则需要评估升级范围。Jetty 主要用于测试，不影响发布产物的运行时依赖。
