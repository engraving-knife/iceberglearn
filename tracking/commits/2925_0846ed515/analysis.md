# 提交 2925：Build: Bump software.amazon.awssdk:bom from 2.39.2 to 2.39.4 (#14693)

## 提交信息

- **序号**：2925 / 4088
- **哈希**：0846ed515691461545ea635cb871fd7f06176e9f
- **短哈希**：0846ed515
- **日期**：2025-11-25 23:42:38 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.39.2 to 2.39.4 (#14693)
- **PR/Issue**：#14693

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，将 AWS SDK for Java 的 BOM 从 2.39.2 升级到 2.39.4。这是对 2912 号提交（从 2.38.7 升级到 2.39.2）的后续补丁版本升级。

此次升级为 semver-patch 级别更新（补丁版本升级），仅包含 bug 修复和安全补丁，不涉及功能变更或 API 破坏。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中的 `awssdk-bom` 版本声明，将版本号从 `2.39.2` 更新为 `2.39.4`。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：更新 AWS SDK BOM 版本声明。

**工作逻辑**：
```toml
# 修改前
awssdk-bom = "2.39.2"

# 修改后
awssdk-bom = "2.39.4"
```

## 总结

该提交将 AWS SDK for Java BOM 从 2.39.2 升级到 2.39.4，属于常规的补丁版本依赖升级。此次升级获取了 AWS SDK 在 2.39.x 系列中的最新 bug 修复和安全补丁，风险极低。
