# 提交 1669：Spec: Fix current-version-id in view-spec.md example (#12146)

## 提交信息

- **序号**：1669 / 4088
- **哈希**：9feca0c306b9f49382c4b9bab39daddf5a81712c
- **短哈希**：9feca0c30
- **日期**：2025-02-01（Sat Feb 1 11:07:33 2025 -0800，原始 +0100 时区为 12:07:33）
- **作者**：ldsantos0911 <36975329+ldsantos0911@users.noreply.github.com>
- **提交说明**：Spec: Fix current-version-id in view-spec.md example (#12146)
- **PR/Issue**：#12146

## 总体目的

`format/view-spec.md` 是 Iceberg View 规范文档，其中第 263 行附近有一个 view 元数据 JSON 示例，文件名是 `00002-(uuid).metadata.json`（即第 2 版元数据文件），但示例 JSON 体中的 `"current-version-id"` 却写成 `1`。这造成示例内部自相矛盾——文件名暗示这是版本 2 的元数据，但 `current-version-id` 又指回版本 1，会让规范读者困惑 `current-version-id` 到底语义是什么。

本提交把示例中的 `"current-version-id": 1` 改为 `"current-version-id": 2`，使其与文件名 `00002-...` 一致，消除规范文档中的笔误。

## 如何达成设计目的

纯文档修订，仅修改 `format/view-spec.md` 一行，把示例 JSON 的 `current-version-id` 值从 `1` 改为 `2`。

## 修改详情

### `format/view-spec.md`（修改，+1/-1 行）

**修改目的**：修正 view 元数据示例中 `current-version-id` 与元数据文件名版本号不一致的笔误。

**工作逻辑**：示例文件路径为 `s3://bucket/warehouse/default.db/event_agg/metadata/00002-(uuid).metadata.json`，对应版本 2 的元数据；`current-version-id` 应指向当前激活的 view 版本，此处应为 `2` 而非 `1`。

## 小结

- **成效**：修正 view 规范文档示例的内部一致性，避免读者误解 `current-version-id` 字段语义。
- **影响范围**：仅文档，无代码或行为变更。
- **回迁到 1.4.x 的注意事项**：纯文档修订，回迁安全且无风险。若 1.4.x 分支的 `view-spec.md` 在该位置有相同笔误，建议一并修正。
