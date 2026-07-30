# 提交 0931：Build: Bump org.assertj:assertj-core from 3.26.0 to 3.26.3 (#10698)

## 提交信息

- **序号**：0931 / 4088
- **哈希**：de4262ddc83fa2f7f0559bec294efbb9eb47e2b9
- **短哈希**：de4262ddc
- **日期**：2024-07-14 09:53:12 +0200
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.assertj:assertj-core from 3.26.0 to 3.26.3 (#10698)
- **PR/Issue**：#10698

## 总体目的

本提交由 dependabot 自动生成，将 Iceberg 构建中使用的 AssertJ（`org.assertj:assertj-core`）测试断言库从 3.26.0 升级到 3.26.3。AssertJ 是 Iceberg 测试代码中广泛使用的流式断言库。3.26.x 系列的 patch 版本升级通常包含 bug 修复与小改进，不引入破坏性 API 变更。定期升级测试依赖可以获取断言相关的修复，避免因断言库自身 bug 导致的误报或漏报，并保持与上游同步。

## 如何达成设计目的

实现方式极简：仅修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `assertj-core` 的版本字符串，由 `3.26.0` 改为 `3.26.3`。所有通过 `libs.assertj.core`（或类似引用）使用该依赖的模块会自动升级，无需修改各模块的 build.gradle。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：将 AssertJ 测试依赖从 3.26.0 升级到 3.26.3。

**工作逻辑**：把 `[versions]` 段中的 `assertj-core = "3.26.0"` 改为 `assertj-core = "3.26.3"`。下游所有引用 `assertj-core` 版本的依赖会随之升级。

## 小结

- **成效**：将 `org.assertj:assertj-core` 从 3.26.0 升级到 3.26.3，跟进上游 patch 版本。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行改动，+1/-1。仅影响测试 classpath，不影响生产代码。
- **回迁到 1.4.x 的注意事项**：属于测试依赖 patch 版本升级，回迁风险极低（semver patch，无破坏性变更）。若 1.4.x 已使用相同或更高版本则无需回迁；若 1.4.x 仍使用 3.26.0 或更早，可安全回迁以获取修复。回迁后建议运行测试套件验证断言行为一致。
