# 提交 1789：Spec: Fix typo in view spec (#12405)

## 提交信息

- **序号**：1789 / 4088
- **哈希**：c0f1abd6a4ad4a09e75f301f199bc549b689a742
- **短哈希**：c0f1abd6a
- **日期**：2025-02-26 16:30:26 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Spec: Fix typo in view spec (#12405)
- **PR/Issue**：#12405

## 总体目的

Iceberg 的视图规范（view-spec.md）描述了视图版本元数据文件应包含的字段，其中关于视图版本保留数量的说明存在一处用词错误。原说明将该属性称为“table property”（表属性），但实际控制的是视图版本的保留数量，应当属于“view property”（视图属性）。

这个错误会让阅读规范的实现者和使用者产生误解，把视图属性误当成表属性，进而在代码或配置中查找错误的位置。本提交修正这一拼写错误，使规范文本与所描述的对象（视图）保持一致。

由于视图规范是 Iceberg 规范文档的重要组成部分，保持其用语准确对于社区理解和正确实现视图相关功能至关重要。

## 如何达成设计目的

通过对 `format/view-spec.md` 中视图版本元数据字段说明部分的一处文字修改，将 “table property” 改为 “view property”。改动非常局部，仅涉及说明性文档的一行文字，不涉及任何逻辑或结构变更。

## 修改详情

### `format/view-spec.md`（修改, ±1 lines）

**修改目的**：修正视图规范中关于版本保留属性归属的错误描述。

**工作逻辑**：在视图版本元数据文件的 “Notes” 第一条中，原文为 “The number of versions to retain is controlled by the table property: `version.history.num-entries`.”，将其中 “table property” 改为 “view property”，使其正确反映该属性是视图属性而非表属性。该属性 `version.history.num-entries` 用于控制保留的视图版本数量。

## 小结

- **成效**：修正了视图规范中的一处术语错误，使文档描述与视图语义一致。
- **影响范围**：仅影响规范文档 `format/view-spec.md`，不涉及代码改动，对运行时行为无影响。
- **回迁到 1.4.x 的注意事项**：文档类改动，回迁无风险，也无前置依赖。可直接回迁以保持 1.4.x 分支文档与主分支一致。
