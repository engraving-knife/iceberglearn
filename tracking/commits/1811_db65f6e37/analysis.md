# 提交 1811：Build: Bump org.mongodb:bson from 4.11.0 to 4.11.5 (#12438)

## 提交信息

- **序号**：1811 / 4088
- **哈希**：db65f6e374ecfac16d81e81eb2f6f6a2c79ffbc2
- **短哈希**：db65f6e37
- **日期**：2025-03-03 12:53:34 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.mongodb:bson from 4.11.0 to 4.11.5 (#12438)
- **PR/Issue**：#12438

## 总体目的

这是一个由 dependabot 自动生成的依赖版本升级提交。该提交将 `org.mongodb:bson` 从 4.11.0 升级到 4.11.5。

BSON（Binary JSON）是 MongoDB Java 驱动的核心库之一，提供二进制 JSON 的序列化与反序列化能力。在 Iceberg 项目中，BSON 库主要用于支持 Variant 数据类型相关功能——Variant 类型在内部存储格式上借鉴了 BSON 的二进制编码思想。保持 BSON 库处于最新版本有助于获得缺陷修复与安全补丁。

此次升级属于 semver-patch 级别（补丁版本升级），按照语义化版本约定，4.11.5 相对于 4.11.0 仅包含向后兼容的缺陷修复，不会引入破坏性变更。

## 如何达成设计目的

dependabot 通过修改 `gradle/libs.versions.toml` 版本目录文件中的版本变量声明，将 `bson-ver` 从 `4.11.0` 改为 `4.11.5`。Iceberg 使用 Gradle 版本目录集中管理依赖版本，修改该变量后所有引用 `bson-ver` 的模块依赖都会自动使用新版本。

## 修改详情

### gradle/libs.versions.toml (修改, 1 line)

修改了版本目录中的 `bson-ver = "4.11.0"` 为 `bson-ver = "4.11.5"`。该变量位于版本目录的 `[versions]` 块中，其他模块通过引用此变量声明 BSON 依赖。这是该提交的唯一实质性变更。

## 小结

这是一个低风险的依赖补丁版本升级，仅修改版本目录中的一行。回迁到 1.4.x 分支时，若该分支的 libs.versions.toml 中存在 bson-ver 变量，则可直接 cherry-pick；需注意 1.4.x 分支可能使用不同的版本目录结构。由于是补丁升级，回迁风险极低。
