# 提交 2229：View Spec: Fix engine-version key in the JSON example

## 提交信息

- **序号**：2229 / 4088
- **哈希**：f5eab59c923ca262ab9a86d41e509a56afe6a421
- **短哈希**：f5eab59c9
- **日期**：2025-06-11 10:15:48 -0700
- **作者**：hsiang-c
- **提交说明**：View Spec: Fix engine-version key in the JSON example
- **PR/Issue**：#13292

## 总体目的

本提交修复了 Iceberg 视图规范（View Spec）文档中 JSON 示例的一个键名错误。在视图规范的元数据 JSON 示例中，`summary` 对象里表示引擎版本的键名被错误地写成了 camelCase 形式的 `engineVersion`，而规范本身定义的应该是 kebab-case 形式的 `engine-version`。这个不一致会误导文档读者按照错误的键名去实现或解析视图元数据，可能导致引擎版本信息无法被正确识别。修复此类文档与规范定义不一致的问题，有助于保证规范文档作为权威参考的准确性。

## 如何达成设计目的

- 直接将 `format/view-spec.md` 文件中三处出现的 `"engineVersion"` 键名修改为 `"engine-version"`，使其与规范定义的属性命名风格（kebab-case）保持一致。
- 修改范围限定在 JSON 示例代码块内，不涉及任何代码逻辑变更。

## 修改详情

### `format/view-spec.md` (修改, +3/-3 lines)

**修改目的**：修正视图规范文档中 JSON 示例里引擎版本键名的命名风格错误。

**工作逻辑**：文档中有三处 JSON 示例（分别对应两个 metadata 文件示例中的 summary 块），都将 `"engineVersion" : "3.3.2"` 修改为 `"engine-version" : "3.3.2"`。这与规范中 `engine-name` 等 kebab-case 的键名风格保持统一，确保文档示例与规范定义一致。

## 总结

这是一个纯文档修复提交，修正了视图规范示例 JSON 中 `engine-version` 键名的命名错误，保证文档示例与规范定义的一致性，避免误导实现者。
