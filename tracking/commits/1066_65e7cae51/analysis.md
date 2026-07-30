# 提交 1066：Build: Bump guava from 33.2.1-jre to 33.3.0-jre (#10960)

## 提交信息

- **序号**：1066 / 4088
- **哈希**：65e7cae512206595d6bb50505e40e3758caa6a83
- **短哈希**：65e7cae51
- **日期**：2024-08-18 12:44:58 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump guava from 33.2.1-jre to 33.3.0-jre (#10960)
- **PR/Issue**：#10960

## 总体目的

这是一次由 GitHub Dependabot 自动发起的依赖升级，目标是把 Iceberg 仓库所依赖的 Google Guava 库（以及配套的 `guava-testlib`）从 `33.2.1-jre` 升级到 `33.3.0-jre`。Dependabot 会定期扫描仓库中声明在 Gradle version catalog（`gradle/libs.versions.toml`）里的依赖，发现新版本后自动提交 PR 触发 CI 验证。

Guava 是 Iceberg 大量使用的核心通用库（集合、缓存、I/O、并发原语、`Files`、`ImmutableList`/`ImmutableMap` 等），保持其版本更新有助于获取上游 bug 修复、性能改进与潜在的安全补丁。本次升级属于 SemVer 中的 minor 升级（33.2.x → 33.3.x），按 Guava 的兼容性承诺，对 API 是二进制兼容的，理论上不会破坏现有调用方。

## 如何达成设计目的

实现方式非常直接：仅在 Gradle version catalog 文件 `gradle/libs.versions.toml` 中，把 `guava` 这一行的版本字符串从 `"33.2.1-jre"` 改为 `"33.3.0-jre"`。由于该 catalog 中只声明了一个 `guava` 版本变量，所有依赖 Guava 的子项目（通过 `libs.guava` 引用）都会统一使用新版本，`guava-testlib` 也通过同一变量取版本，自动随之升级，无需在多个 build.gradle 中重复修改。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 Guava 版本号统一从 `33.2.1-jre` 升级到 `33.3.0-jre`。

**工作逻辑**：

```diff
-guava = "33.2.1-jre"
+guava = "33.3.0-jre"
```

该变量同时被 `com.google.guava:guava` 与 `com.google.guava:guava-testlib` 两个依赖引用，因此一行修改即可同时升级主库与测试库，确保二者版本一致。

## 小结

- **成效**：把 Guava 与 guava-testlib 从 33.2.1-jre 升级到 33.3.0-jre，获取上游 minor 版本带来的改进与修复。
- **影响范围**：仅修改 `gradle/libs.versions.toml` 一个文件，1 行变更；影响所有使用 Guava 的子模块（core、aws、gcp、azure、flink、spark、hive 等），但属于二进制兼容的 minor 升级，预期无破坏性影响。
- **回迁到 1.4.x 的注意事项**：Dependabot 类的依赖升级回迁到 1.4.x 通常风险很低，可以直接 cherry-pick；但需注意 1.4.x 分支可能存在不同的 Guava 版本基线或额外的兼容性约束（例如某些下游模块对 Guava 版本有更严格的上下界），cherry-pick 前应确认 1.4.x CI 上的相关测试通过。如果 1.4.x 已经独立维护了一份依赖版本策略，也可以选择不回迁，由 1.4.x 自身的 Dependabot 流程处理。
