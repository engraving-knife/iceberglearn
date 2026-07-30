# 提交 0502：Build: Bump org.assertj:assertj-core from 3.25.2 to 3.25.3 (#9706)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0502 |
| 完整哈希 | 8991c1a91425bc864772abec9e0af37421b0616b |
| 短哈希 | 8991c1a91 |
| 日期 | 2024-02-14（Wed Feb 14 21:14:26 2024 +0100） |
| 作者 | dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com> |
| 说明 | Build: Bump org.assertj:assertj-core from 3.25.2 to 3.25.3 (#9706) |
| PR | #9706 |
| 依赖类型 | direct:production |
| 更新类型 | version-update:semver-patch（补丁版本升级） |

提交统计：1 个文件修改，1 行新增，1 行删除。

涉及文件：`gradle/libs.versions.toml`

## 总体目的

本提交由 Dependabot 自动生成，将 Iceberg 全模块测试所用的 AssertJ 核心断言库从 `3.25.2` 升级到 `3.25.3`，跨 1 个补丁版本。AssertJ 是 Iceberg 测试体系的主力流式断言库，提供 `assertThat(...)` 风格的丰富断言 API（针对集合、异常、可选值、路径、时间等），在根 `build.gradle` 的 `subprojects` 块（约第 203-213 行）以 `testImplementation libs.assertj.core` 形式声明，因此该依赖被所有子模块的测试类路径继承，是覆盖面最广的测试依赖之一。

`3.25.2 → 3.25.3` 属补丁级别（`version-update:semver-patch`），按语义化版本约定为向后兼容更新，通常包含断言行为缺陷修复、新断言重载或对 JDK 新版本的兼容性微调，不引入破坏性 API 变更。由于 AssertJ 仅出现在测试类路径（`testImplementation`），不进入任何发布产物，对 Iceberg 公共 API 与运行时行为零影响。同批次（2024-02-14）合入的还有 0501（Nessie 0.77.1）、0503（tez010）、0504（awssdk-bom）、0505（arrow）共五次依赖升级，属 Dependabot 日常批次推进，本提交是其中最简单的一行版本号提升。

## 如何达成设计目的

实现路径是单点修改：在 `gradle/libs.versions.toml` 的版本声明区把 `assertj-core = "3.25.2"` 改为 `assertj-core = "3.25.3"`，保持与同文件所有版本声明一致的 `key = "version"` 风格。库定义区 `assertj-core = { module = "org.assertj:assertj-core", version.ref = "assertj-core" }` 通过 `version.ref` 引用该变量，无需改动；根 `build.gradle` 的 `subprojects` 依赖块通过 `libs.assertj.core` 访问器引用，亦无需改动。一处版本号变更即把全部子模块测试类路径上的 AssertJ 统一升到 3.25.3。

## 修改详情

### `gradle/libs.versions.toml`

修改目的：把 AssertJ 核心库的锁定版本从 `3.25.2` 提升到 `3.25.3`。

工作逻辑：

- 版本声明区第 26 行附近：`assertj-core = "3.25.2"` → `assertj-core = "3.25.3"`。
- 该版本变量被库定义区第 153 行附近的 `assertj-core = { module = "org.assertj:assertj-core", version.ref = "assertj-core" }` 引用，后者又被根 `build.gradle` 第 212 行 `subprojects` 块中的 `testImplementation libs.assertj.core` 消费。由于该声明位于 `subprojects`（第 133 行起），Iceberg 全部子模块（`:iceberg-core`、`:iceberg-api`、`:iceberg-aws`、`:iceberg-nessie` 等）的测试类路径均自动获得 AssertJ，本次升级随之在所有模块测试中生效。
- 升级为补丁级别、向后兼容，无需调整任何测试源码中的断言写法；3.25.x 系列内 `assertThat` 链式 API 稳定，因此全量测试无需配合改动。

## 小结

本提交是 Dependabot 触发的测试断言库补丁升级：将 `gradle/libs.versions.toml` 中 `assertj-core` 由 `3.25.2` 升至 `3.25.3`。AssertJ 经根 `build.gradle` 的 `subprojects` 块以 `testImplementation` 形式被全部子模块继承，是 Iceberg 测试体系的基础断言设施，但不进入发布产物。升级属补丁级别、向后兼容，对 Iceberg 自身代码与公共 API 零影响，回迁 1.4.x 风险极低，仅需同步该一行版本号。
