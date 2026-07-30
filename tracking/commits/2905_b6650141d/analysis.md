# 提交 2905：Flink: Update Flink 1.18 status to 'End of Life' (#14152)

## 提交信息

- **序号**：2905 / 4088
- **哈希**：b6650141d45c6cbe97b86ffea89635f33ce7880d
- **短哈希**：b6650141d
- **日期**：2025-11-21 13:24:26 +0100
- **作者**：Maximilian Michels
- **提交说明**：Flink: Update Flink 1.18 status to 'End of Life' (#14152)
- **PR/Issue**：#14152

## 总体目的

Iceberg 官网文档 `site/docs/multi-engine-support.md` 中维护着一张 Flink 版本支持矩阵，列出每个 Flink 大版本当前的支持状态（Maintained / End of Life）、Iceberg 对该 Flink 版本支持的起始与最终版本，以及对应的 `iceberg-flink-runtime-<flinkVer>` 下载链接。该矩阵需要随 Flink 上游的生命周期演进同步更新。

Flink 1.18 上游已经进入 End of Life（不再接收修复），因此 Iceberg 社区也需要把文档中 Flink 1.18 的状态从 `Maintained` 改为 `End of Life`，并冻结其对应的 Iceberg 最终支持版本。由于 1.18 不再获得新的 Iceberg 发行版，下载链接不能再继续使用 `{{ icebergVersion }}` 模板占位符（该占位符会随文档构建时填入当前最新 Iceberg 版本，对一个已 EOL 的 Flink 版本来说是错误的），需要改成具体的最终版本号 `1.9.2`。这与表中已有的 1.15 / 1.16 / 1.17 等 EOL 行的处理方式保持一致——它们的下载链接都指向各自冻结的具体版本号而非当前最新版本。

## 如何达成设计目的

本提交只改一行文档：把 Flink 1.18 行的状态列从 `Maintained` 改为 `End of Life`，并把下载链接中的 `{{ icebergVersion }}` 占位符替换为具体版本 `1.9.2`（`iceberg-flink-runtime-1.18-1.9.2.jar`）。起始版本 `1.5.0` 与最终版本列 `1.9.2` 保持不变（最终版本本就是 1.9.2，只是链接之前用了占位符现在改成与最终版本列一致的固定值）。

## 修改详情

### `site/docs/multi-engine-support.md` (+1/-1 lines)

**修改目的**：把 Flink 1.18 标记为 End of Life，并冻结其 runtime jar 下载链接到最终版本 1.9.2。

**工作逻辑**：
改动前该行为：
```
| 1.18    | Maintained      | 1.5.0  | 1.9.2 | [iceberg-flink-runtime-1.18]({{ icebergVersion }}.../iceberg-flink-runtime-1.18-{{ icebergVersion }}.jar) |
```
改动后为：
```
| 1.18    | End of Life     | 1.5.0  | 1.9.2 | [iceberg-flink-runtime-1.18](.../iceberg-flink-runtime-1.18-1.9.2.jar) |
```
两点变化：状态列 `Maintained` → `End of Life`；下载链接里的两处 `{{ icebergVersion }}` 模板替换为固定版本 `1.9.2`。这样文档构建时不会再把当前最新 Iceberg 版本错误地填入一个已 EOL 的 Flink 版本的下载链接，与表中 1.15/1.16/1.17 等 EOL 行使用固定版本号的做法一致。其余 Flink 版本（1.19、1.20、2.0 等 Maintained 行）仍保留 `{{ icebergVersion }}` 占位符不变。

## 总结

本提交是一处纯文档维护，把 Flink 1.18 在 Iceberg 多引擎支持矩阵中的状态更新为 End of Life，并把其 runtime jar 下载链接从动态的 `{{ icebergVersion }}` 占位符冻结为最终发行版本 1.9.2，与 Flink 上游生命周期及表中既有 EOL 行的处理方式保持一致，避免向用户给出错误的下载链接。
