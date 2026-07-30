# 提交 1166：Docs: Clarify Partition Transform (#8337)

## 提交信息

- **序号**：1166 / 4088
- **哈希**：e5d9a1594b44491e1811876257a9a3473f8d8347
- **短哈希**：e5d9a1594
- **日期**：2024-09-19（Thu Sep 19 00:15:16 2024 -0700）
- **作者**：Jason Fehr <jasonmfehr@users.noreply.github.com>
- **提交说明**：Docs: Clarify Partition Transform (#8337)
- **PR/Issue**：#8337

## 总体目的

Iceberg 文档 `docs/docs/partitioning.md` 在「分区转换」一节举例 `logs` 表分区方案时，写的是「partitioned by `date(event_time)` and `level`」。但 Iceberg 的分区转换函数中**没有 `date` 这个 transform**，正确的时间戳到日期的转换函数是 `day`（对应 `Days.transform()`，输出 `date` 类型）。`date` 在 Iceberg 语境里是 `Types.DateType` 类型名，而非 transform 名。

这一处笔误会误导读者，让他们以为 Iceberg 提供 `date(...)` 这一 transform，进而在 SQL 或 API 中误用。本提交把文档中的 `date(event_time)` 改为 `day(event_time)`，使示例与实际 API 一致。

PR 编号 `#8337` 显示这是 2023 年中就提出的老 issue，直到 2024-09 才合入，属于长期遗留的小修复。

## 如何达成设计目的

直接修改 `docs/docs/partitioning.md` 中第 84 行（自上一段「problems」之后）：把字符串 `date(event_time)` 替换为 `day(event_time)`，其他文字不变。无代码改动。

## 修改详情

### `docs/docs/partitioning.md`

**修改目的**：把分区转换示例中的错误 transform 名 `date` 改为正确的 `day`。

**工作逻辑**：

修改前：

```
Table partitioning is configured using these relationships. The `logs` table would be partitioned by `date(event_time)` and `level`.
```

修改后：

```
Table partitioning is configured using these relationships. The `logs` table would be partitioned by `day(event_time)` and `level`.
```

- `day(event_time)` 对应 Iceberg 的 `Transforms.day(...)`，把时间戳列按天分桶，输出 `date` 类型，是 Iceberg 最常用的分区转换之一。
- 这段文字出现在「为什么 Iceberg 比传统 Hive 分区更好」的论述之后，举例说明 Iceberg 通过 transform 自动从 `event_time` 派生 `event_date` 分区值，因此示例 transform 名必须准确。

## 小结

- **成效**：文档示例与 Iceberg 实际 transform API 一致，避免读者误解。
- **影响范围**：仅 `docs/docs/partitioning.md` 一个文件，1 行 1 字符级别的修改，无任何代码或构建逻辑变更。
- **回迁到 1.4.x 的注意事项**：
  - 这是纯文档错误修正，与产品版本功能无关，对 1.4.x 运行时无任何影响。
  - 1.4.x 作为维护分支通常不会单独追平文档，**默认不需要回迁**。若 1.4.x 分支的 `partitioning.md` 同处也存在该笔误，可顺手回迁以保持文档准确；回迁无任何风险。
