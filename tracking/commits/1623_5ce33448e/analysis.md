# 提交 1623：Remove `slf4j-api` reference in `LICENSE` (#12052)

## 提交信息

- **序号**：1623 / 4088
- **哈希**：5ce33448ebfbc604219ee3e216b69edf4df7d029
- **短哈希**：5ce33448e
- **日期**：2025-01-23（Thu Jan 23 14:52:52 2025 +0100）
- **作者**：JB Onofré <jbonofre@apache.org>
- **提交说明**：Remove `slf4j-api` reference in `LICENSE` (#12052)
- **PR/Issue**：#12052

## 总体目的

Iceberg 的云存储 bundle 模块（`aws-bundle`、`azure-bundle`、`gcp-bundle`）是便利性 uber-jar，把各云 SDK 的依赖及其传递依赖打包成一个 fat jar 供用户直接使用。按 Apache 发行政策，这些 bundle 的 LICENSE 文件需列出所有内含第三方依赖的许可信息。

此前这三个 bundle 的 LICENSE 文件中都列有 `org.slf4j:slf4j-api:1.7.36`（MIT License）的条目。但 `slf4j-api 1.7.36` 已不再包含在这些 bundle 的实际依赖树中——可能是因为上游 SDK 升级后不再传递引入该旧版 SLF4J（SLF4J 1.7.x 已被 2.x 取代），或该依赖被显式排除/relocated。

LICENSE 文件列出实际不存在的依赖虽然不会造成合规风险（多列不会违反任何许可），但有损准确性：

1. **误导用户**：用户检查 LICENSE 时会误以为 bundle 包含 slf4j-api 1.7.36，实际并未包含；
2. **审计噪音**：Apache 发行审计工具（如 `apache-rat`、`license-checker`）可能因 LICENSE 与实际依赖不一致而产生告警；
3. **维护负担**：后续维护者可能误以为该依赖仍在，做出错误的依赖决策。

本提交从三个 bundle 的 LICENSE 文件中移除 `slf4j-api 1.7.36` 条目，使 LICENSE 与实际依赖树一致。

## 如何达成设计目的

逐文件删除 `slf4j-api` 条目（含分隔线与 Group/Name/Version/Project URL/License 四行，加上前后分隔线共 6-8 行）。三个文件各删除一处，共 18 行。

## 修改详情

### `aws-bundle/LICENSE`（修改，-6 行）

**修改目的**：移除不再包含的 slf4j-api 依赖条目。

**工作逻辑**：删除位于 CC0 许可条目与 `software.amazon.awssdk:annotations` 条目之间的 `org.slf4j:slf4j-api:1.7.36` 块（含分隔线 `---`、Group/Name/Version、Project URL、License 行）。

### `azure-bundle/LICENSE`（修改，-6 行）

**修改目的**：同上。

**工作逻辑**：删除文件末尾 `org.reactivestreams:reactive-streams` 条目之后的 `org.slf4j:slf4j-api:1.7.36` 块（含分隔线）。

### `gcp-bundle/LICENSE`（修改，-6 行）

**修改目的**：同上。

**工作逻辑**：删除位于 Apache 2.0 许可条目与 `org.threeten:threetenbp` 条目之间的 `org.slf4j:slf4j-api:1.7.36` 块（含分隔线）。

## 小结

- **成效**：从三个云存储 bundle 模块的 LICENSE 文件中移除已不存在的 `slf4j-api 1.7.36` 依赖条目，使 LICENSE 与实际依赖树一致，提升发行合规准确性，减少审计噪音与用户误解。
- **影响范围**：仅 `aws-bundle/LICENSE`、`azure-bundle/LICENSE`、`gcp-bundle/LICENSE` 三个法律文件，各删 6 行，无代码与功能影响。
- **回迁到 1.4.x 的注意事项**：回迁零风险，纯 LICENSE 文本删除。可直接 cherry-pick。需确认 1.4.x 的 bundle 模块是否仍有 slf4j-api 1.7.36 在依赖树中——若 1.4.x 的依赖版本与 main 不同（如 1.4.x 仍包含 slf4j-api 1.7.36），则不应回迁此删除（LICENSE 需保留以保持与实际依赖一致）。建议回迁前用 `./gradlew :iceberg-aws-bundle:dependencies` 等命令验证 1.4.x 的 bundle 是否仍包含 slf4j-api。若 1.4.x 已升级依赖使 slf4j-api 1.7.36 不再存在，则可安全回迁。
