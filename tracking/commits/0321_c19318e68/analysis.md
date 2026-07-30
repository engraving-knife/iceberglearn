# 提交 0321：API: Fix Javadoc on UpdateSchema#updateColumnDoc (#9405)

## 提交信息

- **序号**：0321 / 4088
- **哈希**：c19318e6865f21c117ddb67df23da80984b52b44
- **短哈希**：c19318e68
- **日期**：2024-01-03 15:16:34 -0800
- **作者**：Amogh Jahagirdar
- **提交说明**：API: Fix Javadoc on UpdateSchema#updateColumnDoc (#9405)
- **PR/Issue**：#9405

## 总体目的

这个提交修复了 `UpdateSchema` 接口中 `updateColumnDoc(String name, String newDoc)` 方法的 Javadoc 文档错误。`updateColumnDoc` 方法的职责是根据列名找到对应列后，更新该列的文档字符串（即 `doc` 属性，用于在表 schema 中对列做注释说明）。但在本提交之前，该方法的 Javadoc 文档完全描述的是另一个方法的语义，与该方法实际的行为不匹配，存在严重的文档误导。

具体来说，原 Javadoc 第一行写的是"Update a column in the schema to a new primitive type"（将 schema 中的列更新为新的基本类型），这显然是 `updateColumn(String name, Type newType)` 这类用于修改列类型的方法的描述，被错误地复制粘贴到了 `updateColumnDoc` 上。原文档还提到"Columns may be updated and renamed in the same schema update"（列可以在同一次 schema 更新中被更新和重命名），这与单纯更新文档字符串无关；`@param name` 的说明也写成"name of the column to rename"（要重命名的列名），明显是重命名方法的措辞；`@throws` 部分则罗列了"type incompatibility or if it conflicts with other additions, renames, or updates"（类型不兼容，或与其他新增、重命名、更新冲突），同样描述的是涉及类型变更的校验逻辑。

这种文档与实现不符的问题会带来实际的危害。Iceberg 的 `UpdateSchema` 是面向用户的公共 API（位于 `api` 模块），其 Javadoc 是用户了解方法行为的权威依据。当用户阅读 `updateColumnDoc` 的文档时，会误以为这个方法涉及类型变更、重命名以及一系列冲突校验，从而对方法的行为产生错误预期，甚至在使用时做出错误的判断（例如误以为调用该方法可能因为类型不兼容而抛异常）。因此本提交将 Javadoc 改为准确反映方法实际语义，是 API 文档质量的必要修复。

## 如何达成设计目的

修改方式是直接重写 `updateColumnDoc` 方法的 Javadoc 注释，使其每一行都与"更新列的文档字符串"这一实际语义对齐。改动点包括：方法主述由"更新列到新的基本类型"改为"更新列的文档字符串"；移除与重命名相关的句子；`@param name` 由"要重命名的列名"改为"要更新文档字符串的列名"；`@throws` 由"类型不兼容或与新增/重命名/更新冲突"改为"如果 name 未在 schema 中找到列，或该列将被删除"。保留了对 `Schema#findField(String)` 的引用说明（即通过列名定位列的方式不变）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/UpdateSchema.java`

**修改目的**：修正 `updateColumnDoc(String name, String newDoc)` 方法的 Javadoc，使其准确描述该方法的实际行为，而非沿用其他方法（疑似 `updateColumn` 或 `renameColumn`）的错误描述。

**工作逻辑**：

1. 方法主述行由 `Update a column in the schema to a new primitive type.` 改为 `Update the documentation string for a column.`，与 `updateColumnDoc` 实际只更新列的 `doc` 字符串的行为一致。同时保留 `<p>The name is used to find the column to update using {@link Schema#findField(String)}.` 这一段，说明列定位方式未变。

2. 删除原来的 `<p>Columns may be updated and renamed in the same schema update.` 段落。该段落描述的是涉及重命名的场景，与 `updateColumnDoc` 无关。

3. `@param name` 由 `name of the column to rename` 改为 `name of the column to update the documentation string for`，使参数语义与方法行为一致。

4. `@param newDoc replacement documentation string for the column` 保持不变（原本就是正确的）。

5. `@throws IllegalArgumentException` 由 `If name doesn't identify a column in the schema or if this change introduces a type incompatibility or if it conflicts with other additions, renames, or updates.` 改为 `If name doesn't identify a column in the schema or if the column will be deleted`。移除了"类型不兼容"和"与新增/重命名/更新冲突"这两类与文档字符串更新无关的异常条件，新增了"列将被删除"这一实际会触发异常的条件（因为对即将被删除的列更新文档没有意义）。

## 小结

本提交通过重写 `updateColumnDoc` 方法的 Javadoc，消除了由复制粘贴导致的文档与实现不符问题，使该公共 API 方法的文档准确反映其"仅更新列文档字符串"的语义，避免用户因错误文档而产生对类型变更、重命名及冲突校验的误解。
