# 提交 2628：Docs: Update supported Flink versions (#13611)

## 提交信息

- **序号**：2628 / 4088
- **哈希**：723d0998d69f254822efd6f8939e71c0723aaab2
- **短哈希**：723d0998d
- **日期**：2025-09-12 15:26:48 +0200
- **作者**：Maximilian Michels
- **提交说明**：Docs: Update supported Flink versions
- **PR/Issue**：#13611

## 总体目的

Iceberg 的多引擎支持文档 `site/docs/multi-engine-support.md` 中有一个 Flink 版本兼容性矩阵，列出每个 Flink 版本的状态（维护中/停止维护）、Iceberg 支持的起始版本、最新版本及对应的 runtime jar 链接。

Iceberg 1.10.0 引入了对 Flink 2.0 的支持（并在 1.10.0 中移除了 Flink 1.18 的支持，见 2621 的发布说明）。本提交更新该兼容性矩阵以反映这两项变化：
1. **新增 Flink 2.0 行**：状态为 Maintained，Iceberg 起始支持版本为 1.10.0，最新版本跟随 `{{ icebergVersion }}` 变量，附 runtime jar 链接。
2. **冻结 Flink 1.18 的最新版本**：将 1.18 行的"最新 Iceberg 版本"从 `{{ icebergVersion }}`（动态变量，会随站点版本变化）改为固定的 `1.9.2`，因为 1.10.0 起不再支持 Flink 1.18，1.9.2 是支持 1.18 的最后一个 Iceberg 版本。

## 如何达成设计目的

修改 `multi-engine-support.md` 中 Flink 兼容性表格：
- 在 Flink 1.20 行之后新增 Flink 2.0 行，填入起始版本 1.10.0、最新版本 `{{ icebergVersion }}`、runtime jar 链接（指向 `iceberg-flink-runtime-2.0`）。
- 将 Flink 1.18 行的最新版本列从 `{{ icebergVersion }}` 改为 `1.9.2`，表示 1.18 的支持止于 Iceberg 1.9.2。

## 修改详情

### `site/docs/multi-engine-support.md` (+2/-1 lines)

**修改目的**：更新 Flink 版本兼容性矩阵，反映 Flink 2.0 支持与 1.18 停止支持。

**工作逻辑**：
- **Flink 1.18 行**：将"最新 Iceberg 版本"列从 `{{ icebergVersion }}`（动态变量）改为固定的 `1.9.2`。因为 Iceberg 1.10.0 已移除 Flink 1.18 支持，1.9.2 是最后一个支持 1.18 的版本。runtime jar 链接中仍保留 `{{ icebergVersion }}` 变量（这是一个小瑕疵，理论上也应固定为 1.9.2，但链接列可能由模板统一管理）。
- **新增 Flink 2.0 行**：`| 2.0 | Maintained | 1.10.0 | {{ icebergVersion }} | [iceberg-flink-runtime-2.0](...) |`。表示 Flink 2.0 从 Iceberg 1.10.0 起开始支持，当前最新版本跟随站点 icebergVersion 变量（1.10.0），附指向 `iceberg-flink-runtime-2.0` 的 Maven 链接。

## 总结

本提交更新了 Flink 版本兼容性矩阵，新增 Flink 2.0 行（从 Iceberg 1.10.0 起支持），并将 Flink 1.18 的最新支持版本固定为 1.9.2（因 1.10.0 已移除 1.18 支持）。这使文档准确反映了 1.10.0 版本的 Flink 支持策略变化，与发布说明（2621）保持一致。
