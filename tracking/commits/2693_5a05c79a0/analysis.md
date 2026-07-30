# 提交 2693：Build: Bump org.immutables:value from 2.11.3 to 2.11.4 (#14205)

## 提交信息

- **序号**：2693 / 4088
- **哈希**：5a05c79a05d5c52781e2f13dbd4b639a88696957
- **短哈希**：5a05c79a0
- **日期**：2025-09-29 08:58:03 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.immutables:value from 2.11.3 to 2.11.4 (#14205)
- **PR/Issue**：#14205

## 总体目的

本提交由 Dependabot 自动生成，将项目依赖 `org.immutables:value` 从 `2.11.3` 升级到 `2.11.4`。Immutables 是一个 Java 注解处理器，用于生成不可变值对象（immutable value objects）的代码，Iceberg 在部分模块中使用它来定义不可变模型类。

本次升级属于 semver-patch（补丁版本）升级，依据 Dependabot 元数据为 `direct:production`、`version-update:semver-patch`，意味着是直接生产依赖的小版本更新，通常包含 bug 修复与小改进，理论上保持向后兼容。Dependabot 的提交说明中给出了上游 release notes、changelog 与 commits 对比链接，便于审阅者核查变更内容。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml`，将 `immutables-value` 版本号从 `2.11.3` 改为 `2.11.4`。Gradle 版本目录集中管理所有依赖版本，单一改动即可让所有引用该别名的模块统一升级，避免版本分散。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 Immutables value 库版本。

**工作逻辑**：将 `immutables-value = "2.11.3"` 改为 `immutables-value = "2.11.4"`。该别名在构建脚本中被引用以引入 Immutables 注解处理依赖，改后所有相关模块构建时自动拉取 2.11.4 版本。

## 总结

Dependabot 自动升级 `org.immutables:value` 至 2.11.4 的补丁版本更新，属于常规依赖维护，旨在获取上游的最新修复与改进。改动仅一行版本号，风险低，依赖 Gradle 版本目录集中生效。
