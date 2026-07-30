# 提交 3731：Build: Bump gradle-wrapper from 8.14.4 to 8.14.5 (#16381)

## 提交信息

- **序号**：3731 / 4088
- **哈希**：3e88117c6af6dabd818bb144da8ca594e87ec95b
- **短哈希**：3e88117c6
- **日期**：2026-05-18 15:16:09 +0200
- **作者**：Huaxin Gao
- **提交说明**：Build: Bump gradle-wrapper from 8.14.4 to 8.14.5 (#16381)
- **PR/Issue**：#16381

## 总体目的

将 Iceberg 项目使用的 Gradle Wrapper 版本从 8.14.4 升级到 8.14.5。Gradle Wrapper 通过 `gradle-wrapper.properties` 固定项目所用的 Gradle 发行版，确保所有开发者和 CI 使用相同的 Gradle 版本。本次为 patch 版本升级（8.14.4 → 8.14.5），通常包含 bug 修复与稳定性改进，保持构建工具为最新以获得最新的修复。

## 如何达成设计目的

通过 `gradle wrapper --gradle-version 8.14.5` 命令（或手动修改）更新两处：
1. `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 指向新版 gradle-8.14.5-bin.zip，并更新 `distributionSha256Sum` 用于校验下载完整性。
2. `gradlew` 脚本中用于在缺失 wrapper jar 时从 GitHub 下载的 URL 同步更新到 v8.14.5 标签。

## 修改详情

### `gradle/wrapper/gradle-wrapper.properties` (+2/-2 lines)

**修改目的**：更新 Gradle 发行版 URL 与校验和。

**工作逻辑**：
- `distributionSha256Sum` 从 `f1771298a70f6db5a29daf62378c4e18a17fc33c9ba6b14362e0cdf40610380d` 更新为 `6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854`（8.14.5 的 SHA256）。
- `distributionUrl` 从 `gradle-8.14.4-bin.zip` 更新为 `gradle-8.14.5-bin.zip`。

### `gradlew` (+1/-1 lines)

**修改目的**：更新 fallback 下载 jar 的 URL。

**工作逻辑**：
将 fallback 下载 URL 从 `https://raw.githubusercontent.com/gradle/gradle/v8.14.4/gradle/wrapper/gradle-wrapper.jar` 更新为 `https://raw.githubusercontent.com/gradle/gradle/v8.14.5/gradle/wrapper/gradle-wrapper.jar`，确保在 wrapper jar 缺失时能从对应版本标签下载。

## 总结

本提交将 Gradle Wrapper 从 8.14.4 升级到 8.14.5（patch 版本），更新了发行版 URL、SHA256 校验和以及 fallback 下载 URL。改动仅涉及构建工具版本，属于常规构建依赖维护，旨在获取最新 patch 版本的 bug 修复与稳定性改进，不影响产品代码。
