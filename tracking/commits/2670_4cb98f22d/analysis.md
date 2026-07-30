# 提交 2670：Build: Bump org.assertj:assertj-core from 3.27.4 to 3.27.5 (#14130)

## 提交信息

- **序号**：2670 / 4088
- **哈希**：4cb98f22dc225fe217945ce1fd303d55e86e3597
- **短哈希**：4cb98f22d
- **日期**：2025-09-20 22:33:48 -0700
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.assertj:assertj-core from 3.27.4 to 3.27.5 (#14130)
- **PR/Issue**：#14130

## 总体目的

本提交由 Dependabot 自动生成，将 Apache Iceberg 项目构建依赖中的 AssertJ Core 测试断言库从 3.27.4 升级到 3.27.5。AssertJ 是 Java 生态中广泛使用的流式断言库，Iceberg 在测试代码中大量使用它来编写可读性强的断言。

3.27.4 到 3.27.5 属于 patch 版本升级，按照语义化版本规范，这意味着升级仅包含 bug 修复和小的内部改进，不会引入破坏性变更。Dependabot 将此依赖标记为 `direct:production` 类型的 `version-update:semver-patch` 更新，定期保持测试依赖的最新状态有助于及时获得 bug 修复并减少技术债。

## 如何达成设计目的

通过修改 Gradle 版本目录文件 `gradle/libs.versions.toml` 中 `assertj-core` 的版本声明，从 `3.27.4` 改为 `3.27.5`。由于 Iceberg 使用 Gradle 版本目录（Version Catalog）集中管理依赖版本，所有引用该版本的模块都会自动应用新版本，无需修改各模块的 build.gradle 文件。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 assertj-core 依赖版本从 3.27.4 升级到 3.27.5。

**工作逻辑**：在版本目录文件的依赖版本声明区域，将 `assertj-core = "3.27.4"` 一行修改为 `assertj-core = "3.27.5"`。其余所有条目保持不变。这是 Dependabot 自动化依赖升级的标准做法，单点修改版本目录即可同步全项目。

## 总结

这是一次常规的依赖版本升级，将 AssertJ Core 从 3.27.4 升级到 3.27.5，属于 patch 级别的 bug 修复更新。修改仅涉及一行版本目录配置，风险极低，有助于保持测试依赖的最新状态。Iceberg 项目通过 Dependabot 自动化管理这类小版本升级，以减少人工维护成本并及时获取上游修复。
