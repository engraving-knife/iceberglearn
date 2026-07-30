# 提交 2007：docs: Fix pusblished

## 提交信息

- **序号**：2007 / 4088
- **哈希**：787b9f0bee7b52b489c3fbb24e01f3f904edaf09
- **短哈希**：787b9f0be
- **日期**：2025-04-16 16:10:15 +0200
- **作者**：slfan1989
- **提交说明**：docs: Fix pusblished (#12814)
- **PR/Issue**：#12814

## 总体目的

本提交修复了 Spark 过程（procedures）文档中的一个拼写错误：将 "pusblished" 修正为 "published"。

该拼写错误出现在 `publish_changes` 过程的参数描述表格中，描述 `wap_id` 参数时写道 "The wap_id to be pusblished from stage to prod"，其中 "pusblished" 应为 "published"。虽然这是一个小的文档修复，但拼写错误会影响文档的专业性和可读性。

## 如何达成设计目的

通过修改 `docs/docs/spark-procedures.md` 文件中对应行的文本，将拼写错误修正。

## 修改详情

### `docs/docs/spark-procedures.md` (修改, +1/-1 line)

**修改目的**：修复拼写错误。

**工作逻辑**：
将 `publish_changes` 过程参数表格中 `wap_id` 参数的描述从：
```
The wap_id to be pusblished from stage to prod
```
修正为：
```
The wap_id to be published from stage to prod
```

## 总结

本提交修复了 Spark 过程文档中 "pusblished" 的拼写错误，将其修正为 "published"，是一个单行文档修复。
