# 提交 0834：Build: Bump org.scala-lang.modules:scala-collection-compat_2.13 (#10195)

## 提交信息
- **序号**：0834 / 4088
- **哈希**：6c76a3a9a7a41db0451992625d54e0109ff6ba53
- **短哈希**：6c76a3a9a
- **日期**：2024-06-15 21:10:25 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.scala-lang.modules:scala-collection-compat_2.13 (#10195)
- **PR/Issue**：#10195

## 总体目的

本提交由 Dependabot 自动生成，将 Scala 集合兼容库 `org.scala-lang.modules:scala-collection-compat_2.13` 从 2.11.0 升级到 2.12.0。这是一次次版本（semver-minor）升级，属于依赖维护的常规操作，目的是纳入上游 2.12.0 版本中的新特性与改进，保持依赖处于较新的稳定状态。

`scala-collection-compat` 是 Scala 官方提供的跨版本兼容库，用于在 Scala 2.12 和 2.13 之间平滑迁移集合 API（例如提供 `scala-2.13` 风格的集合 API 给 2.12 使用，或辅助 2.13 代码兼容旧 API）。Iceberg 的 Spark、Flink 等集成模块使用 Scala，需要该库来处理不同 Scala 版本间的集合 API 差异。次版本升级通常向后兼容，风险较低。Dependabot 在 PR 描述中附带了上游 release notes 与 commits 对比链接。

## 如何达成设计目的

通过 Gradle 版本目录（Version Catalog）机制集中管理依赖版本。本提交在 `gradle/libs.versions.toml` 中将 `scala-collection-compat` 的版本别名从 `"2.11.0"` 改为 `"2.12.0"`，所有通过版本目录引用该别名的模块在下次构建时自动拉取新版本，无需修改各模块的 `build.gradle`。

## 修改详情

### `gradle/libs.versions.toml`
**修改目的**：将 scala-collection-compat 库版本从 2.11.0 升级到 2.12.0。
**工作逻辑**：在版本目录的 `[versions]` 段，将 `scala-collection-compat = "2.11.0"` 改为 `scala-collection-compat = "2.12.0"`。该别名在 `[libraries]` 段被引用，版本号变更后所有引用处自动生效，影响使用 Scala 2.13 的 Spark/Flink 等集成模块。

## 小结
- **成效**：将 Scala 集合兼容库升级到 2.12.0 次版本，纳入上游改进与新特性，依赖保持更新。
- **影响范围**：影响使用 Scala 的 Spark、Flink 等集成模块的编译与运行时依赖。次版本升级，按 semver 约定向后兼容。
- **回迁注意事项**：回迁到 1.4.x 风险较低，仅需修改 `gradle/libs.versions.toml` 中 `scala-collection-compat` 版本号。由于是次版本升级，建议回迁后对 Spark/Flink 集成模块做编译与基础测试验证，确认无 API 不兼容情况。需注意 1.4.x 支持的 Scala 版本范围与 main 是否一致。
