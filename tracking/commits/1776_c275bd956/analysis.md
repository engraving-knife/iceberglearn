# 提交 1776：Build: Bump org.awaitility:awaitility from 4.2.2 to 4.3.0 (#12384)

## 提交信息

- **序号**：1776 / 4088
- **哈希**：c275bd956e34803b08bb4d31514fb95da32b1d34
- **短哈希**：c275bd956
- **日期**：2025-02-24 12:17:46 +0100
- **作者**：dependabot[bot]
- **提交说明**：Build: Bump org.awaitility:awaitility from 4.2.2 to 4.3.0 (#12384)
- **PR/Issue**：#12384

## 总体目的

这是由 Dependabot 自动生成的依赖升级提交，将 Awaitility 测试库从 4.2.2 版本升级到 4.3.0 版本。Awaitility 是一个用于异步测试的 Java 库，提供流畅的 DSL 来等待异步操作完成。此次升级为次版本升级（semver-minor），可能包含新功能和 API 改进。Iceberg 项目在集成测试中使用 Awaitility 来等待异步条件满足。

## 如何达成设计目的

提交通过更新 `gradle/libs.versions.toml` 文件中 awaitility 的版本号来完成升级。

## 修改详情

### `gradle/libs.versions.toml`（修改, +1/-1 lines）

**修改目的**：升级 awaitility 依赖版本。

**工作逻辑**：将 `awaitility = "4.2.2"` 修改为 `awaitility = "4.3.0"`。

## 小结

- **成效**：将 Awaitility 测试库升级到 4.3.0 次版本。
- **影响范围**：仅影响使用 Awaitility 的测试代码。次版本升级通常向后兼容，但需注意是否有新增的 API 变更。
- **回迁到 1.4.x 的注意事项**：低优先级回迁。测试库升级不影响生产代码。回迁时需确认 4.3.0 版本与 1.4.x 分支的测试代码兼容。无前置依赖。
