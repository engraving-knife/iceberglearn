# 提交 1048：Build: Bump org.awaitility:awaitility from 4.2.1 to 4.2.2 (#10912)

## 提交信息

- **序号**：1048 / 4088
- **哈希**：b4fcd401293aeb5b48b92467d7f038f415a6db9f
- **短哈希**：b4fcd4012
- **日期**：2024-08-12（Mon Aug 12 15:51:15 2024 +0200）
- **作者**：dependabot[bot] <49699333+dependabot[bot]@users.noreply.github.com>
- **提交说明**：Build: Bump org.awaitility:awaitility from 4.2.1 to 4.2.2 (#10912)
- **PR/Issue**：#10912

## 总体目的

`org.awaitility:awaitility` 是一个用于在测试中表达异步断言的 DSL 库（如 `await().atMost(...).until(...)`），Iceberg 的测试代码用它来等待异步条件成立。dependabot 检测到该依赖有新 patch 版本 4.2.2（旧版本 4.2.1），本提交是例行升级，获取该库的 patch 修复。

## 如何达成设计目的

通过修改 Gradle 版本目录中 `awaitility` 的版本声明，从 `4.2.1` 改为 `4.2.2`。该版本号被测试模块通过 `${libs.awaitility}` 引用。这是 patch 版本升级，API 完全兼容，无需改动测试代码。

## 修改详情

### `gradle/libs.versions.toml`

**修改目的**：把 `awaitility` 依赖从 4.2.1 升到 4.2.2。

**工作逻辑**：将 `awaitility = "4.2.1"` 改为 `awaitility = "4.2.2"`。该变量是 Iceberg 对 awaitility 测试库的统一版本声明。

## 小结

- **成效**：把 `org.awaitility:awaitility` 从 4.2.1 升级到 4.2.2，获取该测试库的 patch 修复。
- **影响范围**：仅 `gradle/libs.versions.toml` 一个文件，1 行修改。
- **回迁到 1.4.x 的注意事项**：测试库 patch 版本升级完全兼容，**回迁风险极低**，可按需回迁。1.4.x 若使用 awaitility 且固定在 4.2.1，回迁可获得 patch 修复；若 1.4.x 未使用该库或固定更老版本，可不动。
