# 提交 3513：Update documentation on Spark migrate procedure (#15874)

## 提交信息

- **序号**：3513 / 4088
- **哈希**：9586899fab3cc15fc0d0a5c9060b98ff632974e6
- **短哈希**：9586899fab
- **日期**：2026-04-10 13:45:54 -0700
- **作者**：Wing Yew Poon
- **提交说明**：Update documentation on Spark migrate procedure (#15874)
- **PR/Issue**：#15874

## 总体目的

更新 Spark migrate（迁移）过程的文档，补充 PR #15429 中引入的行为变化说明。migrate 过程现在会在表是 bucketed（分桶）的情况下失败，因为分桶信息不会被保留到 Iceberg 表中。需要在文档中明确说明这一限制。

## 如何达成设计目的

在 `spark-procedures.md` 的 migrate 部分添加一行说明 bucketed 表的失败行为。

## 修改详情

### `docs/docs/spark-procedures.md` (+1 line)

**修改目的**：补充 migrate 过程对 bucketed 表的失败说明。

**工作逻辑**：
在已有的 "Migrate will fail if any table partition uses an unsupported format" 说明后，新增：
```
Migrate will also fail if the table is bucketed, as the bucketing will not be preserved.
```

## 总结

文档更新提交，补充 Spark migrate 过程对 bucketed 表的失败说明，反映 PR #15429 中引入的行为变化。migrate 不会保留分桶信息，因此对 bucketed 表会直接失败。
