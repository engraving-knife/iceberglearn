# 提交 3764：Build: Fix order in revapi.yml (#16511)

## 提交信息

- **序号**：3764 / 4088
- **哈希**：2cce97af94593afa1c442b18c9ef509c56122013
- **短哈希**：2cce97af9
- **日期**：2026-05-21 16:16:49 +0200
- **作者**：gaborkaszab
- **提交说明**：Build: Fix order in revapi.yml (#16511)
- **PR/Issue**：#16511

## 总体目的

这个提交修复了 `.palantir/revapi.yml` 配置文件中版本条目的顺序问题。Revapi 是一个 API 兼容性检查工具，用于检测 Java 库的 API 变更（如方法移除、类移除、可见性变更等）。`revapi.yml` 中的 `acceptedBreaks` 部分记录了已接受的 API 变更，按版本号分组。

问题在于 `1.10.0` 版本的条目被放在了 `1.9.0` 等较旧版本之后，而不是按版本顺序排列。这可能是之前某次编辑时的疏忽。这个提交将 `1.10.0` 条目移动到正确的位置，使其排在 `1.2.0` 之前（因为 1.10.0 > 1.2.0 在版本排序中）。

## 如何达成设计目的

将 `1.10.0` 版本的 accepted breaks 条目从文件后部（1.9.0 之后）移动到 `1.2.0` 版本条目之前，同时将列表项的缩进格式从 4 空格改为 2 空格以保持一致性。内容本身没有变化，只是位置和缩进格式的调整。

## 修改详情

### `.palantir/revapi.yml` (+113/-105 lines)

**修改目的**：将 `1.10.0` 版本的 accepted breaks 条目移动到正确的版本顺序位置。

**工作逻辑**：
- 将 `1.10.0` 条目从文件后部（1.9.0 之后）移到 `1.2.0` 条目之前。
- `1.10.0` 条目包含以下已接受的 API 变更（内容不变）：
  - **iceberg-api**：`EncryptingFileIO` 的序列化变更（新增 Manifest List 读取方法）。
  - **iceberg-core**：多个类的序列化变更、`OAuth2Manager` 继承关系变更、多个已废弃类和方法的移除（`PartitionStatsUtil`、`RefreshingAuthManager`、`RewriteTablePathUtil` 的方法等）、`ResourcePaths` 常量值变更、`PartitionStats` 方法可见性降低。
  - **iceberg-data**：`PartitionStatsHandler` 类的移除。
- 缩进格式统一为 2 空格（原来部分条目使用 4 空格）。

## 总结

这是一个配置文件整理提交，将 `revapi.yml` 中 `1.10.0` 版本的 accepted breaks 条目移动到正确的版本排序位置，并统一了缩进格式。虽然不涉及任何功能变更，但保持版本顺序的正确性有助于维护者快速定位和理解各版本的 API 变更记录。
