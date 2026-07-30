# 提交 4063：Core: Refactor ContentStats and FieldStats (#17159)

## 提交信息

- **序号**：4063 / 4088
- **哈希**：100d0621b95fef392edd6c2f9ebd813b21685946
- **短哈希**：100d0621b
- **日期**：2026-07-17 12:31:38 -0700
- **作者**：Ryan Blue
- **提交说明**：Core: Refactor ContentStats and FieldStats (#17159)
- **PR/Issue**：#17159

## 总体目的

这个提交对 Iceberg 核心的统计信息（stats）数据模型进行了重大重构，将 `ContentStats` 和 `FieldStats` 的数据模型接口与它们的 `StructLike` 序列化实现分离。

此前的设计中，`ContentStats` 和 `FieldStats` 直接继承 `StructLike` 接口，将数据模型与结构化序列化表示耦合在一起。这种耦合带来几个问题：(1) 数据模型被 `StructLike` 的位置式访问（positional get/set）污染，难以清晰地表达业务语义；(2) 无法独立于序列化表示演进数据模型；(3) 与此前已经完成的 `TrackedFile`/`TrackedFileStruct` 分离模式不一致。

本次重构将接口拆分为：
- **数据模型接口**：`ContentStats`、`FieldStats`——纯业务语义方法（`fieldStats()`、`statsFor(id)`、`lowerBound()` 等），不再继承 `StructLike`。
- **StructLike 实现**：`ContentStatsStruct`、`FieldStatsStruct`——实现数据模型接口 + `StructLike`，提供位置式访问以支持序列化。

这与 `TrackedFile`/`TrackedFileStruct` 的分离模式一致，统一了核心数据模型的设计范式。同时新增了 `copy()` 和 `copy(Set<Integer> fieldIds)` 方法支持深拷贝和选择性拷贝，并将计数字段从 boxed `Long` 改为 primitive `long`。

## 如何达成设计目的

重构采用接口/实现分离模式：
1. `ContentStats` 接口移除 `extends StructLike`，改为纯数据模型：`fieldStats()` 返回 `Iterable`（原 `List`），新增 `type()`、`copy()`、`copy(Set<Integer>)`。
2. `FieldStats` 接口移除 `extends StructLike`，`type()` 返回 `Types.StructType`（原 `Type`），计数字段改为 primitive `long`，新增 `copy()`。
3. 新增 `ContentStatsStruct` 和 `FieldStatsStruct` 作为 `StructLike` 实现，内部维护字段 ID 到统计值的映射，通过 `posToId`/`posToOffset` 数组实现位置式访问。
4. 删除旧的 `BaseContentStats`、`BaseFieldStats`、`FieldStatistic` 实现类及其测试。
5. 更新 `StatsUtil`、`MetricsUtil`、`TrackedFileStruct` 等使用方适配新接口。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ContentStats.java` (+14/-4 lines, 重构)

**修改目的**：将 ContentStats 改为纯数据模型接口。

**工作逻辑**：移除 `extends StructLike`；`fieldStats()` 返回类型从 `List<FieldStats<?>>` 改为 `Iterable<FieldStats<?>>`；`statsStruct()` 重命名为 `type()`；新增 `copy()` 和 `copy(Set<Integer> fieldIds)` 方法。

### `core/src/main/java/org/apache/iceberg/ContentStatsStruct.java` (+129/-0 lines, 新文件)

**修改目的**：提供 ContentStats 的 StructLike 实现。

**工作逻辑**：
- 实现 `ContentStats, StructLike, Serializable`。
- 内部用 `Map<Integer, FieldStats<?>> idToFieldStats` 维护字段 ID 到统计的映射。
- `posToId` 数组将 StructLike 位置映射到字段 ID。
- `setStats(id, fieldStats)` 方法带校验：字段 ID 存在于 struct 中、与 FieldStats 的 fieldId 匹配。
- `copy()` 深拷贝所有字段统计；`copy(Set<Integer> fieldIds)` 仅拷贝指定字段（用于投影）。
- `get(pos, class)` / `set(pos, value)` 通过 posToId 映射实现位置式访问。

### `core/src/main/java/org/apache/iceberg/FieldStats.java` (+17/-4 lines, 重构)

**修改目的**：将 FieldStats 改为纯数据模型接口。

**工作逻辑**：移除 `extends StructLike`；`type()` 返回 `Types.StructType`（原 `Type`），注释说明可能是投影类型；`valueCount()`、`nullValueCount()`、`nanValueCount()` 从 `Long` 改为 `long`；新增 `copy()`。

### `core/src/main/java/org/apache/iceberg/FieldStatsStruct.java` (+225/-0 lines, 新文件)

**修改目的**：提供 FieldStats 的 StructLike 实现。

**工作逻辑**：
- 实现 `FieldStats<T>, StructLike, Serializable`。
- 维护 lowerBound、upperBound、tightBounds、valueCount、nullValueCount、nanValueCount、avgValueSize 字段。
- 二进制类型的边界值以 `byte[]` 内部存储，访问时包装为 `ByteBuffer`（避免序列化问题）。
- `fromFieldMetrics(FieldMetrics)` 从原始指标填充统计值。
- `posToOffset` 数组将 StructLike 位置映射到统计偏移量（通过 `StatsUtil.statOffset`）。
- `getOffset`/`setOffset` 通过 switch 实现位置式访问。
- `copy()` 深拷贝，对 `byte[]` 类型的边界值使用 `Arrays.copyOf`。

### 删除的文件

- `BaseContentStats.java` (-271 lines)：旧的基础实现类。
- `BaseFieldStats.java` (-363 lines)：旧的基础实现类。
- `FieldStatistic.java` (-218 lines)：旧的字段统计工具类。
- `TestContentStats.java` (-483 lines)、`TestFieldStats.java` (-244 lines)：旧测试。

### 新增测试

- `TestContentStatsStruct.java` (+260 lines)
- `TestFieldStatsStruct.java` (+397 lines)

### 其他修改的文件

- `StatsUtil.java` (+386/-... lines)：适配新接口，调整 ID 转换和偏移逻辑。
- `MetricsUtil.java`、`TrackedFileStruct.java`：适配新接口。
- `TestStatsUtil.java` (+957/-... lines)、`TestTrackedFile.java`、`TestTrackedFileAdapters.java`、`TestTrackedFileStruct.java`：适配新接口和构造方式。

## 总结

这是一个大规模的架构重构提交（净减少约 261 行，但涉及约 4200 行变更），将 `ContentStats` 和 `FieldStats` 的数据模型与 `StructLike` 序列化实现分离，与已有的 `TrackedFile`/`TrackedFileStruct` 模式统一。重构提升了代码的清晰度和可维护性，使数据模型可以独立于序列化表示演进。新增的 `copy()` 方法支持选择性投影拷贝，primitive `long` 计数避免了 boxed 类型的开销。删除了大量旧实现代码，由 Ryan Blue 完成。
