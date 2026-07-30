# 提交 2245：docs: Fix typo in `spark-procedures.md`

## 提交信息

- **序号**：2245 / 4088
- **哈希**：67f550e7ee7e11272e8e4c3fe18a4f1f68a95dc9
- **短哈希**：67f550e7e
- **日期**：2025-06-16 16:50:31 -0500
- **作者**：Zach Schumacher
- **提交说明**：docs: Fix typo in `spark-procedures.md`
- **PR/Issue**：#13325

## 总体目的

本提交修复了 Spark 存储过程文档中 `rewrite_manifests` 过程输出列名的一个拼写错误。文档中将 `added_manifests_count` 错误地拼写为 `added_mainfests_count`（"mainfests" 应为 "manifests"）。这个拼写错误可能误导用户在查询过程输出结果时使用错误的列名，导致查询失败。修复后确保文档中的列名与实际过程返回的列名一致。

## 如何达成设计目的

- 将 `spark-procedures.md` 文件中的 `added_mainfests_count` 修正为 `added_manifests_count`。

## 修改详情

### `docs/docs/spark-procedures.md` (修改, +1/-1 lines)

**修改目的**：修正 `rewrite_manifests` 过程输出列名的拼写错误。

**工作逻辑**：将输出表格中 `added_mainfests_count` 修改为 `added_manifests_count`，修正 "mainfests" → "manifests" 的字母顺序错误。

## 总结

这是一个纯文档拼写修复提交，修正了 Spark 存储过程文档中 `rewrite_manifests` 输出列名的拼写错误，确保文档与实际输出列名一致。
