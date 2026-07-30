# 提交 2469：Docs: Add V3 types to Spark/Flink type conversion table (#13744)

## 提交信息

- **序号**：2469 / 4088
- **哈希**：aca97753e68ad3c5bea16969efe3efb2b7e1a58c
- **短哈希**：aca97753e
- **日期**：2025-08-06 16:23:49 -0700
- **作者**：Manu Zhang
- **提交说明**：Docs: Add V3 types to Spark/Flink type conversion table (#13744)
- **PR/Issue**：#13744

## 总体目的

该提交更新了 Flink 和 Spark 的类型转换文档表格，加入了 Iceberg V3 规范引入的新类型，使文档与实际支持的类型集合保持一致。

Iceberg V3 规范引入了若干新类型，包括纳秒级时间戳（timestamp nanos）、unknown 类型、variant 类型，以及 geometry/geography 等地理空间类型。这些类型此前并未在 Spark/Flink 的类型转换对照表中体现，导致用户无法从文档中了解这些新类型的映射关系及支持情况。通过补充这些条目，文档可以更准确地反映引擎集成的真实状态，避免用户对类型支持产生误解。

## 如何达成设计目的

通过对两个文档文件的修改来实现：

1. **Flink 文档**：在 Iceberg 到 Flink 的类型转换表中新增"Notes"列，并添加纳秒时间戳、unknown、variant、geometry、geography 等条目，其中 variant/geometry/geography 标记为"Not supported"。

2. **Spark 文档**：在 Iceberg 到 Spark 的类型转换表中补充同样的新类型条目，其中 variant 类型标注为"Spark 4.0+"支持，其余新类型标记为"Not supported"。

3. **格式修正**：顺手修正了 Spark 文档中 `timestamp_ntz` 行的多余空格。

## 修改详情

### `docs/docs/flink.md` (+24/-12 lines)

**修改目的**：在 Flink 类型转换表中补充 V3 新类型并新增 Notes 列。

**工作逻辑**：原表仅有 Iceberg 和 Flink 两列。修改后增加第三列 Notes，用于标注支持状态。新增条目包括：
- `nanosecond timestamp` → `timestamp(9)`
- `nanosecond timestamp with timezone` → `timestamp_ltz(9)`
- `unknown` → `null`
- `variant` → 未支持（Not supported）
- `geometry` → 未支持（Not supported）
- `geography` → 未支持（Not supported）

### `docs/docs/spark-getting-started.md` (+7/-1 lines)

**修改目的**：在 Spark 类型转换表中补充 V3 新类型条目。

**工作逻辑**：新增条目包括：
- `nanosecond timestamp` → 未支持
- `nanosecond timestamp with timezone` → 未支持
- `unknown` → 未支持
- `variant` → `variant`（Spark 4.0+）
- `geometry` → 未支持
- `geography` → 未支持

同时修正了 `timestamp without timezone` 行中 `timestamp_ntz` 后多余的一个空格。

## 总结

这是一个纯文档更新提交，将 Iceberg V3 规范引入的新类型（纳秒时间戳、unknown、variant、geometry、geography）补充到 Spark 和 Flink 的类型转换对照表中。该提交不涉及任何代码修改，仅通过完善文档帮助用户了解新类型在各引擎中的映射与支持状态，特别是 variant 类型在 Spark 4.0+ 中已获支持，而在 Flink 中尚未支持。
