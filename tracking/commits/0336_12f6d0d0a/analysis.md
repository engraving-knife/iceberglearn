# 提交 0336：Build: Bump org.assertj:assertj-core from 3.24.2 to 3.25.1 (#9427)

## 提交信息

- **序号**：0336
- **哈希**：12f6d0d0a35522915f5a8da73973403fe12cb01c
- **短哈希**：12f6d0d0a
- **日期**：2024-01-08 09:51:52 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.assertj:assertj-core from 3.24.2 to 3.25.1 (#9427)
- **PR/Issue**：#9427

## 总体目的

这个提交由 Dependabot 自动生成，把 Iceberg 测试体系依赖的 AssertJ 断言库 `org.assertj:assertj-core` 从 `3.24.2` 升级到 `3.25.1`，跨 1 个 minor 版本（3.25.0 + 3.25.1 两个补丁版本），属于 `version-update:semver-minor` 级别的依赖更新。

AssertJ 是 Java 生态中流式断言库的事实标准，Iceberg 在大量单元/集成测试中使用 `assertThat(...).isEqualTo(...)`、`assertThatThrownBy(...)`、`containsExactlyInAnyOrderElementsOf(...)` 等断言 API 验证行为。`assertj-core` 是 AssertJ 主模块（针对 JDK 自带类型与基本断言流），其版本升级会直接影响整个 Iceberg 测试套件的断言行为。AssertJ 3.25.x 系列的主要变化包括：将断言引擎从内部维护逐步切换到基于 Eclipse collections / Spring 等更现代的实现、对 `InstanceOfAssertFactories` 进行增强、修复若干 NPE 与边界 bug、并新增对 Java 21 类型与 `Stream` 断言的更好支持。3.25.0 → 3.25.1 是该 minor 系列的第一个补丁版本，主要修复 3.25.0 引入的回归问题。

由于 AssertJ 严格遵循 semver 兼容性承诺，minor 版本升级不包含破坏性 API 变更（仅新增 API 与修复 bug），故 Dependabot 把它归类为 `version-update:semver-minor`，风险较低。Iceberg 维护者合并此 PR 即表示认可升级在 Iceberg 测试套件范围内的兼容性。该升级也帮助 Iceberg 测试体系跟进 Java 生态主流断言库的最新稳定版本，避免长期停留在旧版本导致后续升级跨度变大。

## 如何达成设计目的

改动只涉及一处版本常量：在 Gradle 版本目录文件 `gradle/libs.versions.toml` 中，将 `assertj-core = "3.24.2"` 改为 `assertj-core = "3.25.1"`。所有通过 `libs.assertj.core`（或类似别名）引用该版本的模块（如 `core`、`api`、`spark/v3.x/spark`、`flink/v1.17/flink` 等所有带测试的子模块）会自动解析到新版本，无需逐个修改各模块的 `build.gradle`。这是 Gradle 版本目录带来的核心价值——单一来源（single source of truth）管理依赖版本。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 `org.assertj:assertj-core` 的版本从 3.24.2 升级到 3.25.1。

**工作逻辑**：`gradle/libs.versions.toml` 是 Gradle 版本目录（Version Catalog），集中声明所有依赖版本。改动位于版本声明区的字母序位置（约第 28 行附近，处于 `avro = "1.11.3"` 与 `awaitility = "4.2.0"` 之间），原行 `assertj-core = "3.24.2"` 被改为 `assertj-core = "3.25.1"`。该版本常量被各模块的测试配置通过 `testImplementation(libs.assertj.core)` 引用，控制编译期与运行期解析到的 AssertJ artifact 版本。升级后，所有测试运行时使用的 `assertj-core` jar 会自动切换到 3.25.1，新增的断言 API（如对 `Optional`、`Stream` 的增强断言）可在新测试代码中使用，3.25.0 引入的若干 bug 修复也会对现有测试生效。

## 小结

该提交由 Dependabot 自动将 `org.assertj:assertj-core` 从 3.24.2 升级到 3.25.1，使 Iceberg 测试套件跟进 AssertJ 最新稳定版本，获取 3.25.x 系列的 bug 修复、API 增强与 Java 21 类型支持改进。改动仅一处版本常量，依赖 Gradle 版本目录机制自动传递到所有测试模块，属于低风险、低成本的依赖维护工作。
