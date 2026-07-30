# 提交 1930：Docs: Fix Latest Iceberg Support version of Hive (#12640)

## 提交信息

- **序号**：1930 / 4088
- **哈希**：6e2eaf02f380ed8e3be7f58273d680b766897b18
- **短哈希**：6e2eaf02f
- **日期**：2025-03-27 20:20:13 +0100
- **作者**：Manu Zhang
- **提交说明**：Docs: Fix Latest Iceberg Support version of Hive (#12640)
- **PR/Issue**：#12640

## 总体目的

此提交修复 Iceberg 官方文档中 Hive 引擎支持矩阵里"Latest Iceberg Support"（最新支持的 Iceberg 版本）一列的错误数值。在多引擎支持文档 `site/docs/multi-engine-support.md` 中，Hive 2 与 Hive 3 的"Latest Iceberg Support"列原本使用占位变量 `{{ icebergVersion }}`（一个站点构建模板变量，会渲染为当前站点展示的 Iceberg 版本），但实际上 Hive 运行时 jar 的最新发布版本已固定为 1.7.2，使用 `{{ icebergVersion }}` 会导致文档显示与实际发布物不一致。

`{{ icebergVersion }}` 是一个站点模板变量，会在文档渲染时替换为当前 Iceberg 仓库的版本（例如在维护分支上可能是 1.8.x 或更高），而 Hive 的 `iceberg-hive-runtime` jar 实际只在 1.7.2 版本上发布，因此用模板变量会误导用户去寻找并不存在的更高版本的 Hive 运行时 jar。此提交将这两行的值改为硬编码的 `1.7.2`，与同表中"Latest Runtime Jar"列链接中的版本号保持一致。

## 如何达成设计目的

修改思路直接：在 `site/docs/multi-engine-support.md` 的 Hive 引擎支持表格中，将 Hive 2 与 Hive 3 两行的"Latest Iceberg Support"列由 `{{ icebergVersion }}` 替换为硬编码的 `1.7.2`，使文档显示的版本号与实际发布物及表格中"Latest Runtime Jar"链接指向的版本一致。

## 修改详情

### `site/docs/multi-engine-support.md` (修改, +2/-2 lines)

**修改目的**：修正 Hive 引擎支持矩阵中"Latest Iceberg Support"列的取值。

**工作逻辑**：在 Hive 引擎支持表格的两行中：
- Hive 2 行（推荐小版本 2.3.8，状态 Deprecated）：`Latest Iceberg Support` 从 `{{ icebergVersion }}` 改为 `1.7.2`。
- Hive 3 行（推荐小版本 3.1.2，状态 Deprecated）：`Latest Iceberg Support` 从 `{{ icebergVersion }}` 改为 `1.7.2`。

这样表格中"Latest Iceberg Support"列的值与同行的"Latest Runtime Jar"链接中 `iceberg-hive-runtime-1.7.2.jar` 的版本号完全一致，避免文档与实际发布物脱节。

## 总结

本次提交为文档修复，将 Hive 引擎支持矩阵中"Latest Iceberg Support"列从动态模板变量 `{{ icebergVersion }}` 改为硬编码 `1.7.2`，使文档显示与实际发布的 Hive 运行时 jar 版本保持一致，避免误导用户。
