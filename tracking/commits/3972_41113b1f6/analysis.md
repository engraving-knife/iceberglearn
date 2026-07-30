# 提交 3972：Core: Remove unused TestTrackedFileStruct.CONTENT_STATS_ORDINAL (#17031)

## 提交信息

- **序号**：3972 / 4088
- **哈希**：41113b1f691d34a7b62c933e2303836c029f2142
- **短哈希**：41113b1f6
- **日期**：2026-07-01 13:08:13 -0700
- **作者**：gaborkaszab
- **提交说明**：Core: Remove unused TestTrackedFileStruct.CONTENT_STATS_ORDINAL (#17031)
- **PR/Issue**：#17031

## 总体目的

本提交移除了测试类 `TestTrackedFileStruct` 中未使用的静态常量 `CONTENT_STATS_ORDINAL`。这是一个简单的代码清理，移除死代码以保持代码整洁。

注释说明 partition 和 content_stats 在 `schemaWithContentStats` 内部用提供的 struct 类型重建，它们的 ordinal 通过 field ID 查找，但 `CONTENT_STATS_ORDINAL` 实际未被任何测试使用。

## 如何达成设计目的

直接删除未使用的常量定义和关联注释。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestTrackedFileStruct.java` (+0/-3 lines)

**修改目的**：移除未使用常量。

**工作逻辑**：
```java
// 移除的代码：
// partition and content_stats are rebuilt with the supplied struct types inside
// schemaWithContentStats, so their ordinals are looked up by field ID.
private static final int CONTENT_STATS_ORDINAL = ordinalOf(TrackedFile.CONTENT_STATS_ID);
```
注意 `PARTITION_ORDINAL` 保留（仍被使用），仅移除 `CONTENT_STATS_ORDINAL`。

## 总结

简单的死代码清理，移除测试中未使用的 `CONTENT_STATS_ORDINAL` 常量。无功能影响。
