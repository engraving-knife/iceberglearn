# 提交 1992：Build: Bump guava from 33.4.6-jre to 33.4.7-jre

## 提交信息

- **序号**：1992 / 4088
- **哈希**：0ebfd4ce1b982289c0a5f6ae6356c3f154022c25
- **短哈希**：0ebfd4ce1
- **日期**：2025-04-14 15:01:03 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump guava from 33.4.6-jre to 33.4.7-jre (#12789)
- **PR/Issue**：#12789

## 总体目的

本提交由 Dependabot 自动生成，将 Google Guava 依赖从 33.4.6-jre 升级到 33.4.7-jre。这是一次补丁版本（patch）升级，属于常规的依赖维护工作，用于获取 Guava 的最新 bug 修复和改进。

Guava 是 Iceberg 项目广泛使用的核心依赖库（包括 `guava` 和 `guava-testlib`），用于集合操作、缓存、并发等基础功能。保持依赖的最新补丁版本有助于获取安全修复和稳定性改进。

## 如何达成设计目的

通过修改 Gradle 版本目录（version catalog）文件中 guava 的版本号声明来完成升级。Iceberg 使用 `gradle/libs.versions.toml` 集中管理依赖版本，修改一处即可同步更新所有引用该版本的模块。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 guava 版本号。

**工作逻辑**：
将版本目录中 guava 的版本声明从 `guava = "33.4.6-jre"` 改为 `guava = "33.4.7-jre"`。该声明被 `com.google.guava:guava` 和 `com.google.guava:guava-testlib` 两个制品共同引用，升级后两者同步更新到 33.4.7-jre。

## 总结

本提交是 Dependabot 自动发起的 Guava 依赖补丁版本升级（33.4.6-jre → 33.4.7-jre），仅修改版本目录一处声明，属于常规依赖维护。
