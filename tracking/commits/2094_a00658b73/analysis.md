# 提交 2094：Docs: Mark Spark 3.3 as End-of-Life since 1.9 (#12989)

## 提交信息

- **序号**：2094 / 4088
- **哈希**：a00658b7337837847e5ec657b2f4736094077afe
- **短哈希**：a00658b73
- **日期**：2025-05-07 08:33:15 -0600
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Mark Spark 3.3 as End-of-Life since 1.9 (#12989)
- **PR/Issue**：#12989

## 总体目的

Iceberg 1.9.0 版本起，正式结束对 Spark 3.3 的支持。Spark 3.3 上游已进入维护尾声，Iceberg 社区决定把有限的维护精力聚焦在 Spark 3.4 / 3.5 等仍活跃的版本上。本次提交更新官方文档，把多引擎支持表中 Spark 3.3 的状态从 `Deprecated` 改为 `End of Life`，并把最后一个提供 runtime jar 的 Iceberg 版本固定为 `1.8.1`（即 1.9.0 起不再发布 Spark 3.3 的 runtime jar）；同时从发布页面 `releases.md` 中移除 Spark 3.3 的 Scala 2.12 / 2.13 runtime jar 下载链接，避免用户在 1.9.0 及以后版本中误以为仍有 Spark 3.3 支持。

## 如何达成设计目的

1. 在 `site/docs/multi-engine-support.md` 的引擎支持表中，把 Spark 3.3 行的状态列从 `Deprecated` 改为 `End of Life`，把"最后支持的 Iceberg 版本"列从动态的 `{{ icebergVersion }}` 改为固定的 `1.8.1`，并把 runtime jar 链接里的 `{{ icebergVersion }}` 占位符替换为 `1.8.1`。
2. 在 `site/docs/releases.md` 中删除 Spark 3.3 的 Scala 2.12 与 2.13 两条 runtime jar 下载条目，使 1.9.0 及以后版本的发布列表不再包含 Spark 3.3。

## 修改详情

### `site/docs/multi-engine-support.md` (修改, +1/-1 lines)

**修改目的**：在引擎支持表中标记 Spark 3.3 为 End-of-Life，并固定最后支持版本为 1.8.1。

**工作逻辑**：
将 Spark 3.3 一行的状态、最后版本与 jar 链接由 `Deprecated / {{ icebergVersion }} / ...{{ icebergVersion }}...` 改为 `End of Life / 1.8.1 / ...1.8.1...`。其余行（Spark 3.0/3.1/3.2 已 EOL，Spark 3.4/3.5 Maintained）保持不变。

### `site/docs/releases.md` (修改, +0/-2 lines)

**修改目的**：从最新版本的 runtime jar 下载列表中移除 Spark 3.3。

**工作逻辑**：
删除以下两行下载链接：
- `{{ icebergVersion }} Spark 3.3_with Scala 2.12 runtime Jar`
- `{{ icebergVersion }} Spark 3.3_with Scala 2.13 runtime Jar`

使 1.9.0 及以后版本的发布页面只保留 Spark 3.4 / 3.5 与 Flink 各版本的 runtime jar。

## 总结

本次提交是 1.9.0 版本发布过程中的文档维护：把 Spark 3.3 标记为 End-of-Life（最后支持版本 1.8.1），并从发布页移除其 runtime jar 下载链接，向用户明确传达 1.9.0 起不再支持 Spark 3.3 的信息。改动仅涉及两处文档表格，无代码影响。
