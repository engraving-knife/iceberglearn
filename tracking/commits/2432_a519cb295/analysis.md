# 提交 2432：Build: Bump orc from 1.9.6 to 1.9.7 (#13470)

## 提交信息

- **序号**：2432 / 4088
- **哈希**：a519cb295183f3f0e59d4f62bce3734a1684f26b
- **短哈希**：a519cb295
- **日期**：2025-07-30 16:12:59 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump orc from 1.9.6 to 1.9.7 (#13470)
- **PR/Issue**：#13470

## 总体目的

本提交由 dependabot 自动生成，将 Apache ORC 依赖从 1.9.6 升级到 1.9.7。ORC 是 Iceberg 支持的文件格式之一，通过 `gradle/libs.versions.toml` 中统一管理的 `orc` 版本变量控制 `orc-core` 和 `orc-tools` 两个构件。

这是一次 patch 版本升级（1.9.6 → 1.9.7），按照语义化版本约定，patch 升级仅包含 bug 修复和小的改进，不引入破坏性变更。dependabot 在提交信息中标注了 `update-type: version-update:semver-patch`。升级 ORC 可以获得最新的问题修复，提升 ORC 格式读写的稳定性与兼容性。

## 如何达成设计目的

只需在版本目录文件中修改 `orc` 变量的值即可，因为 `orc-core` 和 `orc-tools` 都引用该变量，Gradle 会自动解析为 1.9.7。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 ORC 依赖版本。

**工作逻辑**：将 `orc = "1.9.6"` 修改为 `orc = "1.9.7"`。该变量在 libraries 目录中被 `orc-core` 和 `orc-tools` 构件引用，因此一次修改即可同时升级两个构件。

## 总结

这是一个由 dependabot 自动发起的依赖版本升级，将 ORC 从 1.9.6 升级到 1.9.7。作为 patch 版本升级，风险较低，主要是获取 ORC 社区的最新 bug 修复。改动仅涉及一行版本号配置。
