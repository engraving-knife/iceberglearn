# 提交 2070：Spec: Remove misleading statement about source-ids

## 提交信息

- **序号**：2070 / 4088
- **哈希**：61e8acecf512d8dd2a72727803e5836ea1099eed
- **短哈希**：61e8acecf
- **日期**：2025-05-01 15:53:18 -0500
- **作者**：Wing Yew Poon
- **提交说明**：Spec: Remove misleading statement about source-ids (#12948)
- **PR/Issue**：#12948

## 总体目的

Iceberg 规范文档 `format/spec.md` 中在分区演化（Partition Evolution）相关章节里有一条关于 v3 元数据中 source-ids 的陈述："In v3 metadata, writers must use only `source-ids` because v3 requires reader support for multi-arg transforms."（在 v3 元数据中，写入器必须仅使用 `source-ids`，因为 v3 要求读取器支持多参数转换）。

该陈述具有误导性。原因在于：v3 确实引入了对多参数转换（multi-arg transforms）的读取器支持要求，但"写入器必须仅使用 `source-ids`"这一表述并不准确——它暗示写入器在使用多参数转换时只能通过 `source-ids` 来引用源字段，而实际上规范中对于多参数转换的源字段引用方式有更完整的定义。这条孤立的陈述既不完整又容易让读者产生误解，因此本提交将其从规范中移除，以保持规范文档的准确性和清晰性。

## 如何达成设计目的

直接删除规范文档中该误导性陈述所在的段落行，不做其他修改。通过移除不准确的信息，避免实现者根据该陈述做出错误的实现决策。

## 修改详情

### `format/spec.md` (修改, +0/-2 lines)

**修改目的**：移除分区演化章节中关于 v3 source-ids 的误导性陈述。

**工作逻辑**：
删除了以下两行内容：
```
In v3 metadata, writers must use only `source-ids` because v3 requires reader support for multi-arg transforms.
```
（含一个空行）。该陈述原本位于分区字段 `field-id` 属性说明之后，删除后直接衔接到关于旧版本读取器如何处理未知转换的段落，使文档更加准确。

## 总结

本提交移除 Iceberg 规范文档中关于 v3 元数据 source-ids 的一条误导性陈述。该陈述声称写入器必须仅使用 `source-ids`，这一表述不够准确且容易导致误解。删除后规范文档在该处的表述更为清晰。
