# 提交 0802：Bump org.assertj:assertj-core from 3.25.3 to 3.26.0

## 提交信息

| 字段 | 值 |
|------|------|
| 序号 | 0802 |
| 完整哈希 | 1837c81758344482086a82432e498fd2d509b48b |
| 短哈希 | 1837c8175 |
| 日期 | 2024-06-03 08:03:11 +0200 |
| 作者 | dependabot[bot] |
| 提交说明 | Build: Bump org.assertj:assertj-core from 3.25.3 to 3.26.0 (#10416) |
| PR/Issue | #10416 |
| 修改文件数 | 1 |
| 增/删行数 | +1 / -1 |

## 总体目的

这是由 GitHub Dependabot 自动生成的依赖升级提交，用于将 Java 测试断言库 `org.assertj:assertj-core` 从 3.25.3 升级到 3.26.0。AssertJ 是 Iceberg 项目测试代码中广泛使用的流式断言库，提供比 JUnit 原生断言更丰富的可读性与错误信息。本次升级属于 minor 版本升级，目的是跟进上游新特性与缺陷修复，保持测试基础设施的现代性与稳定性。

## 如何达成设计目的

Iceberg 使用 Gradle 的版本目录（Version Catalog）机制集中管理依赖版本，所有依赖版本声明在 `gradle/libs.versions.toml` 中。Dependabot 识别到 `assertj-core` 别名对应的版本 `3.25.3` 存在更新的 `3.26.0`，于是直接修改版本目录中的版本字符串，由各模块通过 `libs.assertj.core`（或类似别名引用）自动传递应用，无需修改各模块的 `build.gradle` 文件。提交信息中标注依赖类型为 `direct:production`、更新类型为 `version-update:semver-minor`，意味着属于次要版本升级，需留意 AssertJ 3.26.0 的 release notes 中是否含有 API 行为变化。

AssertJ 3.26.0 是一个 minor 升级，通常新增断言方法与改进错误信息，但不会移除既有 API，因此对 Iceberg 现有测试代码兼容性良好。

## 修改详情

### `gradle/libs.versions.toml`

Gradle 版本目录文件，集中声明项目所有第三方依赖的版本。本次修改仅一行：

```diff
-assertj-core = "3.25.3"
+assertj-core = "3.26.0"
```

将 `assertj-core` 版本别名从 `3.25.3` 升级到 `3.26.0`。该别名在版本目录的 `[libraries]` 段被引用为 `assertj-core = { module = "org.assertj:assertj-core", version.ref = "assertj-core" }`（或类似形式），各测试模块通过 `testImplementation(libs.assertj.core)` 引入，因此一处修改即可全局生效。

## 小结

- **成效**：测试断言库升级到 3.26.0，获得上游 minor 版本的新特性与修复，保持测试基础设施与时俱进。
- **影响范围**：仅影响测试编译与测试运行，不影响 Iceberg 主产物（jar）的运行时行为与对外 API。
- **回迁到 1.4.x 分支的注意事项**：回迁风险较低。需确认 1.4.x 分支的 `gradle/libs.versions.toml` 中 `assertj-core` 当前版本；若 1.4.x 已有独立的版本锁定或补丁式调整，应核对 3.26.0 是否与该分支的 JDK 版本及测试代码兼容。AssertJ 3.26.x 要求 JDK 8+，与 Iceberg 1.4.x 的 JDK 基线兼容，通常可直接 cherry-pick。建议回迁后运行完整测试套件验证无断言行为差异。
