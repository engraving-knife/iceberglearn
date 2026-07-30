# 提交 2814：Build: Bump junit-platform from 1.13.4 to 1.14.1 (#14469)

## 提交信息

- **序号**：2814 / 4088
- **哈希**：7d7a90538891742c5a3e4d3b22b95798f15d66a4
- **短哈希**：7d7a90538
- **日期**：2025-11-01 22:15:20 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump junit-platform from 1.13.4 to 1.14.1 (#14469)
- **PR/Issue**：#14469

## 总体目的

这是 Dependabot 自动生成的依赖版本升级提交。JUnit Platform 是 JUnit 5 测试框架的基础平台层，提供测试引擎 API（Launcher、TestEngine 等），被 Iceberg 项目用于运行单元测试和集成测试。本次升级涉及三个相关制品：`junit-platform-launcher`、`junit-platform-suite-api`、`junit-platform-suite-engine`。

本次从 1.13.4 升至 1.14.1，属于次版本（minor）升级，可能引入新功能和 API 变更。值得注意的是，后续提交 2818 将 JUnit Jupiter（junit 本身）从 5.13.4 升至 5.14.1，本提交的 junit-platform 1.14.1 与之配套，保持 JUnit 平台层与 Jupiter API 版本的一致性。

## 如何达成设计目的

通过修改 `gradle/libs.versions.toml` 中的 `junit-platform` 版本属性，从 `1.13.4` 更新为 `1.14.1`。该属性被 Gradle 版本目录（Version Catalog）引用，统一管理 JUnit Platform 相关依赖的版本。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：升级 JUnit Platform 版本属性。

**工作逻辑**：将 `junit-platform = "1.13.4"` 改为 `junit-platform = "1.14.1"`。此属性在版本目录中被 `junit-platform-launcher`、`junit-platform-suite-api`、`junit-platform-suite-engine` 等依赖引用，一次修改即可统一升级所有相关制品。

## 总结

将 JUnit Platform 从 1.13.4 升级到 1.14.1，属于次版本升级，可能与 JUnit 5.14 的 API 变更配套。这是 Dependabot 批量依赖升级（2811-2819）的一部分，与提交 2818（junit 5.13.4 -> 5.14.1）共同保持 JUnit 测试框架版本的一致性。
