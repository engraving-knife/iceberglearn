# 提交 2192：Build: Bump guava from 33.4.7-jre to 33.4.8-jre (#12851)

## 提交信息

- **序号**：2192 / 4088
- **哈希**：7537c3c3a2a6491abcf0c3ef58cc4d5dc6ac4bae
- **短哈希**：7537c3c3a
- **日期**：2025-06-02 22:01:40 +0200
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump guava from 33.4.7-jre to 33.4.8-jre (#12851)
- **PR/Issue**：#12851

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Google Guava 库从 33.4.7-jre 升级到 33.4.8-jre。Guava 是 Google 提供的核心 Java 工具库，Iceberg 项目广泛使用其集合、缓存、并发、字符串处理等工具。这是一次补丁版本（patch）升级，属于小版本维护更新，通常包含 bug 修复和小的改进，不涉及破坏性变更。Dependabot 自动检测到新版本可用后提交了此升级，涵盖 guava 主库和 guava-testlib 两个依赖。

## 如何达成设计目的

- 在 `gradle/libs.versions.toml` 版本目录文件中，将 guava 的版本号从 `33.4.7-jre` 修改为 `33.4.8-jre`。
- 该版本号变更会自动应用于所有引用 guava 版本的依赖项（guava、guava-testlib）。

## 修改详情

### `gradle/libs.versions.toml` (修改, +1/-1 lines)

**修改目的**：升级 guava 依赖版本。

**工作逻辑**：将版本目录中 guava 的版本定义从 `33.4.7-jre` 改为 `33.4.8-jre`，这是单个版本号字符串的替换。

## 总结

这是一次常规的依赖版本升级（33.4.7-jre → 33.4.8-jre），由 Dependabot 自动完成，属于补丁级维护更新，用于获取 Guava 上游的 bug 修复和小改进，对项目功能无影响。
