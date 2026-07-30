# 提交 3594：Build: Bump guava from 33.5.0-jre to 33.6.0-jre (#16116)

## 提交信息

- **序号**：3594 / 4088
- **哈希**：5acbb7a5a75ed5cccc289dae7336c0731be90597
- **短哈希**：5acbb7a5a
- **日期**：2026-04-25 23:49:32 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump guava from 33.5.0-jre to 33.6.0-jre (#16116)
- **PR/Issue**：#16116

## 总体目的

这是一个 Dependabot 自动依赖升级提交，将 Google Guava 库从版本 33.5.0-jre 升级到 33.6.0-jre。Guava 是 Google 的 Java 核心库，提供集合、缓存、并发、字符串处理等工具，被 Iceberg 广泛使用。这次升级同时覆盖 `com.google.guava:guava` 和 `com.google.guava:guava-testlib` 两个组件。这是一个 semver-minor（次版本）升级。

## 如何达成设计目的

Dependabot 自动检测到版本目录中 `guava` 版本引用有新版本可用，自动创建 PR 升级版本声明。由于 `guava` 和 `guava-testlib` 共享同一个版本变量，一次升级即覆盖两个组件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 Guava 版本声明。

**工作逻辑**：
```toml
-guava = "33.5.0-jre"
+guava = "33.6.0-jre"
```
该版本变量被 `guava` 和 `guava-testlib` 两个库别名引用，升级后两个组件统一更新到 33.6.0-jre 版本。

## 总结

这是一个常规的依赖维护提交，通过次版本升级保持 Guava 核心库的最新状态。Guava 是 Iceberg 的核心依赖之一，33.6.0-jre 版本可能包含新功能、性能改进和 bug 修复。
