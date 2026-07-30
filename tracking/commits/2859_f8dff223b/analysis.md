# 提交 2859：Spec: minor corrections (#14495)

## 提交信息

- **序号**：2859 / 4088
- **哈希**：f8dff223bb8256e85c7477c8f6aacd7607c36686
- **短哈希**：f8dff223b
- **日期**：2025-11-10 10:31:57 -0800
- **作者**：Wing Yew Poon
- **提交说明**：Spec: minor corrections (#14495)
- **PR/Issue**：#14495

## 总体目的

这个提交对 Iceberg 表格式规范文档（spec.md）进行了两处小修正，修正了规范文档中的不准确描述和错误值。

第一处修正涉及 `format-version` 字段的描述。原文描述中提到"Currently, this can be 1 or 2 based on the spec"（当前规范中可以是 1 或 2），但随着 Iceberg 规范已发展到支持 v3 版本，这一描述已经过时。修正后移除了对具体版本号的限制说明，改为更通用的表述。

第二处修正涉及 `geography` 类型的列属性值。原文中 geography 类型的 `iceberg.binary-type` 属性值错误地写成了 `GEOMETRY`，应为 `GEOGRAPHY`。这是一个明显的笔误。

## 如何达成设计目的

通过直接修改 `format/spec.md` 文件中的两处文本，修正描述错误。

## 修改详情

### `format/spec.md` (+2/-2 lines)

**修改目的**：修正规范文档中的两处错误。

**工作逻辑**：

1. **format-version 描述修正**：将 `format-version` 字段的描述从"An integer version number for the format. Currently, this can be 1 or 2 based on the spec. Implementations must throw an exception if a table's version is higher than the supported version."改为"An integer version number for the format. Implementations must throw an exception if a table's version is higher than the supported version."。移除了"Currently, this can be 1 or 2 based on the spec"这一已过时的描述，因为规范现已支持 v3。

2. **geography 类型属性修正**：在类型映射表中，将 geography 类型的 `iceberg.binary-type` 属性值从 `GEOMETRY` 修正为 `GEOGRAPHY`。原来错误地与 geometry 类型使用了相同的属性值，现在修正为正确的 `GEOGRAPHY`。

## 总结

这是一个规范文档的小修正提交，修正了两处错误：一是移除了 `format-version` 字段中过时的版本号限制描述（规范已支持 v3），二是将 `geography` 类型的列属性值从错误的 `GEOMETRY` 修正为正确的 `GEOGRAPHY`。修改极小但对规范文档的准确性很重要。
