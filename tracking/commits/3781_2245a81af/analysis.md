# 提交 3781：Build: Bump slf4j from 2.0.17 to 2.0.18 (#16553)

## 提交信息

- **序号**：3781 / 4088
- **哈希**：2245a81af168e6b9d2ee51c49ecec8b4c8c0a876
- **短哈希**：2245a81af
- **日期**：2026-05-24 10:30:27 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump slf4j from 2.0.17 to 2.0.18 (#16553)
- **PR/Issue**：#16553

## 总体目的

这是 Dependabot 自动生成的依赖升级提交，将 SLF4J（Simple Logging Facade for Java）从 2.0.17 升级到 2.0.18。这是一个 semver-patch 级别的升级，影响 `slf4j-api` 和 `slf4j-simple` 两个模块。SLF4J 是 Java 日志门面框架，Iceberg 使用它进行日志记录。

## 如何达成设计目的

在 `gradle/libs.versions.toml` 中更新 `slf4j` 版本号。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 SLF4J 日志框架版本。

**工作逻辑**：将 `slf4j = "2.0.17"` 改为 `slf4j = "2.0.18"`，同时影响 `slf4j-api` 和 `slf4j-simple` 两个依赖的版本。

## 总结

常规的依赖维护提交，将 SLF4J 从 2.0.17 升级到 2.0.18，获取最新的 bug 修复。
