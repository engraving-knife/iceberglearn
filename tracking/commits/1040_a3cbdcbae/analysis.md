# 提交 1040：Add Flink 1.20 & remove Flink 1.17 in stage-binaries.sh and docs (#10888)

## 提交信息

- **序号**：1040 / 4088
- **哈希**：a3cbdcbae6627b42a52eb8e028ce4f83f30421f9
- **短哈希**：a3cbdcbae
- **日期**：2024-08-07 16:07:38 +0200
- **作者**：Robert Stupp <snazy@snazy.de>
- **提交说明**：Add Flink 1.20 & remove Flink 1.17 in stage-binaries.sh and docs (#10888)
- **PR/Issue**：#10888（提交说明中标注为 #10881 的后续 follow-up）

## 总体目的

本提交是 Flink 1.20 支持工作的收尾配套，对应 PR #10888，并在提交说明中明确标注为 #10881（即提交 1032-1035 那组 Flink 1.20 模块引入）的 follow-up。前面 1032-1035 完成了源码模块与 Gradle 构建系统的切换，但还有两处"面向发布与用户"的配置/文档没有同步更新：

1. **发布暂存脚本 `dev/stage-binaries.sh`**：该脚本用于在发布流程中暂存各引擎的 runtime jar 到 Maven 暂存仓库。脚本中的 `FLINK_VERSIONS` 变量仍为 `1.17,1.18,1.19`，没有 1.20，且仍包含已淘汰的 1.17。如果不更新，发布时会漏掉 1.20 的 runtime jar、还会徒劳尝试暂存已不构建的 1.17 jar。

2. **面向用户的文档**：`site/docs/multi-engine-support.md`（Flink 版本兼容性矩阵）和 `site/docs/releases.md`（各版本 runtime jar 下载链接）需要反映新的版本支持状态——1.20 进入 Maintained、1.17 的最后支持版本固化为 1.6.0。

本提交的目的是补齐这两处遗漏，使发布脚本与用户文档与 1032-1035 的源码改动保持一致。

## 如何达成设计目的

- 对 `dev/stage-binaries.sh`：将 `FLINK_VERSIONS` 变量从 `1.17,1.18,1.19` 改为 `1.18,1.19,1.20`。
- 对 `site/docs/multi-engine-support.md`：在 Flink 兼容性表格中新增 1.20 行（Maintained，首次支持版本 1.7.0），并将 1.17 行的"最后支持版本"从 `{{ icebergVersion }}`（动态当前版本）固化为 `1.6.0`（表示 1.6.0 是最后发布 1.17 runtime 的 Iceberg 版本）。
- 对 `site/docs/releases.md`：在 runtime jar 下载链接列表中新增 1.20 链接、移除 1.16 与 1.17 链接（这两个已不再发布新版本），并按版本从新到旧排列。

## 修改详情

### `dev/stage-binaries.sh`

**修改目的**：更新发布暂存脚本覆盖的 Flink 版本列表。

**工作逻辑**：将第 22 行的 `FLINK_VERSIONS=1.17,1.18,1.19` 改为 `FLINK_VERSIONS=1.18,1.19,1.20`。脚本后续会遍历此变量为每个版本暂存 `iceberg-flink-runtime-<version>` jar。改动后，发布流程会暂存 1.18/1.19/1.20 三个 Flink runtime jar，不再暂存 1.17。

### `site/docs/multi-engine-support.md`

**修改目的**：更新 Flink 版本兼容性矩阵，反映 1.20 进入维护、1.17 停止更新。

**工作逻辑**：在 Flink 兼容性表格中做两处改动：

1. **1.17 行**：将"最后支持版本"列从 `{{ icebergVersion }}`（站点变量，表示当前最新版本）改为 `1.6.0`。这表示 Iceberg 1.6.0 是最后一个发布 `iceberg-flink-runtime-1.17` 的版本，后续版本不再发布 1.17 runtime。1.17 的状态列保持 `Deprecated`。

2. **新增 1.20 行**：
   ```
   | 1.20    | Maintained      | 1.7.0                   | {{ icebergVersion }}   | [iceberg-flink-runtime-1.20](...) |
   ```
   状态为 Maintained，首次支持的 Iceberg 版本为 1.7.0，最后支持版本为动态的 `{{ icebergVersion }}`，并附带指向 Maven Central 的 1.20 runtime jar 下载链接。

1.18、1.19 行保持不变。

### `site/docs/releases.md`

**修改目的**：更新 runtime jar 下载链接列表，新增 1.20、移除已停止发布的 1.16 与 1.17。

**工作逻辑**：在 Flink runtime jar 链接区域：
- 新增 `{{ icebergVersion }} Flink 1.20 runtime Jar` 链接（指向 `iceberg-flink-runtime-1.20`）。
- 保留 `Flink 1.19 runtime Jar` 链接。
- 保留 `Flink 1.18 runtime Jar` 链接。
- 删除 `Flink 1.17 runtime Jar` 链接。
- 删除 `Flink 1.16 runtime Jar` 链接。

调整后列表按版本从新到旧排列为 1.20、1.19、1.18。

## 小结

- **成效**：完成 Flink 1.20 支持的发布侧与文档侧收尾——发布脚本 `stage-binaries.sh` 会正确暂存 1.18/1.19/1.20 三个 runtime jar；用户文档的兼容性矩阵与下载链接反映了 1.20 Maintained、1.17 最后版本 1.6.0 的新状态。
- **影响范围**：3 个文件。`dev/stage-binaries.sh`（1 行）、`site/docs/multi-engine-support.md`（2 行：1 改 1 增）、`site/docs/releases.md`（4 行：2 增 2 删）。无源码改动。
- **回迁到 1.4.x 的注意事项**：**不应回迁**。本提交依赖 1032-1035 的源码改动——1.4.x 分支没有 `flink/v1.20/` 模块，发布脚本若加入 1.20 会在暂存时因找不到产物而失败；1.4.x 仍需发布 1.15/1.16/1.17 的 runtime jar，移除 1.17 会切断 1.4.x 用户获取 1.17 runtime 的渠道。文档中的版本矩阵也应与 1.4.x 实际支持的版本一致。1.4.x 的发布脚本与文档应基于自身版本矩阵独立维护。
