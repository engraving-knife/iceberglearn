# 提交 0746：MR: Fix using Date type as partition field (#10210)

## 提交信息

- **序号**：0746 / 4088
- **哈希**：ed0959257cba02f378f7097d81cecaaaef9fa43f
- **短哈希**：ed0959257
- **日期**：2024-05-07 21:31:22 +0800
- **作者**：lurnagao-dahua <91278331+lurnagao-dahua@users.noreply.github.com>
- **提交说明**：MR: Fix using Date type as partition field (#10210)
- **PR/Issue**：#10210

## 总体目的

本提交修复 Hive MR 引擎在向 Iceberg 表写入数据时，使用 `DATE` 或 `TIMESTAMP` 类型字段作为分区字段会抛出异常的 bug。

### Bug 成因

Iceberg 对时间类型有"内部表示"与"外部表示"两套约定：

- **内部表示**（用于存储与分区变换计算）：`DATE` 存为自 epoch 起的天数（`int`），`TIMESTAMP` 存为自 epoch 起的微秒数（`long`）。
- **外部表示**（用于 `Record` 等通用数据模型）：`DATE` 用 `java.time.LocalDate`，`TIMESTAMP` 用 `java.time.LocalDateTime` / `OffsetDateTime`。

`PartitionKey.partition(StructLike row)` 在计算分区值时，通过 `Accessor.get(row)` 取源字段值。`Accessor`（具体是 `PositionAccessor`）内部调用 `row.get(position, javaClass)`，其中 `javaClass = type.typeId().javaClass()`。对于 `DATE` 类型，`javaClass()` 返回 `Integer.class`（对应内部表示的天数）；对于 `TIMESTAMP`，返回 `Long.class`（对应微秒）。

问题在于 `HiveIcebergRecordWriter.partition(Record row)` 直接把原始 `Record`（通常是 `GenericRecord`）传给了 `currentKey.partition(row)`。`GenericRecord.get(pos, Integer.class)` 不会做类型转换，它直接返回存储在记录中的对象——对 `DATE` 字段返回的是 `LocalDate`，对 `TIMESTAMP` 字段返回的是 `LocalDateTime`。随后这个 `LocalDate` / `LocalDateTime` 对象被传给分区变换函数（如 identity 变换），变换函数期望的是 `Integer` / `Long`，于是触发 `ClassCastException`，导致写入失败。

### 为什么其它引擎没有这个问题

- **Flink** 的 `PartitionKeySelector` 在调用 `partitionKey.partition()` 之前，先用 `RowDataWrapper.wrap(row)` 把 Flink 的 `RowData` 包装成 `StructLike`，包装过程中完成了类型转换。
- **Spark** 同样使用 `InternalRowWrapper` 做包装转换。
- **MR** 的 `HiveIcebergRecordWriter` 缺少这一层包装，直接把 `Record` 丢给了 `PartitionKey`，是唯一遗漏的路径。

## 如何达成设计目的

修复策略是在 `HiveIcebergRecordWriter` 中引入 `InternalRecordWrapper`，在分区计算前对 `Record` 做包装，与 Flink / Spark 的做法对齐。

`InternalRecordWrapper`（位于 `iceberg-data` 模块）实现了 `StructLike` 接口，其 `get(pos, javaClass)` 方法在返回字段值前会按类型应用预置的转换函数：

- `DATE`：`LocalDate` → `DateTimeUtil.daysFromDate()` → `Integer`（天数）
- `TIME`：`LocalTime` → `DateTimeUtil.microsFromTime()` → `Long`（微秒）
- `TIMESTAMP`（带时区）：`OffsetDateTime` → `DateTimeUtil.microsFromTimestamptz()` → `Long`
- `TIMESTAMP`（不带时区）：`LocalDateTime` → `DateTimeUtil.microsFromTimestamp()` → `Long`
- `FIXED`：`byte[]` → `ByteBuffer`
- 其它类型：直接透传

这样 `Accessor.get(wrapper)` 调用 `wrapper.get(pos, Integer.class)` 时，wrapper 会先把 `LocalDate` 转成 `Integer` 再返回，分区变换函数就能拿到正确类型的值。

## 修改详情

### `mr/src/main/java/org/apache/iceberg/mr/hive/HiveIcebergRecordWriter.java`

**修改目的**：在分区计算前用 `InternalRecordWrapper` 包装 `Record`，使 `PartitionKey` 能拿到时间类型的内部表示（天数 / 微秒）而非 Java 时间对象。

**工作逻辑**：

1. 新增 import：`org.apache.iceberg.data.InternalRecordWrapper`。

2. 新增成员字段 `private final InternalRecordWrapper wrapper;`。该 wrapper 在构造器中通过 `new InternalRecordWrapper(schema.asStruct())` 初始化，按 schema 的结构类型预生成各字段的类型转换函数数组。

3. 修改 `partition(Record row)` 方法：
   - 修改前：`currentKey.partition(row);`
   - 修改后：`currentKey.partition(wrapper.wrap(row));`
   - `wrapper.wrap(row)` 把当前 `row` 绑定到 wrapper 并返回 wrapper 自身（一个 `StructLike` 视图），`PartitionKey.partition()` 通过该视图取值时会自动完成 `LocalDate` → `Integer` 等转换。

注意 `wrapper` 是成员字段、在每次 `partition()` 调用时复用（`wrap` 只是重新绑定内部引用，不创建新对象），与原有 `currentKey` 复用模式一致，不会引入额外对象分配开销。

### `mr/src/test/java/org/apache/iceberg/mr/hive/TestHiveIcebergStorageHandlerWithEngine.java`

**修改目的**：新增两个引擎级测试，分别覆盖 `DATE` 和 `TIMESTAMP` 作为分区字段的写入场景，验证修复有效并防止回归。

**工作逻辑**：

1. **`testWriteWithDatePartition()`**：
   - 限定执行引擎为 `mr`（Tez 写入尚未实现）。
   - 构造 schema：`id`（Long）+ `part_field`（Date）。
   - 用 `identity("part_field")` 建分区表，写入 3 条不同日期的记录（2023-01-21、2023-01-22、2022-01-21）。
   - 执行 `SELECT * from part_test order by id` 并断言结果数量与日期值正确。

2. **`testWriteWithTimestampPartition()`**：
   - 限定执行引擎为 `mr`。
   - 构造 schema：`id`（Long）+ `part_field`（Timestamp without zone）。
   - 用 `identity("part_field")` 建分区表，写入 3 条不同时间戳的记录。
   - 执行查询并断言结果正确。

两个测试在修复前会因 `ClassCastException` 失败，修复后通过。

## 小结

- **成效**：成功修复 MR 引擎使用 `DATE` / `TIMESTAMP` 类型字段作为分区字段时抛出 `ClassCastException` 的 bug，与 Flink / Spark 引擎的行为对齐。
- **影响范围**：仅影响 `mr` 模块的 `HiveIcebergRecordWriter` 类（写入路径的分区计算环节），不影响其它模块。对使用非时间类型分区的写入无任何行为变化（`InternalRecordWrapper` 对无需转换的类型直接透传）。
- **回迁到 1.4.x 的注意事项**：这是一个低风险、自包含的 bug 修复，适合回迁到 1.4.x 分支。回迁时需确认：1.4.x 分支的 `HiveIcebergRecordWriter` 代码结构与 main 一致（`partition` 方法签名、构造器逻辑）；`InternalRecordWrapper` 类在 1.4.x 中已存在且转换逻辑一致（该类是 iceberg-data 模块的既有类，非本次新增）。测试用例可一并回迁以防止回归。
