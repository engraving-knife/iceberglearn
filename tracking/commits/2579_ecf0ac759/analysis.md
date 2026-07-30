# 提交 2579：Docs: metadata deletion doc fix (#13432)

## 提交信息

- **序号**：2579 / 4088
- **哈希**：ecf0ac7595dbcd045a65b3c9db048dfdf469a0ee
- **短哈希**：ecf0ac759
- **日期**：2025-08-29 11:23:39 -0700
- **作者**：yguy-ryft
- **提交说明**：Docs: metadata deletion doc fix (#13432)
- **PR/Issue**：#13432

## 总体目的

此次提交修复 Iceberg 文档中关于元数据文件删除（metadata deletion）的描述不准确和易混淆之处。Iceberg 通过 JSON 元数据文件跟踪表状态，每次提交生成新的元数据文件并在 `metadata-log` 字段中记录旧的元数据文件（即"已跟踪"的元数据）。`write.metadata.previous-versions-max` 控制跟踪的元数据文件数量，`write.metadata.delete-after-commit.enabled` 控制是否在提交后删除最旧的已跟踪元数据文件。

原文档存在几个问题：
1. `write.metadata.previous-versions-max` 的描述为"keep before deleting after commit"，暗示它与删除直接挂钩，但实际上它控制的是"跟踪"数量，而非"保留后删除"的数量。
2. 原文档的示例表述有误：称"配置 `enabled=true` 和 `max=20` 不会自动删除元数据文件"，这是错误的——实际上启用后每次新元数据创建时会删除最旧的已跟踪文件。
3. 未明确说明"未跟踪的（orphaned）元数据文件"无法通过 `delete-after-commit` 删除，只能通过 orphan file deletion 清理。

修改后的文档澄清了"跟踪"与"删除"的关系，修正了示例，并补充了 orphan 元数据文件需要通过 orphan file deletion 清理的说明，同时新增了指向维护文档的交叉链接。

## 如何达成设计目的

- 在 `maintenance.md` 中重写"Remove old metadata files"章节：明确 `metadata-log` 字段跟踪旧元数据，`previous-versions-max` 控制跟踪数量；`delete-after-commit.enabled=true` 时每次新元数据创建后删除最旧的已跟踪文件；未跟踪的元数据文件只能通过 orphan file deletion 清理。
- 属性表格新增 `Default` 列，并将 `previous-versions-max` 描述改为"to track"而非"to keep"。
- 修正示例：第二个示例改为"enabled=true, max=20, 21 次提交后有 20 个已跟踪文件，最旧的已被删除，之后每次提交删除最旧的"。
- 在 `configuration.md` 中同步更新属性描述，并新增指向 maintenance.md 的链接。

## 修改详情

### `docs/docs/configuration.md` (+2/-2)

**修改目的**：同步更新写属性表格中的元数据删除属性描述。

**工作逻辑**：
- `write.metadata.delete-after-commit.enabled` 描述末尾新增指向 `maintenance.md#remove-old-metadata-files` 的链接。
- `write.metadata.previous-versions-max` 描述从"提交后删除前保留的最大旧版本元数据文件数"改为"要跟踪的最大旧版本元数据文件数"，准确反映其语义是跟踪数量而非删除阈值。

### `docs/docs/maintenance.md` (+17/-10)

**修改目的**：重写元数据文件删除章节，修正错误描述与示例。

**工作逻辑**：
- 新增段落说明每个元数据文件通过 `metadata-log` 字段跟踪旧元数据，跟踪数量由 `previous-versions-max` 定义。
- 明确 `delete-after-commit.enabled=true` 时保留已跟踪文件（上限 `previous-versions-max`），每次新元数据创建时删除最旧的已跟踪文件；仅删除已跟踪文件，不删除 orphan 文件。
- 新增说明：未跟踪的元数据文件也会作为 orphan file deletion 的一部分被删除。
- 属性表格新增 `Default` 列（false / 100），描述更新为"track"语义。
- 修正示例：
  - 示例1：`enabled=false, max=10`，100 次提交后有 10 个已跟踪 + 90 个 orphan 文件；这 90 个 orphan 无法通过 `enabled=true` 删除（因已未跟踪），只能通过 orphan file deletion 清理。
  - 示例2：`enabled=true, max=20`，21 次提交后有 20 个已跟踪文件，最旧的已在提交后被 writer 删除；此后每次提交删除最旧的。

## 总结

一次文档准确性修复提交，修正了元数据文件删除机制描述中"跟踪"与"删除"语义混淆、示例错误等问题。明确 `previous-versions-max` 控制跟踪数量、`delete-after-commit` 仅删除已跟踪文件、orphan 元数据需通过 orphan file deletion 清理，并修正了示例。同时在 configuration.md 中同步更新描述并新增交叉链接。
