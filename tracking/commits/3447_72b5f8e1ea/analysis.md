# 提交 3447：API, Core: Only include required stats fields (#15739)

## 提交信息

- **序号**：3447 / 4088
- **哈希**：72b5f8e1eab614a9b946308d8a6eb9f20a90ea3e
- **短哈希**：72b5f8e1ea
- **日期**：2026-03-23 19:34:07 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：API, Core: Only include required stats fields (#15739)
- **PR/Issue**：#15739

## 总体目的

优化统计信息（stats）的存储和序列化效率。之前 `FieldStatistic.fieldStatsFor()` 方法总是生成包含全部 8 个统计字段的 StructType，无论字段类型是否需要这些统计信息。例如，非 FLOAT/DOUBLE 类型不需要 NaN 计数，非 STRING/BINARY 类型不需要平均/最大值大小，必填字段不需要 null 计数。

此提交改为根据字段类型和可空性，只包含实际需要的统计字段，减少元数据开销。

## 如何达成设计目的

- 修改 `FieldStatistic.fieldStatsFor()` 方法，接收 `Types.NestedField` 而非 `Type`，根据字段属性条件性地包含统计字段
- 引入 `SupportsIndexProjection` 机制，使 `BaseFieldStats` 能够处理投影后的字段索引
- 在 `BaseContentStats` 构建时，为每个字段的统计信息解析正确的 statsStruct
- 更新 revapi 配置以接受序列化兼容性变更

## 修改详情

### `.palantir/revapi.yml` (+4/-0 lines)

**修改目的**：接受 `SupportsIndexProjection` 类的默认序列化变更。

**工作逻辑**：
- 添加一条 accepted break 记录，说明 `SupportsIndexProjection` 类的默认序列化已变更
- 理由是跨版本序列化不保证兼容

### `api/src/main/java/org/apache/iceberg/stats/FieldStatistic.java` (+46/-32 lines)

**修改目的**：根据字段类型和可空性条件性地包含统计字段。

**工作逻辑**：
- 方法签名从 `fieldStatsFor(Type type, int baseFieldId)` 改为 `fieldStatsFor(Types.NestedField field, int baseFieldId)`
- 总是包含 `VALUE_COUNT`、`LOWER_BOUND`、`UPPER_BOUND`、`EXACT_BOUNDS`
- 仅当字段为 optional 时包含 `NULL_VALUE_COUNT`
- 仅当类型为 FLOAT 或 DOUBLE 时包含 `NAN_VALUE_COUNT`
- 仅当类型为 STRING 或 BINARY 时包含 `AVG_VALUE_SIZE` 和 `MAX_VALUE_SIZE`
- 使用动态构建 `List<Types.NestedField>` 的方式替代静态枚举所有字段

### `core/src/main/java/org/apache/iceberg/stats/StatsUtil.java` (+1/-1 lines)

**修改目的**：适配 `fieldStatsFor` 方法签名变更。

### `core/src/main/java/org/apache/iceberg/stats/BaseContentStats.java` (+18/-1 lines)

**修改目的**：在构建统计信息时传递 statsStruct，并解析每个字段的投影结构。

**工作逻辑**：
- 在 `fieldStats.add()` 时传入 `structType` 给 Builder
- 在 `build()` 方法中，遍历所有字段统计，为每个字段解析其对应的 statsStruct
- 如果字段的 statsField 类型是 StructType，则通过 `BaseFieldStats.buildFrom(stat).statsStruct(statsField.type().asStructType()).build()` 创建带投影的统计信息

### `core/src/main/java/org/apache/iceberg/stats/BaseFieldStats.java` (+54/-13 lines)

**修改目的**：引入索引投影机制，支持处理精简后的统计字段结构。

**工作逻辑**：
- `BaseFieldStats` 从实现 `Serializable` 改为继承 `SupportsIndexProjection`
- 新增 `fromProjectionPos` 字段，记录从投影位置到完整 8 字段位置的映射
- 新增 `identityMapping()` 方法生成恒等映射（用于完整字段情况）
- 新增 `projectionMapping(Types.StructType statsStruct, int dataFieldId)` 方法，根据字段 ID 偏移计算投影映射
- 将 `get()` 和 `set()` 方法改为 `internalGet()` 和 `internalSet()`，由 `SupportsIndexProjection` 负责索引转换
- Builder 新增 `statsStruct()` 方法用于设置投影映射

### `api/src/main/java/org/apache/iceberg/avro/SupportsIndexProjection.java` (+5/-0 lines)

**修改目的**：为 `SupportsIndexProjection` 添加支持。

### 测试文件

- `TestStatsUtil.java`、`TestContentStats.java`、`TestFieldStats.java`：更新测试以验证条件性字段包含和投影映射的正确性

## 总结

该提交优化了统计信息的存储效率，根据字段类型和可空性只包含实际需要的统计字段。通过引入 `SupportsIndexProjection` 投影机制，使 `BaseFieldStats` 能够正确处理精简后的字段结构。这减少了不必要的元数据开销，特别是对于不包含 FLOAT/DOUBLE/STRING/BINARY 类型的表。
