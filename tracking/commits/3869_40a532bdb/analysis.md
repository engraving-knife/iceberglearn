# 提交分析：3869 - Spec: clarify Avro encoding for day partition transform

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 3869 |
| 短哈希 | 40a532bdb |
| 完整哈希 | 40a532bdbc244b27d771ae21cad9e09c0ca089d8 |
| 日期 | 2026-06-12 17:15:10 -0700 |
| 作者 | Andrei Tserakhau |
| 提交说明 | Spec: clarify Avro encoding for day partition transform in manifests (#16446) |

## 总体目的

澄清 Iceberg 规范中关于 `day` 分区转换在 Avro manifest 中的编码方式，解决规范与实际实现之间的歧义，消除跨语言互操作问题。

### 背景

Iceberg 规范的分区转换结果类型表中，`day` 转换的结果类型列为 `int`。然而实际上，Java、PyIceberg 和 Rust 三大实现都在 Avro manifest 中将 `day` 分区字段编码为带有 `logicalType: date` 的 `int`。这种规范与实现之间的不一致导致了真实的互操作失败。

## 修改详情

### 1. 修改分区转换结果类型表

**文件路径**: `format/spec.md`

将 `day` 转换的结果类型从 `int` 改为 `date [1]`，其中 `[1]` 是一个脚注引用：

```diff
-| **`day`**         | Extract a date or timestamp day, as days from 1970-01-01     | `date`, `timestamp`, `timestamptz`, `timestamp_ns`, `timestamptz_ns`                                      | `int`       |
+| **`day`**         | Extract a date or timestamp day, as days from 1970-01-01     | `date`, `timestamp`, `timestamptz`, `timestamp_ns`, `timestamptz_ns`                                      | `date` [1]  |
```

### 2. 新增脚注说明

在分区转换说明部分新增 Notes 和脚注 [1]：

```
Notes:

1. Readers must also accept `int` values for the `day` transform, interpreting each integer as a `date` represented by the number of days since `1970-01-01`.
```

这条脚注确保了向前兼容性：读取器必须同时接受纯 `int` 和带 `logicalType: date` 的 `int` 两种编码方式。

## 文档修改类提交说明

此提交属于规范文档修改类。核心变更是将 `day` 分区转换的 Avro 编码类型从 `int` 改为 `date`，与 Java/PyIceberg/Rust 三大实现的既有行为对齐。同时通过脚注要求读取器兼容旧的纯 `int` 编码，确保向后兼容。

### 设计考量

- **写入器（Writers）**: 应该使用 `logicalType: date` 编码 `day` 分区字段
- **读取器（Readers）**: 必须同时接受纯 `int` 和带 `logicalType: date` 的 `int`
- **逻辑类型**: Iceberg 的逻辑结果类型仍然是 `int`（天数），Avro 层面的 `date` logicalType 只是序列化标注

## 总结

此提交修复了规范与实现之间的歧义。通过将 `day` 转换的 Avro 编码类型明确为 `date` 并要求读取器兼容纯 `int`，既统一了写入行为又保证了向后兼容，解决了实际的跨语言互操作失败问题（Issue #16414）。
