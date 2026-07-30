# 提交 0297：Docs: Fix incorrect set_current_snapshot procedure argument (#9360)

## 提交信息

- **序号**：0297 / 4088
- **哈希**：bfa8006e57db8758d49f31d4ba7b06a8e924e163
- **短哈希**：bfa8006e5
- **日期**：2023-12-21 13:01:35 -0800
- **作者**：Tom Tanaka
- **提交说明**：Docs: Fix incorrect set_current_snapshot procedure argument (#9360)
- **PR/Issue**：#9360

## 总体目的

Iceberg 的 Spark 过程（procedure）`set_current_snapshot` 用于将表的当前快照切换到指定的快照 ID 或者一个引用（reference，可以是 branch 或 tag）。该过程的实际参数在代码中只接受两个互斥的输入：`snapshot_id`（long 类型）或 `ref`（string 类型，表示 branch 或 tag 名称）。

然而在用户文档 `docs/spark-procedures.md` 中介绍 `set_current_snapshot` 的“使用 tag 设置当前快照”示例时，错误地把参数名写成了 `tag`，例如：

```sql
CALL catalog_name.system.set_current_snapshot(table => 'db.sample', tag => 's1');
```

这个示例与实际的存储过程签名不一致——存储过程并不存在名为 `tag` 的参数。用户如果直接复制这段示例去执行，会得到参数不匹配的错误，无法成功调用过程，造成使用上的困扰和文档信任度的下降。

本提交的目的就是修正这个文档错误，把示例中的参数名 `tag` 改为正确的 `ref`，使文档与实际过程签名保持一致，让用户能够按文档直接成功执行。

## 如何达成设计目的

本提交采用最直接的单行文档修复策略：在 `docs/spark-procedures.md` 中找到示例代码里错误的参数名 `tag => 's1'`，将其替换为正确的 `ref => 's1'`。改动范围极小但精准命中问题点，与同章节的参数表（`ref | string | Snapshot Reference (branch or tag) to set as current`）保持一致。

## 修改详情

### `docs/spark-procedures.md`

**修改目的**：修正 `set_current_snapshot` 过程示例中错误的参数名 `tag` 为正确的 `ref`。

**工作逻辑**：
该文件是 Iceberg Spark Procedures 的用户文档。修改点位于 `### set_current_snapshot` 章节末尾的“Set the current snapshot for `db.sample` to tag `s1`”示例代码块。改动如下：

```diff
-CALL catalog_name.system.set_current_snapshot(table => 'db.sample', tag => 's1');
+CALL catalog_name.system.set_current_snapshot(table => 'db.sample', ref => 's1');
```

这一改动与同章节中的参数表对齐——参数表明确列出 `ref`（string 类型，描述为 “Snapshot Reference (branch or tag) to set as current”）才是用于指定分支或标签名称的参数。修改后，用户复制示例即可正确调用过程，避免了“按文档操作却报错”的体验问题。

## 小结

本提交通过将 `docs/spark-procedures.md` 中 `set_current_snapshot` 过程示例里的错误参数名 `tag` 修正为 `ref`，使文档示例与存储过程的实际签名（以及同章节参数表）保持一致，解决了用户按文档执行会因参数不匹配而失败的问题。
