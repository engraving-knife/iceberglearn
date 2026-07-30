# 提交 2860：Update docs to refer to table format-version in spec definition (#14410)

## 提交信息

- **序号**：2860 / 4088
- **哈希**：b81ddb6c5f0a27e6df5b94be308b512121d044b6
- **短哈希**：b81ddb6c5
- **日期**：2025-11-10 10:48:38 -0800
- **作者**：Marc Cenac
- **提交说明**：Update docs to refer to table format-version in spec definition (#14410)
- **PR/Issue**：#14410

## 总体目的

这个提交更新了配置文档中 `format-version` 属性的描述，移除了对具体版本号的限制说明。原文描述为"Table's format version (can be 1 or 2)"，暗示格式版本只能是 1 或 2，但随着 Iceberg 规范已发展到支持 v3 版本，这一描述已经过时且不准确。

修正后改为"Table's format version as defined in the Spec"，移除了"(can be 1 or 2)"的限制说明，使描述适用于规范定义的所有版本。这与提交 2858 中对 spec.md 的修正相呼应，两者都是移除对具体版本号的过时限制描述。

## 如何达成设计目的

通过修改 `docs/docs/configuration.md` 文件中 `format-version` 属性的描述文本，移除版本号限制说明，改为引用规范定义。

## 修改详情

### `docs/docs/configuration.md` (+1/-1 lines)

**修改目的**：更新 format-version 属性描述，移除过时的版本号限制。

**工作逻辑**：将描述从"Table's format version (can be 1 or 2) as defined in the [Spec](../../spec.md#format-versioning). Defaults to 2 since version 1.4.0."改为"Table's format version as defined in the [Spec](../../spec.md#format-versioning). Defaults to 2 since version 1.4.0."。仅移除了"(can be 1 or 2)"部分，其余描述（包括规范链接和默认值说明）保持不变。

## 总结

这是一个文档描述修正提交，移除了配置文档中 `format-version` 属性对具体版本号的过时限制说明（"can be 1 or 2"），改为引用规范定义的通用表述。这与规范文档自身的修正保持一致，确保文档准确反映 Iceberg 已支持 v3 格式版本的现状。
