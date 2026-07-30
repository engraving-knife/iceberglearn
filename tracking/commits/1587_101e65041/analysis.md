# 提交 1587：Doc: Add Hive DELETE ORPHAN-FILES example (#11896)

## 提交信息

- **序号**：1587
- **哈希**：101e65041ae09cb9aa24e14a4fa25da77856c912
- **短哈希**：101e65041
- **日期**：2025-01-15（Wed Jan 15 20:02:13 2025 +0900）
- **作者**：Yuya Ebihara <ebyhry@gmail.com>
- **提交说明**：Doc: Add Hive DELETE ORPHAN-FILES example (#11896)
- **PR/Issue**：#11896

## 总体目的

本提交为 Iceberg 的 Hive 集成文档补充 `DELETE ORPHAN-FILES` 这一维护操作的语法示例。

背景：Iceberg 表在长期运行中会产生"孤儿文件"（orphaned files）——即数据/元数据文件不再被任何快照或元数据引用，但仍残留在底层存储上，浪费空间。Iceberg 提供了 `remove_orphan_files` 这一维护过程（maintenance procedure），Hive 集成层通过 `ALTER TABLE ... EXECUTE` 语法暴露该能力。文档此前的 Hive 页面（`docs/docs/hive.md`）已展示了 `expire_snapshots` 的示例（紧邻本提交插入位置之前），却唯独缺少 `DELETE ORPHAN-FILES` 的示例，导致用户只能从其他渠道（如 Spark 文档或 Javadoc）猜测 Hive 下的写法。

本提交在该页面 `expire_snapshots` 示例之后插入 `DELETE ORPHAN-FILES` 小节，给出两种调用形式（不带时间参数与带 `OLDER THAN` 时间参数），与既有 `expire_snapshots` 示例风格保持一致，方便用户对照使用。

## 如何达成设计目的

纯文档改动，在 `docs/docs/hive.md` 的 `expire_snapshots` 示例代码块之后新增一个三级标题小节 `### \`DELETE ORPHAN-FILES\``，包含一句功能说明和一段 SQL 代码示例。

### 修改详情

#### `docs/docs/hive.md`

**修改目的**：补充 `DELETE ORPHAN-FILES` 的语法示例。

**插入内容**（位于 `expire_snapshots` 示例之后、"Type compatibility" 小节之前）：
```markdown
### `DELETE ORPHAN-FILES`

Used to remove files which are not referenced in any metadata files of an Iceberg table and can thus be considered "orphaned".
The function is available with the following syntax:
```sql
ALTER TABLE table_a EXECUTE DELETE ORPHAN-FILES;
ALTER TABLE table_a EXECUTE DELETE ORPHAN-FILES OLDER THAN ('2021-12-09 05:39:18.689000000');
```
```

**说明**：
- 第一句解释"孤儿文件"定义：未被任何元数据文件引用的文件。
- 给出两种语法：
  1. `ALTER TABLE table_a EXECUTE DELETE ORPHAN-FILES;`——不带时间参数，删除所有孤儿文件。
  2. `ALTER TABLE table_a EXECUTE DELETE ORPHAN-FILES OLDER THAN ('2021-12-09 05:39:18.689000000');`——只删除早于指定时间戳的孤儿文件，避免删除刚写入但尚未被提交引用的文件（安全保留窗口）。时间戳示例复用了上方 `expire_snapshots` 示例的同一时间值，保持文档一致性。
- 插入位置选择在 `expire_snapshots` 之后，符合"先过期快照、再清理孤儿文件"的常见维护顺序，逻辑连贯。

## 小结

- **成效**：Hive 文档现在完整覆盖了 `expire_snapshots` 与 `DELETE ORPHAN-FILES` 两个最常用的维护操作示例，用户无需再跨文档查找。降低了使用门槛，减少误用。
- **影响范围**：仅 `docs/docs/hive.md` 一个文件，新增 9 行，无代码、构建、运行时变更。
- **回迁到 1.4.x 的注意事项**：纯文档改进，与版本功能无关。1.4.x 若也维护 Hive 文档，**可回迁**以保持文档完整；但文档通常跟随 main 维护、对发布产物无影响，优先级低。需确认 1.4.x 的 Hive 集成已支持 `DELETE ORPHAN-FILES` 语法（该语法支持是更早引入的，1.4.x 应已具备），否则文档会误导用户。
