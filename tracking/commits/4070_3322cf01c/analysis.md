# 提交 4070：Build: Bump software.amazon.awssdk:bom from 2.47.2 to 2.47.5 (#17291)

## 提交信息

- **序号**：4070 / 4088
- **哈希**：3322cf01cb020bfda05e34e6bdd88beb7454ee7a
- **短哈希**：3322cf01c
- **日期**：2026-07-18 22:27:43 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.47.2 to 2.47.5 (#17291)
- **PR/Issue**：#17291

## 总体目的

Dependabot 自动升级提交，将 AWS SDK for Java v2 的 BOM（`software.amazon.awssdk:bom`）从 2.47.2 升级到 2.47.5（semver patch 补丁版本升级）。这是对提交 4051（将 AWS SDK 从 2.46.21 升级到 2.47.2）的后续补丁升级。

由于本次升级在 2.47 minor 系列内（2.47.2 → 2.47.5），`runtime-deps.txt` 中记录的版本前缀 `2.47` 不变，因此只需更新 `gradle/libs.versions.toml` 中的 BOM 版本变量，无需同步更新打包清单。patch 版本升级包含 bug 修复和安全补丁。

## 如何达成设计目的

通过 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本变量管理。Dependabot 将该变量从 `2.47.2` 改为 `2.47.5`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本变量。

**工作逻辑**：
```toml
awssdk-bom = "2.47.5"
```
将 `awssdk-bom` 变量从 `2.47.2` 改为 `2.47.5`。由于仍在 2.47 minor 系列内，`runtime-deps.txt` 中的 `2.47` 版本前缀无需更新。

## 总结

常规的 AWS SDK patch 版本升级，在 2.47 minor 系列内从 2.47.2 升级到 2.47.5，获取上游 bug 修复和安全补丁。由于 minor 版本未变，仅需更新 BOM 版本变量，无需同步打包清单。patch 级别升级风险很低。
