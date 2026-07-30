# 提交 1431：Docs: Add `WHEN NOT MATCHED BY SOURCE` to Spark doc (#11636)

## 提交信息

- **序号**：1431 / 4088
- **哈希**：f7ff0dc8c0a27e2bcd727e4f7705cf0a69ccc9b3
- **短哈希**：f7ff0dc8c
- **日期**：2024-11-25（Mon Nov 25 14:08:54 2024 +0100）
- **作者**：Hussein Awala <hussein@awala.fr>
- **提交说明**：Docs: Add `WHEN NOT MATCHED BY SOURCE` to Spark doc (#11636)
- **PR/Issue**：#11636

## 总体目的

Spark 3.5 在 `MERGE INTO` 语句中新增了对 `WHEN NOT MATCHED BY SOURCE ... THEN ...` 子句的支持，允许对"源数据中不存在但目标表中存在"的行执行 `UPDATE` 或 `DELETE`。这是 SQL 标准中 MERGE 的完整语义补充——此前 Spark 只支持 `WHEN MATCHED`（源匹配目标）和 `WHEN NOT MATCHED`（源未匹配，即目标缺失），无法在 MERGE 中处理"目标侧多出来的行"。

Iceberg 的 Spark 写入文档 `docs/docs/spark-writes.md` 在 MERGE 章节已经介绍了 `WHEN MATCHED` 和 `WHEN NOT MATCHED`，但遗漏了 `WHEN NOT MATCHED BY SOURCE`。本提交在文档中补上这一子句的说明和示例，让用户知道在 Spark 3.5+ 上对 Iceberg 表可以使用完整的 MERGE 语义。

## 如何达成设计目的

在 `docs/docs/spark-writes.md` 的 MERGE 章节、"Only one record in the source data can update any given row..."这段之后，新增一段说明 + 代码示例，介绍 `WHEN NOT MATCHED BY SOURCE`。无代码逻辑改动。

## 修改详情

### `docs/docs/spark-writes.md`

**修改目的**：补全 MERGE 文档，说明 Spark 3.5 新增的 `WHEN NOT MATCHED BY SOURCE` 子句。

**工作逻辑**：在 `WHEN NOT MATCHED AND s.event_time > ...` 示例之后新增：

```markdown
Spark 3.5 added support for `WHEN NOT MATCHED BY SOURCE ... THEN ...` to update or delete rows that are not present in the source data:

```sql
WHEN NOT MATCHED BY SOURCE THEN UPDATE SET status = 'invalid'
```
```

示例 SQL 演示了把目标表中"源数据里没有的行"的 `status` 列更新为 `'invalid'`，典型场景是软删除/标记失效。

## 小结

- **成效**：Spark 写入文档现在完整覆盖 MERGE 的三种子句（`WHEN MATCHED`、`WHEN NOT MATCHED`、`WHEN NOT MATCHED BY SOURCE`），用户可了解 Spark 3.5+ 对 Iceberg 表的完整 MERGE 能力。
- **影响范围**：仅 `docs/docs/spark-writes.md` 新增 5 行，无源码或测试改动。
- **回迁到 1.4.x 的注意事项**：纯文档改动，无运行时影响。如果 1.4.x 的 `spark-writes.md` 同样缺少该说明，可无风险回迁以保持文档完整性。但 1.4.x 文档通常以 main 为准并由站点统一发布，**回迁优先级低**，可视情况回迁或不回迁。需注意 1.4.x 若已支持 Spark 3.5 模块，回迁此文档对用户有帮助。
