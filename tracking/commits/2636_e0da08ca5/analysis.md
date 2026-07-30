# 提交 2636：Spec: Deprecate Position delete files with row data (#14045)

## 提交信息

- **序号**：2636 / 4088
- **哈希**：e0da08ca5017eb44ea0dbb9c7f4ae5bc21528a7a
- **短哈希**：e0da08ca5
- **日期**：2025-09-15 10:04:41 +0200
- **作者**：pvary
- **提交说明**：Spec: Deprecate Position delete files with row data (#14045)
- **PR/Issue**：#14045

## 总体目的

Iceberg 规范（spec）允许在 V2 位置删除文件（position delete files）中，除了记录被删除行的文件路径和位置外，还可选地包含被删除的行数据本身。然而，写入该行数据是可选的，目前没有任何实现实际写入它。保留这种"携带行数据"的能力增加了规范和实现的复杂度，却未带来实际价值。

本提交正式将"位置删除文件中包含行数据"这一能力标记为废弃（deprecated），在 Java 参考实现中对相关 API 添加 `@Deprecated` 注解，并在规范文档中说明废弃情况，计划在 1.12.0 版本移除该能力。这是规范层面的清理，简化数据模型。

## 如何达成设计目的

1. 在 `PositionDelete` 类中，对携带行数据的 `set(CharSequence, long, R)` 重载方法和 `row()` 访问方法添加 `@Deprecated` 注解及 Javadoc，说明自 1.11.0 起废弃、1.12.0 移除，并指引使用不含行数据的 `set(CharSequence, long)` 替代。
2. 在 `format/spec.md` 中新增一节"Position Delete Files with Row Data"，说明规范允许但可选写入行数据、当前无实现写入、Java 实现支持但已在 1.11.0 废弃。

## 修改详情

### `core/src/main/java/org/apache/iceberg/deletes/PositionDelete.java` (+11/-0 lines)

**修改目的**：废弃携带行数据的 API。

**工作逻辑**：
- 对 `set(CharSequence newPath, long newPos, R newRow)` 方法添加 `@Deprecated` 注解和 Javadoc，说明该方法自 1.11.0 废弃、将在 1.12.0 移除，位置删除不再支持携带行数据，应使用 `set(CharSequence, long)` 替代。
- 对 `row()` 方法添加 `@Deprecated` 注解和 Javadoc，说明位置删除携带行数据的能力自 1.11.0 废弃。

### `format/spec.md` (+4/-0 lines)

**修改目的**：在规范中记录废弃说明。

**工作逻辑**：新增"Position Delete Files with Row Data"小节，说明：虽然规范允许在 V2 位置删除文件中包含被删除行本身（除路径和位置外），但写入行是可选的，当前无实现写入。Java 参考实现支持读写该行，但已在 1.11.0 版本废弃。

## 总结

本提交在规范和 Java 实现层面正式废弃了位置删除文件携带行数据的能力。这是一个早已无实际使用的可选特性，废弃它有助于简化数据模型和实现。相关 API 在 1.11.0 标记废弃、1.12.0 计划移除，给出了清晰的迁移指引。
