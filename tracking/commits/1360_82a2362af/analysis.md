# 提交 1360：DOCS: Explicitly specify `operation` as a _required_ field of `summary` field (#11355)

## 提交信息

- **序号**：1360 / 4088
- **哈希**：82a2362afce28bb2c8a390aadf6babca2d731822
- **短哈希**：82a2362af
- **日期**：2024-11-08（Fri Nov 8 16:39:30 2024 -0500）
- **作者**：Sung Yun <107272191+sungwy@users.noreply.github.com>
- **提交说明**：DOCS: Explicitly specify `operation` as a _required_ field of `summary` field (#11355)
- **PR/Issue**：#11355

## 总体目的

`format/spec.md` 是 Iceberg 表格式的权威规范文档，其中"Snapshots"小节用一张表描述 snapshot 的字段，每个字段在三套规范版本（v1 / v2 / v3）下的必要性（_required_ / _optional_ / 空白）。`summary` 字段在 v2/v3 中是 _required_，其值是一个 string map，其中包含一个 `operation` 子键表示本次快照的操作类型（append / replace / overwrite / delete）。

原表格中 `summary` 的描述为 "A string map that summarizes the snapshot changes, including `operation` (see below)"，措辞模糊——读者容易误以为 `operation` 只是 `summary` 中"可能存在"的一个普通可选键。但实际规范要求 `summary` 内必须包含 `operation`，即 `operation` 本身是 `summary` 内的 _required_ 字段。本提交把描述改为 "A string map that summarizes the snapshot changes, including `operation` as a _required_ field (see below)"，明确这一要求。

## 如何达成设计目的

直接编辑 `format/spec.md`，把 snapshot 字段表中 `summary` 一行的描述文本从 "including `operation` (see below)" 改为 "including `operation` as a _required_ field (see below)"。仅措辞调整，无规范语义变更——这是把规范文本中原本隐含/含糊的要求显式化，与 Iceberg 已有实现（写 snapshot 时总会填 `operation`）保持一致。

## 修改详情

### `format/spec.md`

**修改目的**：在规范文本中明确 `summary.operation` 是必需字段。

**工作逻辑**：在 snapshot 字段表（约第 664 行）中，`summary` 行的描述列由：

```
A string map that summarizes the snapshot changes, including `operation` (see below)
```

改为：

```
A string map that summarizes the snapshot changes, including `operation` as a _required_ field (see below)
```

读者从表格中可立即看出：`summary` 本身在 v2/v3 是 _required_，且其内部的 `operation` 子键同样是 _required_，避免实现方遗漏 `operation` 而违反规范。

## 小结

- **成效**：规范文本现明确 `summary.operation` 为必需字段，消除措辞歧义，便于引擎实现者与表使用者正确理解与实现 snapshot 元数据。
- **影响范围**：仅 `format/spec.md` 一个文件，修改 1 行，无代码、构建或运行时变更；不改变规范语义，仅显式化既有要求。
- **回迁到 1.4.x 的注意事项**：这是 main 分支规范文档的措辞澄清，与 1.4.x 维护分支的发布产物无关。1.4.x 实现已遵循该要求（写 snapshot 时填 `operation`），文档澄清不影响其行为。1.4.x 通常不单独维护 spec.md 文本，**无需回迁**。
