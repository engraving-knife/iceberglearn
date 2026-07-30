# 提交 3611：Build: Bump software.amazon.awssdk:bom from 2.42.33 to 2.42.36 (#16151)

## 提交信息

- **序号**：3611 / 4088
- **哈希**：b0df3ca01d61b2f7ae7143ac660c6b16e33b6e46
- **短哈希**：b0df3ca01
- **日期**：2026-04-28 19:05:57 -0700
- **作者**：Yuya Ebihara
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.42.33 to 2.42.36 (#16151)
- **PR/Issue**：#16151

## 总体目的

这个提交将 AWS SDK for Java 2 的 BOM（Bill of Materials）从版本 2.42.33 升级到 2.42.36，这是一个 patch 级别的版本更新。

AWS SDK BOM 用于统一管理所有 AWS SDK 模块的版本，确保它们之间兼容。升级到 2.42.36 可以获得最新的 bug 修复和改进。同时，AWS CRT（Common Runtime）也从 0.43.9 升级到 0.44.0。

## 如何达成设计目的

通过 Dependabot 自动生成的 PR，更新以下文件：
1. `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本号。
2. `aws-bundle/runtime-deps.txt` 中所有 AWS SDK 依赖的版本号。
3. `kafka-connect/kafka-connect-runtime/runtime-deps.txt` 中所有 AWS SDK 依赖的版本号。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 AWS SDK BOM 版本声明。

**工作逻辑**：
```toml
awssdk-bom = "2.42.36"  # 从 2.42.33 升级
```

### `aws-bundle/runtime-deps.txt` (+45/-45 lines)

**修改目的**：同步更新 AWS bundle 中所有 AWS SDK 依赖的版本。

**工作逻辑**：
所有 `software.amazon.awssdk:*` 依赖从 2.42.33 升级到 2.42.36，`software.amazon.awssdk.crt:aws-crt` 从 0.43.9 升级到 0.44.0。涉及约 45 个依赖条目的版本号更新。

### `kafka-connect/kafka-connect-runtime/runtime-deps.txt` (+40/-40 lines)

**修改目的**：同步更新 Kafka Connect runtime 中所有 AWS SDK 依赖的版本。

**工作逻辑**：
与 aws-bundle 类似，所有 AWS SDK 依赖版本从 2.42.33 升级到 2.42.36，aws-crt 从 0.43.9 升级到 0.44.0。

## 总结

这是一个 Dependabot 自动依赖升级提交，将 AWS SDK for Java 2 从 2.42.33 升级到 2.42.36（patch 版本），同时升级 AWS CRT 从 0.43.9 到 0.44.0。作为 patch 级别升级，通常包含 bug 修复和小改进，风险较低。三个文件的修改确保了版本声明和依赖清单的一致性。
