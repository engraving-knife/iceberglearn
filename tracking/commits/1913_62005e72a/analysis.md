# 提交 1913：Data: Refactor PartitionStatsHandler (#12550)

## 提交信息

- **序号**：1913 / 4088
- **哈希**：62005e72a50dbd2d64aa7c267a006b5902b46c3f
- **短哈希**：62005e72a
- **日期**：2025-03-24 15:40:54 +0100
- **作者**：Ajantha Bhat
- **提交说明**：Data: Refactor PartitionStatsHandler (#12550)
- **PR/Issue**：#12550

## 总体目的

`PartitionStatsHandler` 之前用一个内部枚举 `Column` 来描述分区统计文件 schema 的各列，枚举值携带一个序号 `id`（0..11），代码通过 `Column.XXX.name()` 拿列名、`Column.XXX.id()` 拿字段 id。这种设计有几个问题：

1. 枚举的 `name()` 是大写常量名（如 `DATA_RECORD_COUNT`），但实际列名是小写带下划线（如 `data_record_count`），二者不一致；代码里用 `Column.PARTITION.name()` 作为字段名，导致 schema 字段名实际是 "PARTITION" 而非更规范的 "partition"。
2. 字段定义散落：`schema(...)` 方法里又用 `NestedField.required(id, Column.XXX.name(), type)` 重新声明类型，与枚举的 id/name 分离，重复且易错（事实上原代码里 `Column.PARTITION.id()` 是 0，但 schema 里 partition 字段用的是 `NestedField.required(1, ...)`，spec_id 用 id=1 但 schema 字段 id=2，存在 id 偏移混乱）。
3. 调用方拿字段时既用 `Column.XXX.id()` 又用 `Column.XXX.ordinal()`，含义不清。

本提交把枚举替换为一组 `public static final NestedField` 常量（直接持有字段 id、name、类型），加上 partition 字段的 `PARTITION_FIELD_ID`/`PARTITION_FIELD_NAME` 常量。这样字段定义集中、单一来源，调用方通过 `XXX.fieldId()` 拿 id、`XXX.name()` 拿真实列名，避免 id 偏移错误，并修正了 partition 字段名从 "PARTITION" 改为 "partition"。

## 如何达成设计目的

1. 删除 `Column` 枚举，改为静态导入风格的常量：每个统计列是一个 `NestedField.required/optional(fieldId, name, type)`，并额外为 partition 列定义 `PARTITION_FIELD_ID=0` 与 `PARTITION_FIELD_NAME="partition"`。
2. `schema(unifiedPartitionType)` 方法直接复用这些常量组装 Schema，partition 列用 `NestedField.required(PARTITION_FIELD_ID, PARTITION_FIELD_NAME, unifiedPartitionType)`。
3. `recordToPartitionStats` 等内部方法把 `Column.XXX.id()` 调用改为 `XXX.fieldId()`，语义更清晰。
4. 测试同步改为静态导入这些常量，用 `fieldId()` 替代 `id()`/`ordinal()`。

注意：此次重构也顺带修正了字段 id 语义。原枚举里 PARTITION.id()=0 但 schema 里 partition 字段 id 是 1，存在不一致；新代码统一为 `PARTITION_FIELD_ID=0`，spec_id 字段 id=1，与 NestedField 常量保持一致。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/PartitionStatsHandler.java` (修改, +53/-55 lines)

**修改目的**：用 `NestedField` 常量替代 `Column` 枚举，集中字段定义。

**工作逻辑**：

删除 `Column` 枚举，新增常量：

```java
public static final int PARTITION_FIELD_ID = 0;
public static final String PARTITION_FIELD_NAME = "partition";
public static final NestedField SPEC_ID = NestedField.required(1, "spec_id", IntegerType.get());
public static final NestedField DATA_RECORD_COUNT =
    NestedField.required(2, "data_record_count", LongType.get());
// ... 其余字段同理
```

`schema(...)` 改为：

```java
return new Schema(
    NestedField.required(PARTITION_FIELD_ID, PARTITION_FIELD_NAME, unifiedPartitionType),
    SPEC_ID, DATA_RECORD_COUNT, DATA_FILE_COUNT, ...);
```

`recordToPartitionStats` 用 `XXX.fieldId()` 取字段 id 并从 `record` 读取，例如：

```java
stats.set(DATA_RECORD_COUNT.fieldId(), record.get(DATA_RECORD_COUNT.fieldId(), Long.class));
```

### `data/src/test/java/org/apache/iceberg/data/TestPartitionStatsHandler.java` (修改, +33/-15 lines)

**修改目的**：测试改用新的静态常量。

**工作逻辑**：把 `import static ... PartitionStatsHandler.Column` 改为静态导入各个 `NestedField` 常量与 `PARTITION_FIELD_ID`；测试中 `Column.PARTITION.name()` 改为 `PARTITION_FIELD_ID`（用 `findField(PARTITION_FIELD_ID)`），`Column.XXX.id()` / `ordinal()` 改为 `XXX.fieldId()`，例如：

```java
partitionStats.set(DATA_RECORD_COUNT.fieldId(), RANDOM.nextLong());
```

## 总结

本提交重构 `PartitionStatsHandler`，用一组 `NestedField` 静态常量替代 `Column` 枚举，使分区统计文件 schema 的字段定义（id/name/type）集中且单一来源，消除 id 偏移混乱与列名大小写不一致问题（partition 列名修正为 "partition"），调用方通过 `fieldId()` 拿字段 id，语义更清晰。
