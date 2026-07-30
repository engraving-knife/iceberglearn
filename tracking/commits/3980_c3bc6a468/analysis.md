# 提交 3980：Build: Bump software.amazon.awssdk:bom from 2.46.15 to 2.46.17 (#17099)

## 提交信息

- **序号**：3980 / 4088
- **哈希**：c3bc6a46836ea0eda547846fbe8dad2033869d18
- **短哈希**：c3bc6a468
- **日期**：2026-07-05 00:29:30 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump software.amazon.awssdk:bom from 2.46.15 to 2.46.17 (#17099)
- **PR/Issue**：#17099

## 总体目的

Dependabot 自动升级 AWS SDK BOM 从 2.46.15 到 2.46.17，补丁版本升级，包含 bug 修复和安全补丁。这是提交 3965 升级到 2.46.15 后的后续升级。

## 如何达成设计目的

修改 Gradle 版本目录中的 `awssdk-bom` 版本号。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 AWS SDK BOM 版本。

**工作逻辑**：
```toml
awssdk-bom = "2.46.17"  # 原为 "2.46.15"
```

## 总结

常规依赖升级，将 AWS SDK BOM 从 2.46.15 升级到 2.46.17。
