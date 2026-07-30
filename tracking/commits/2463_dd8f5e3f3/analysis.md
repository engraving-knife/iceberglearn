# 提交 2463：Doc: Flink can now add/drop/modify columns (#13617)

## 提交信息

- **序号**：2463 / 4088
- **哈希**：dd8f5e3f37696473e66eca17346e6384846d7044
- **短哈希**：dd8f5e3f3
- **日期**：2025-08-06 10:49:29 +0200
- **作者**：Robin Moffatt
- **提交说明**：Doc: Flink can now add/drop/modify columns (#13617)
- **PR/Issue**：#13617

## 总体目的

该提交更新了 Flink Iceberg 集成文档中的"未来改进"（Future improvements）部分，移除了已经实现的功能项，反映 Flink Iceberg 集成的最新能力。

此前，文档中的"Future improvements"部分列出了多个 Flink Iceberg 集成尚不支持的功能，包括添加/删除/重命名/修改列。随着这些功能在 Flink Iceberg 集成中的实现，文档需要更新以反映当前的支持状态。该提交从不支持列表中移除了"添加/删除/重命名/修改列"和"watermark"两项，因为它们现在已被支持。

## 如何达成设计目的

通过编辑 `docs/docs/flink.md` 文件，对"Future improvements"部分进行以下修改：

1. **修正措辞**：将 "do not yet supported in the current Flink Iceberg integration work" 改为更简洁的 "not yet supported in the current Flink Iceberg integration"。

2. **移除已支持的功能项**：
   - 移除 "Don't support adding columns, removing columns, renaming columns, changing columns" 条目（对应 FLINK-19062）
   - 移除 "Don't support creating iceberg table with watermark" 条目

3. **统一条目格式**：将剩余条目的格式从 "Don't support creating..." 改为 "Creation of..."。

## 修改详情

### `docs/docs/flink.md` (+2/-4 lines)

**修改目的**：更新 Flink 文档中的未来改进部分，移除已实现的功能。

**工作逻辑**：

修改前（4个不支持项）：
```markdown
There are some features that are do not yet supported in the current Flink Iceberg integration work:

* Don't support creating iceberg table with hidden partitioning. [Discussion](...) in flink mail list.
* Don't support creating iceberg table with computed column.
* Don't support creating iceberg table with watermark.
* Don't support adding columns, removing columns, renaming columns, changing columns. [FLINK-19062](...) is tracking this.
```

修改后（2个不支持项）：
```markdown
There are some features that are not yet supported in the current Flink Iceberg integration:

* Creation of Iceberg table with hidden partitioning. [Discussion](...) in flink mail list.
* Creation of Iceberg table with computed column.
```

## 总结

这是一个纯文档更新提交，反映 Flink Iceberg 集成已支持添加/删除/修改列和 watermark 功能。从"未来改进"列表中移除了这两项，并对剩余条目的措辞和格式进行了统一。该提交不涉及任何代码修改，仅更新文档以保持与实际功能支持状态的一致性。
