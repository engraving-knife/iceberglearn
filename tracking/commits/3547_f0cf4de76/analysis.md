# 提交 3547：Docs: Document that positionDeleteWriteBuilder is for format-version 2 tables only (#15980)

## 提交信息

- **序号**：3547 / 4088
- **哈希**：f0cf4de766a9377771eb3273d4e86d9b01b107c1
- **短哈希**：f0cf4de76
- **日期**：2026-04-15 20:55:32 -0700
- **作者**：sanshi
- **提交说明**：Docs: Document that positionDeleteWriteBuilder is for format-version 2 tables only (#15980)
- **PR/Issue**：#15980

## 总体目的

`FormatModelRegistry.positionDeleteWriteBuilder(...)` 方法的 Javadoc 此前没有说明该方法适用的表格式版本范围。实际上，position delete（位置删除）是 format-version 2 表的概念，通过写入 `DeleteFile`（包含文件路径和位置信息）来标记要删除的行。

而在 format-version 3 表中，删除机制改用了 deletion vectors（删除向量），它总是以 Puffin 格式写入，不再走 `PositionDelete` 的 `FormatModel` 实现链路。这意味着 `positionDeleteWriteBuilder` 及其注册的 `FormatModel` 实现对 v3 表不会生效。

缺乏这个说明可能导致开发者在 v3 表上误用该方法，或对 v3 表的删除行为产生困惑。本提交在 Javadoc 中明确标注该方法仅适用于 format-version 2 表，v3 表使用 deletion vectors（Puffin 格式），不调用注册的 `PositionDelete` FormatModel 实现。

## 如何达成设计目的

在 `positionDeleteWriteBuilder` 方法的 Javadoc 中新增一段 `<p><b>Note:</b>` 说明，描述版本适用范围和 v3 的不同行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/formats/FormatModelRegistry.java` (+5/-0 lines)

**修改目的**：在 `positionDeleteWriteBuilder` 的 Javadoc 中补充版本适用性说明。

**工作逻辑**：
```java
   * <p><b>Note:</b> This method is only applicable to format-version 2 tables. Format-version 3
   * tables use deletion vectors, which are always written in Puffin format. Registered {@link
   * FormatModel} implementations for {@link PositionDelete} are not consulted for format-version 3+
   * tables.
```
说明三点：
1. 该方法仅适用于 format-version 2 表
2. format-version 3 表使用 deletion vectors，以 Puffin 格式写入
3. v3+ 表不会调用注册的 `PositionDelete` FormatModel 实现

## 总结

本提交在 `FormatModelRegistry.positionDeleteWriteBuilder` 方法的 Javadoc 中补充说明：该方法仅适用于 format-version 2 表，format-version 3 表改用 deletion vectors（Puffin 格式），不调用 `PositionDelete` 的 FormatModel 实现。属于文档清晰度改进，帮助开发者正确理解方法的适用范围和 v3 表的删除机制差异。
