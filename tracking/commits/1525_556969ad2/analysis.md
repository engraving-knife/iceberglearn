# 提交 1525 556969ad2 分析

## 提交信息
- 哈希：556969ad2a5e69b0d2403a55bd7f33794a9a61d3
- 日期：2024-12-22（Sun Dec 22 22:05:51 2024 +0100）
- 作者：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- 消息：Build: Bump guava from 33.3.1-jre to 33.4.0-jre (#11850)

## 总体目的

本提交由 Dependabot 自动生成，目的是将 Iceberg 依赖的 Google Guava 从 33.3.1-jre 升级到 33.4.0-jre。Guava 是 Google 开发的 Java 核心库，提供集合、缓存、并发、字符串处理、I/O、哈希等大量实用工具，是 Java 生态中最广泛使用的工具库之一。Iceberg 在核心模块中大量使用 Guava 的工具类（如 `Lists`、`Maps`、`ImmutableList`、`Preconditions`、`Splitter` 等）。

这是一次 minor 版本升级（33.3.1 → 33.4.0），属于 semver 语义下"可能新增功能但保持向后兼容"的更新。Dependabot 在 PR 描述中说明本次同时更新两个相关制品：`com.google.guava:guava`（主库）和 `com.google.guava:guava-testlib`（测试工具库），两者必须保持同版本，因为 testlib 提供的测试工具（如 `EqualsTester`、`TestCase` 等）与主库的 API 紧密耦合。

Guava 的 minor 版本通常包含新工具方法、性能改进与 bug 修复，一般不破坏现有 API（除非涉及 `@Beta` 标注的实验性 API，这些可能被移除或调整）。Iceberg 在使用 Guava 时对 `@Beta` API 应谨慎，但本次升级由 Dependabot 自动完成并通过 CI，说明未触发破坏性变更。

## 如何达成设计目的

Dependabot 修改单一文件 `gradle/libs.versions.toml`，将版本变量 `guava` 从 `33.3.1-jre` 改为 `33.4.0-jre`。版本目录中 `guava` 变量被 `guava` 与 `guava-testlib` 两个依赖引用，因此一处修改即可同步升级两者，保证版本一致。

### 修改详情

#### `gradle/libs.versions.toml`

**修改目的**：将 Guava 版本升级到 33.4.0-jre，同步主库与测试库。

**工作逻辑**：该文件 `[versions]` 区块中声明：

```
-guava = "33.3.1-jre"
+guava = "33.4.0-jre"
```

共 1 行变更（1 增 1 删）。版本后缀 `-jre` 表示 JRE 版本（针对 Java 8+），与之相对的 `-android` 版本面向 Android。Iceberg 选用 `-jre` 是因为其目标运行环境是服务端 JVM。版本目录中通常会有如下引用：

```toml
[libraries]
guava = { module = "com.google.guava:guava", version.ref = "guava" }
guava-testlib = { module = "com.google.guava:guava-testlib", version.ref = "guava" }
```

两者通过 `version.ref = "guava"` 共享同一版本变量，因此修改 `guava = "33.4.0-jre"` 会让两个制品同时升级，这正是 Dependabot PR 描述中"Updates com.google.guava:guava ... Updates com.google.guava:guava-testlib ..."的由来。

## 小结

- **成效**：Guava 升级到 33.4.0-jre，主库与测试库同步更新，获得新工具方法、性能改进与 bug 修复；保持依赖新鲜度。
- **影响范围**：仅 `gradle/libs.versions.toml` 1 个文件、1 行改动；通过版本目录机制全局生效，影响所有使用 Guava 的模块（几乎所有核心模块），属于向后兼容的 minor 升级。需注意是否有 `@Beta` API 被移除，但 CI 通过表明无破坏。
- **回迁到 1.4.x 的注意事项**：Guava 是核心依赖，minor 升级可能带来稳定性与功能改进。1.4.x 维护分支若仍在维护期，**可回迁**以获取改进，但需运行完整测试套件验证 `@Beta` API 兼容性。若 1.4.x 已接近发布末期，为降低风险可仅回迁 patch（但本次是 minor 升级，无对应 patch），因此需评估收益与风险。
