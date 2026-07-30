# 提交 2046：Build: Bump org.apache.httpcomponents.client5:httpclient5

## 提交信息

- **序号**：2046 / 4088
- **哈希**：ef45c137f53b59ca5caf316df2e0e0e7901cb0ac
- **短哈希**：ef45c137f
- **日期**：2025-04-28 08:25:37 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.apache.httpcomponents.client5:httpclient5 (#12906)
- **PR/Issue**：#12906

## 总体目的

本提交由 Dependabot 自动生成，将 Apache HttpComponents HttpClient5 依赖从 5.4.3 升级到 5.4.4。httpclient5 是 Iceberg 中用于 HTTP 通信的客户端库，被多个 Catalog 实现和 IO 模块使用。此次升级为补丁版本升级（semver-patch），包含 bug 修复和安全改进。

## 如何达成设计目的

通过修改 Gradle 版本目录文件中的 httpclient5 版本号来完成升级。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 httpclient5 版本号。

**工作逻辑**：
将 `httpcomponents-httpclient5 = "5.4.3"` 改为 `httpcomponents-httpclient5 = "5.4.4"`。此版本变量被 `org.apache.httpcomponents.client5:httpclient5` 依赖引用，自动从 5.4.3 升级到 5.4.4。

## 总结

Dependabot 自动依赖升级提交，将 Apache HttpComponents HttpClient5 从 5.4.3 升级到 5.4.4（补丁版本）。改动仅 1 行版本号变更。
