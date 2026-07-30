# 提交 4041：Build: Bump jackson-bom from 2.22.0 to 2.22.1 (#17224)

## 提交信息

- **序号**：4041 / 4088
- **哈希**：cd2e2a6c2fdc7a5abec39d342c4693bb3ec2814f
- **短哈希**：cd2e2a6c2
- **日期**：2026-07-15 18:51:31 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump jackson-bom from 2.22.0 to 2.22.1 (#17224)
- **PR/Issue**：#17224

## 总体目的

Dependabot 自动升级提交，将 Jackson BOM（Bill of Materials）从 2.22.0 升级到 2.22.1（semver patch 补丁版本升级）。Jackson 是 Java 生态中最常用的 JSON 处理库，Iceberg 通过 BOM 统一管理 Jackson 各模块（如 `jackson-databind`、`jackson-core`、`jackson-annotations` 等）的版本，确保它们彼此兼容。

2.22.1 是 patch 版本升级，通常包含 bug 修复和小的兼容性改进，不引入破坏性变更，属于低风险维护升级。

## 如何达成设计目的

通过 Gradle 版本目录 `gradle/libs.versions.toml` 中的 `jackson-bom` 版本变量统一管理。Dependabot 将该变量从 `2.22.0` 改为 `2.22.1`，所有引用该 BOM 的 Jackson 模块版本随之同步升级。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Jackson BOM 版本。

**工作逻辑**：
```toml
jackson-bom = "2.22.1"
```
将 `jackson-bom` 变量从 `2.22.0` 改为 `2.22.1`。该 BOM 被 Gradle 用来统一对齐所有 Jackson 模块的版本。

## 总结

常规的 Jackson 依赖补丁版本升级，通过版本目录 BOM 变量一处修改完成全部 Jackson 模块的同步升级，保持 JSON 处理栈的最新补丁版本，获取上游 bug 修复。patch 级别升级风险很低。
