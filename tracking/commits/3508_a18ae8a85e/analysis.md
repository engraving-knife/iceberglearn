# 提交 3508：Build: Bump jetty from 12.1.5 to 12.1.7 (#15887)

## 提交信息

- **序号**：3508 / 4088
- **哈希**：a18ae8a85e8716bd7e1e4d99f23fbc5bcc403cdc
- **短哈希**：a18ae8a85e
- **日期**：2026-04-04 23:34:02 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jetty from 12.1.5 to 12.1.7 (#15887)
- **PR/Issue**：#15887

## 总体目的

Dependabot 自动升级 Jetty 从 12.1.5 到 12.1.7（patch 级别版本升级）。这紧跟在提交 3501（#10837）将 Jetty 升级到 12.1.5 之后，是一个例行的 patch 版本更新。影响 `jetty-server` 和 `jetty-ee10-servlet` 两个组件。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中更新版本号。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 line)

**修改目的**：升级 jetty 版本。

**工作逻辑**：`jetty = "12.1.5"` → `jetty = "12.1.7"`。

## 总结

Dependabot 自动依赖升级提交，将 Jetty 从 12.1.5 升级到 12.1.7（patch 版本）。这是在 3501 提交将 Jetty 升级到 12.x 之后的例行 patch 更新。
