# 提交 3847：Core: Adjust calculations for reserved field IDs (#16441)

## 提交信息

- **序号**：3847 / 4088
- **哈希**：6dca97059d3f87b2af76ac14ce1224aefd469d6d
- **短哈希**：6dca97059
- **日期**：2026-06-09 11:01:20 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Adjust calculations for reserved field IDs (#16441)
- **PR/Issue**：#16441

## 总体目的

本提交调整了 `StatsUtil` 中保留字段 ID（reserved field IDs）的统计字段 ID 计算逻辑。这与前一个提交（3844，对齐内容统计字段与规范）相关，是统计字段 ID 空间分配的重新设计。

在之前的实现中，保留字段 ID 空间（`Integer.MAX_VALUE - 200` 到 `Integer.MAX_VALUE`）的统计字段 ID 被映射到极高的 ID 范围（`2_147_000_000` 起），这种设计存在几个问题：
1. 计算逻辑复杂且容易溢出（使用 `long` 防溢出但最终转 `int`）。
2. 整个 200 个保留字段 ID 空间都被分配统计字段 ID，即使只有少数几个元数据列实际需要统计。
3. 与最新规范中实际支持的元数据列集合不一致。

本提交将设计改为：只为实际支持的元数据列（`LAST_UPDATED_SEQUENCE_NUMBER` 和 `ROW_ID`）分配统计字段 ID，并将它们的统计空间从极高的 ID 范围移到较低的 `9_000` 范围，简化计算并避免溢出风险。

## 如何达成设计目的

整体设计变更：

1. **显式列出支持的元数据字段 ID**：用 `SUPPORTED_METADATA_FIELD_IDS` 集合（`ImmutableSet`）替代原来基于 ID 范围的判断，只包含 `LAST_UPDATED_SEQUENCE_NUMBER` 和 `ROW_ID` 两个实际需要统计的元数据列。

2. **重新分配统计空间**：
   - 元数据字段的统计空间从 `2_147_000_000` 移到 `9_000`（`STATS_SPACE_FIELD_ID_START_FOR_METADATA_FIELDS`）。
   - 数据字段的统计空间保持 `10_000` 起。
   - 统计空间上限设为 `200_000_000`（`STATS_SPACE_FIELD_ID_END`，排他），避免与保留字段 ID 空间重叠。

3. **简化计算**：不再使用 `long` 防溢出，直接用 `int` 计算，因为新的 ID 范围远低于 `Integer.MAX_VALUE`。

4. **反向映射验证**：`fieldIdForStatsFieldFromReservedField()` 现在会检查计算出的字段 ID 是否在 `SUPPORTED_METADATA_FIELD_IDS` 集合中，不在则返回 -1。

## 修改详情

### `core/src/main/java/org/apache/iceberg/StatsUtil.java` (+75/-80 lines)

**修改目的**：重新设计保留字段 ID 的统计字段 ID 计算。

**工作逻辑**：

1. 常量重新定义：
```java
static final Set<Integer> SUPPORTED_METADATA_FIELD_IDS =
    ImmutableSet.of(
        MetadataColumns.LAST_UPDATED_SEQUENCE_NUMBER.fieldId(), MetadataColumns.ROW_ID.fieldId());
private static final int FIRST_SUPPORTED_METADATA_FIELD_ID =
    Collections.min(SUPPORTED_METADATA_FIELD_IDS);
static final int NUM_SUPPORTED_STATS_PER_COLUMN = 200;
static final int STATS_SPACE_FIELD_ID_START_FOR_METADATA_FIELDS = 9_000;
static final int STATS_SPACE_FIELD_ID_START_FOR_DATA_FIELDS = 10_000;
static final int STATS_SPACE_FIELD_ID_END = 200_000_000;
static final int MAX_DATA_STATS_FIELD_ID =
    STATS_SPACE_FIELD_ID_END - NUM_SUPPORTED_STATS_PER_COLUMN;
static final int MAX_DATA_FIELD_ID =
    (MAX_DATA_STATS_FIELD_ID - STATS_SPACE_FIELD_ID_START_FOR_DATA_FIELDS)
        / NUM_SUPPORTED_STATS_PER_COLUMN;
```

2. `statsFieldIdForField()` 改为基于集合判断：
```java
public static int statsFieldIdForField(int fieldId) {
  return SUPPORTED_METADATA_FIELD_IDS.contains(fieldId)
      ? statsFieldIdForReservedField(fieldId)
      : statsFieldIdForDataField(fieldId);
}
```

3. `statsFieldIdForReservedField()` 简化为基于偏移量的直接计算：
```java
private static int statsFieldIdForReservedField(int fieldId) {
  return STATS_SPACE_FIELD_ID_START_FOR_METADATA_FIELDS
      + (NUM_SUPPORTED_STATS_PER_COLUMN * (fieldId - FIRST_SUPPORTED_METADATA_FIELD_ID));
}
```

4. `fieldIdForStatsField()` 调整边界检查顺序，统一验证范围 `[9_000, 200_000_000)`。

5. `fieldIdForStatsFieldFromReservedField()` 新增集合验证：
```java
private static int fieldIdForStatsFieldFromReservedField(int statsFieldId) {
  int fieldId =
      (statsFieldId - STATS_SPACE_FIELD_ID_START_FOR_METADATA_FIELDS)
              / NUM_SUPPORTED_STATS_PER_COLUMN
          + FIRST_SUPPORTED_METADATA_FIELD_ID;
  return SUPPORTED_METADATA_FIELD_IDS.contains(fieldId) ? fieldId : -1;
}
```

### `core/src/test/java/org/apache/iceberg/TestStatsUtil.java` (+104/-80 lines)

**修改目的**：更新测试以匹配新的 ID 计算逻辑。

**工作逻辑**：

1. `statsIdsOverflowForTableColumns` 测试更新边界检查：新增对 `0`、`200`、`8_600`、`STATS_SPACE_FIELD_ID_END`、`Integer.MAX_VALUE` 等无效 ID 的验证。

2. `statsIdsForReservedColumns` 测试重命名为 `statsIdsForMetadataColumns`，验证 `LAST_UPDATED_SEQUENCE_NUMBER`（fieldId → 9_000）和 `ROW_ID`（fieldId → 9_200）的映射。验证不在支持集合中的保留字段 ID 返回 -1。

3. `statsIdsForTableColumns` 和 `contentStatsForOptionalAndNestedFields` 测试中，UUID 字段的 fieldId 从硬编码的 `1_000_000` / `100_000` 改为 `StatsUtil.MAX_DATA_FIELD_ID`，对应统计 fieldId 改为 `StatsUtil.MAX_DATA_STATS_FIELD_ID`。

4. 新增 `contentStatsSkipsFieldsOutsideStatsRange` 测试：验证 fieldId 超过 `MAX_DATA_FIELD_ID` 的字段会被跳过（不出现在统计 schema 中）。

## 总结

本提交简化并修正了保留字段 ID 的统计字段 ID 计算逻辑，从基于整个保留 ID 范围的复杂计算改为基于显式支持集合的简单偏移计算。元数据字段的统计空间从极高的 `2_147_000_000` 移到 `9_000`，避免了潜在的溢出风险。同时通过集合验证确保只有实际支持的元数据列才能获得统计字段 ID。这是与规范对齐工作的一部分，提升了统计字段 ID 分配的清晰度和正确性。
