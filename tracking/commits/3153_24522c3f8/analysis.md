# 提交 3153：Build: Bump org.assertj:assertj-core from 3.27.6 to 3.27.7 (#15132)

## 提交信息

- **序号**：3153 / 4088
- **哈希**：24522c3f8d90632244ca4d1c112c976a971925ca
- **短哈希**：24522c3f8
- **日期**：2026-01-25 09:31:10 -0800
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.assertj:assertj-core from 3.27.6 to 3.27.7 (#15132)
- **PR/Issue**：#15132

## 总体目的

本提交是 Dependabot 自动发起的依赖升级，将测试断言库 `org.assertj:assertj-core` 从 `3.27.6` 升级到 `3.27.7`。AssertJ 是 Iceberg 测试体系中广泛使用的流式断言库（如 `assertThat(...)`、`hasNext()`、`isExhausted()`、`containsKey()` 等），几乎所有模块的单元测试与集成测试都依赖它。该依赖版本声明在 Gradle 版本目录 `gradle/libs.versions.toml` 的 `assertj-core` 属性中，供各模块测试配置统一引用。

本次升级为补丁版本（patch）升级（`version-update:semver-patch`，3.27.6 → 3.27.7），按语义化版本约定仅包含向后兼容的缺陷修复，预期不影响现有测试用例的断言行为。Dependabot 在提交信息中附带了上游 release notes 与 commits 对比链接。由于 assertj-core 仅作为测试依赖使用，升级不会影响 Iceberg 发布产物。

## 如何达成设计目的

直接在 `gradle/libs.versions.toml` 中将 `assertj-core` 属性从 `3.27.6` 改为 `3.27.7`，各模块测试配置通过版本目录引用自动跟随升级，无需逐模块修改。

## 修改详情

### `gradle/libs.versions.toml` (+1/-1 lines)

**修改目的**：将 assertj-core 版本从 3.27.6 提升到 3.27.7。

**工作逻辑**：将 `assertj-core = "3.27.6"` 修改为 `assertj-core = "3.27.7"`。该属性被各模块测试配置引用，AssertJ 是 Iceberg 测试中普遍使用的流式断言库。升级到 3.27.7 获取上游补丁修复，属 semver-patch 级别，预期向后兼容；由于仅作为测试依赖，不影响发布产物。

## 总结

本提交通过将测试断言库 assertj-core 从 3.27.6 升级到 3.27.7，获取上游补丁修复，保持测试依赖的及时更新；作为 semver-patch 升级且仅用于测试，对现有断言行为与发布产物均无破坏性影响。
