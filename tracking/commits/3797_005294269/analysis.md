# 提交 3797：API, Core, Orc: Implement project() for partition statistics scan API (#16569)

## 提交信息

- **序号**：3797 / 4088
- **哈希**：0052942699cd7b5e098d54a958827a911d28ac94
- **短哈希**：005294269
- **日期**：2026-05-29 14:22:01 +0200
- **作者**：gaborkaszab <gaborkaszab@gmail.com>
- **提交说明**：API, Core, Orc: Implement project() for partition statistics scan API (#16569)
- **PR/Issue**：#16569

## 总体目的

本提交为 Iceberg 的分区统计扫描 API（`PartitionStatisticsScan`）实现真正可用的列投影（projection）能力。在此提交之前，`BasePartitionStatisticsScan.project(Schema)` 方法只是直接抛出 `UnsupportedOperationException("Projection is not supported")`，调用方无法只读取部分统计字段，必须把整行统计字段全部读取。这意味着即使只需要少数几个统计指标（如数据记录数、最后更新快照 ID），扫描器也必须从底层文件读取全部 13 列统计字段，造成不必要的 I/O 与内存开销。

随着分区统计文件被引擎/维护任务频繁使用（如 expire snapshots、compaction 决策等），支持投影下推可以显著减少读取的数据量，提高查询与维护效率。本提交通过定义 `BASE_TYPE`、利用 `SupportsIndexProjection` 的基类能力，并借助 `TypeUtil.select` 在读取时按投影 schema 过滤，实现了真正的列投影功能。

此外，本提交还修复了一个潜在的向后兼容问题：当 V2 表的统计文件不包含 `dv_count` 字段时，`PartitionStatsHandler` 在合并统计时会因数组越界而失败。新增的边界检查使得旧统计文件能被安全处理。

## 如何达成设计目的

整体设计思路是复用现有的 `SupportsIndexProjection` 基类机制来支持投影读取。`BasePartitionStatistics` 改造为先声明一个静态的 `BASE_TYPE` StructType，把 13 个统计字段作为其字段；构造时不再用硬编码的 `STATS_COUNT=13`，而是改用 `BASE_TYPE.fields().size()`，并且投影构造函数改为接受 `BASE_TYPE` 与 projection 一起传给父类，由父类处理索引映射。在扫描端，`BasePartitionStatisticsScan` 增加一个 `projection` Schema 字段，`project()` 方法改为校验非空并保存 schema；在构建底层读取器时，使用 `TypeUtil.select(schema, TypeUtil.getProjectedIds(projection))` 计算出实际读取的 schema，再传给 `InternalData.read().project(readSchema)`，从而只读取需要的列。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BasePartitionStatistics.java` (+18/-3 lines)

**修改目的**：让 `BasePartitionStatistics` 真正支持投影 schema，替代原来硬编码的列数常量。

**工作逻辑**：
新增静态字段 `BASE_TYPE`，将所有 13 个统计字段（`EMPTY_PARTITION_FIELD`、`SPEC_ID`、`DATA_RECORD_COUNT` 等）组织为一个 `Types.StructType`。两个构造函数分别从 `BASE_TYPE.fields().size()` 取列数，并把 `BASE_TYPE` 与 projection 一并交给父类 `SupportsIndexProjection` 的构造函数，使父类能够基于基础类型与投影类型建立索引映射：

```java
BasePartitionStatistics(Types.StructType projection) {
  super(BASE_TYPE, projection);
}
```

### `core/src/main/java/org/apache/iceberg/BasePartitionStatisticsScan.java` (+8/-2 lines)

**修改目的**：实现扫描 API 的 `project()` 方法，并在实际读取时使用投影后的 schema。

**工作逻辑**：
- 增加 `private Schema projection;` 字段。
- 将原本抛异常的 `project(Schema newSchema)` 改为校验非空并保存：
```java
public PartitionStatisticsScan project(Schema newSchema) {
  Preconditions.checkArgument(newSchema != null, "Invalid projection schema: null");
  this.projection = newSchema;
  return this;
}
```
- 在 `scan()` 方法内，根据是否设置了投影来计算实际读取的 schema：
```java
Schema readSchema =
    projection == null ? schema : TypeUtil.select(schema, TypeUtil.getProjectedIds(projection));
```
然后将 `readSchema` 传给 `InternalData.read(...).project(readSchema)`，让底层读取器只读取投影列。

### `core/src/main/java/org/apache/iceberg/PartitionStatsHandler.java` (+4/-1 lines)

**修改目的**：修复 V2 表向后兼容问题，避免在统计文件 schema 不含 `dv_count` 列时数组越界。

**工作逻辑**：
原代码在合并统计时仅判断 `inputStats.dvCount() != null` 就写入 `DV_COUNT_POSITION`。但旧 V2 表的统计文件可能没有 `dv_count` 字段，导致 `targetStats` 数组长度不足而越界。新增对 `targetStats.size() > PartitionStatistics.DV_COUNT_POSITION` 的检查：

```java
// For backward compatibility, for V2 tables we have to check that the position of dv_count is
// within the schema of the stats file.
if (inputStats.dvCount() != null
    && targetStats.size() > PartitionStatistics.DV_COUNT_POSITION) {
```

### `core/src/test/java/org/apache/iceberg/PartitionStatisticsScanTestBase.java` (+140/-0 lines)

**修改目的**：为新增的投影能力添加通用测试用例，覆盖正常投影、空 schema 拒绝、未知字段忽略三种场景。

**工作逻辑**：
- `testProjectStatFields`：构造只包含 4 个字段的投影 schema，验证扫描返回的统计对象中只有这些字段被填充，其它字段为 null。
- `testProjectNullSchemaIsRejected`：调用 `project(null)` 应抛出 `IllegalArgumentException`。
- `testProjectIgnoresUnknownField`：投影 schema 中包含一个不存在的字段（id=9999），验证只有真实存在的字段被读取，未知字段被忽略，且其它字段为 null。

### `orc/src/test/java/org/apache/iceberg/orc/TestOrcPartitionStatisticsScan.java` (+14/-0 lines)

**修改目的**：覆盖 ORC 格式下投影功能的预期失败行为。

**工作逻辑**：
重写 `testProjectStatFields` 与 `testProjectIgnoresUnknownField`，因为 ORC 写入尚未注册为内部数据格式，所以这两个测试在 ORC 实现下应抛出 `UnsupportedOperationException("Cannot write using unregistered internal data format: ORC")`。这反映了 ORC 模块当前尚未支持写入分区统计文件，因此相关测试用例预期失败。

## 总结

本提交填补了分区统计扫描 API 在投影能力上的空白，使引擎和维护任务能够按需只读取必要的统计列，减少 I/O 开销。同时附带修复了 V2 表旧统计文件在合并 `dv_count` 时的越界问题，提升了向后兼容性。测试覆盖充分，包含正常、边界和 ORC 失败场景，体现了项目对多格式一致性的重视。
