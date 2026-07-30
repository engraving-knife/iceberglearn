# 提交 1781：Spec: Allow Equality Deletes with Row Lineage and Define Behavior (#12230)

## 提交信息

- **序号**：1781 / 4088
- **哈希**：88445086d039a85aba0530c89b7e9ae4f1f2eaa3
- **短哈希**：88445086d
- **日期**：2025-02-24 12:55:35 -0600
- **作者**：Russell Spitzer
- **提交说明**：Spec: Allow Equality Deletes with Row Lineage and Define Behavior (#12230)
- **PR/Issue**：#12230

## 总体目的

这个提交修改了 Iceberg 表格式规范（spec.md），放宽了行血缘（Row Lineage）功能与等值删除（Equality Deletes）之间的限制。此前的规范规定："当行血缘启用时，新快照不能包含等值删除文件"，即两者完全不兼容。这是因为行血缘需要维护行的 lineage 值（行 ID），而等值删除的设计初衷是避免在写入变更前读取已有数据，因此无法提供原始行的 ID。

这个限制过于严格，实际上阻止了同时需要行血缘和等值删除的场景。本次修改重新定义了两者的交互行为：不再禁止同时使用，而是明确了等值删除对行血缘的影响——通过等值删除更新的行不会被追踪 lineage，这些更新始终被视为"完全删除旧行 + 添加唯一新行"。

## 如何达成设计目的

提交通过修改 `format/spec.md` 中关于行血缘的描述来达成目标。将原先的禁止性描述替换为行为定义性描述，说明等值删除更新的行不追踪 lineage，并明确定义这些更新的语义（旧行完全移除，新行作为唯一新行添加）。

## 修改详情

### `format/spec.md`（修改, +1/-2 lines）

**修改目的**：放宽行血缘与等值删除的兼容性限制，定义等值删除在行血缘启用时的行为。

**工作逻辑**：
- **删除的原文本**：`When row lineage is enabled, new snapshots cannot include Equality Deletes. Row lineage is incompatible with equality deletes because lineage values must be maintained, but equality deletes are used to avoid reading existing data before writing changes.`（当行血缘启用时，新快照不能包含等值删除。行血缘与等值删除不兼容，因为 lineage 值必须被维护，但等值删除用于避免在写入变更前读取已有数据。）
- **新增的文本**：`Row lineage does not track lineage for rows updated via Equality Deletes, because engines using equality deletes avoid reading existing data before writing changes and can't provide the original row ID for the new rows. These updates are always treated as if the existing row was completely removed and a unique new row was added.`（行血缘不追踪通过等值删除更新的行的 lineage，因为使用等值删除的引擎在写入变更前避免读取已有数据，无法为新行提供原始行 ID。这些更新始终被视为旧行被完全移除且添加了一个唯一的新行。）

**行为定义的关键点**：
1. 等值删除更新的行不追踪 lineage（即不继承原始行的 ID）。
2. 原因：使用等值删除的引擎不读取已有数据，无法提供原始行 ID。
3. 语义定义：等值删除的更新被始终视为"完全删除旧行 + 添加唯一新行"，而非"更新现有行"。

## 小结

- **成效**：放宽了 Iceberg 规范中行血缘与等值删除的限制，从"完全禁止"改为"允许但定义行为"。这使得同时需要行血缘和等值删除的场景成为可能，同时明确了等值删除在行血缘上下文中的语义行为。
- **影响范围**：仅修改格式规范文档，不涉及代码实现。这是一个规范层面的变更，后续需要 Iceberg 各引擎实现来适配。影响所有支持行血缘和等值删除的 Iceberg 实现。
- **回迁到 1.4.x 的注意事项**：建议回迁。这是规范文档的修改，如果 1.4.x 分支的 spec.md 包含相关限制描述，应同步修改以保持规范一致。此修改无代码依赖，是纯文档变更。但需注意，此规范变更后续可能有配套的代码实现提交，需一并评估回迁。
