# 提交 1551 8e2ffb35d 分析

## 提交信息
- 哈希：8e2ffb35da2d4c5059e96cb78a30fd8c54cfbedf
- 日期：2025-01-06（Mon Jan 6 18:09:13 2025 +0800）
- 作者：big face cat <731030576@qq.com>
- 消息：Flink: Backport #11557 to Flink 1.19 and 1.18 (#11834)

## 总体目的

将 main 分支上修复的 PR #11557 回迁到 Flink 1.18 和 1.19 两个版本模块，为 range distribution 模式下的 `StatisticsOrRecord` 流提供自定义的 `TypeInformation` 实现，替代原先使用 `TypeInformation.of(StatisticsOrRecord.class)` 生成的通用类型信息。

在 Flink 的类型系统中，`TypeInformation` 描述了数据流的序列化方式、分区语义和状态管理策略。当使用 `TypeInformation.of(SomeClass.class)` 时，如果该类没有注册自定义的 `TypeInfoFactory`，Flink 会回退到使用 Kryo 序列化器。Kryo 是一种通用的对象序列化框架，虽然使用方便，但存在以下问题：序列化效率低于专用序列化器、不支持状态迁移（state schema evolution）、可能导致序列化结果不稳定、在 Flink 的托管状态中不被推荐使用。

`StatisticsOrRecord` 是 Iceberg Flink sink 在 range distribution 模式下使用的复合数据类型，它既可以承载统计信息（`GlobalStatistics`），也可以承载数据记录（`RowData`）。原先使用 `TypeInformation.of(StatisticsOrRecord.class)` 会导致 Flink 使用 Kryo 序列化这个对象，效率低下且不利于状态管理。

本次回迁引入 `StatisticsOrRecordTypeInformation`，它提供了专用的 `StatisticsOrRecordSerializer`（由 `GlobalStatisticsSerializer` 和 `RowData` 序列化器组合而成），确保 `StatisticsOrRecord` 在 Flink 数据流中以高效且可管理的方式序列化。

## 如何达成设计目的

在 Flink 1.18 和 1.19 模块中各新增一个 `StatisticsOrRecordTypeInformation` 类（两个版本的实现略有差异，适配 Flink API 变化），并修改 `FlinkSink` 中 range shuffle 算子的类型信息声明，从 `TypeInformation.of(StatisticsOrRecord.class)` 改为使用新的自定义 `TypeInformation`。

### 修改详情

#### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsOrRecordTypeInformation.java`（新增文件，v1.19 同理）

**修改目的**：提供 `StatisticsOrRecord` 的自定义 `TypeInformation`，替代 Kryo 序列化。

**工作逻辑**：

该类继承 `TypeInformation<StatisticsOrRecord>`，持有三个字段：
- `TypeInformation<RowData> rowTypeInformation`：由 Flink `RowType` 转换而来，用于创建 RowData 序列化器。
- `SortOrder sortOrder`：Iceberg 排序顺序。
- `GlobalStatisticsSerializer globalStatisticsSerializer`：由 `SortKeySerializer` 包装而成的全局统计信息序列化器。

构造函数接收 `RowType flinkRowType`、`Schema schema`、`SortOrder sortOrder`，通过 `FlinkCompatibilityUtil.toTypeInfo(flinkRowType)` 将 Flink 行类型转换为 TypeInformation，并创建 `GlobalStatisticsSerializer(new SortKeySerializer(schema, sortOrder))`。

核心方法 `createSerializer(ExecutionConfig config)`：
- 从 `rowTypeInformation` 创建 `RowData` 的 `TypeSerializer`。
- 用 `globalStatisticsSerializer` 和 `recordSerializer` 组合创建 `StatisticsOrRecordSerializer` 并返回。

此外实现了 `equals`、`hashCode`、`canEqual` 方法，比较 `rowTypeInformation`、`sortOrder` 和 `globalStatisticsSerializer` 三个字段，确保 Flink 类型系统能正确判断两个 `StatisticsOrRecordTypeInformation` 实例是否等价。其余方法（`isBasicType`、`isTupleType`、`getArity` 等）返回固定的类型描述值。

**v1.18 与 v1.19 的差异**：Flink 1.19 引入了 `SerializerConfig` 作为 `ExecutionConfig` 中序列化配置的替代。因此 v1.19 版本额外实现了 `createSerializer(SerializerConfig config)` 方法，而 `createSerializer(ExecutionConfig config)` 委托给前者（`config.getSerializerConfig()`）。v1.18 版本仅实现 `createSerializer(ExecutionConfig config)`。

#### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java`（v1.19 同理）

**修改目的**：在 range shuffle 算子中使用自定义 TypeInformation。

**工作逻辑**：在构建 range shuffle 数据流的代码中：
- 新增 import `StatisticsOrRecordTypeInformation`。
- 创建 `StatisticsOrRecordTypeInformation` 实例：
  ```java
  StatisticsOrRecordTypeInformation statisticsOrRecordTypeInformation =
      new StatisticsOrRecordTypeInformation(flinkRowType, iSchema, sortOrder);
  ```
- 将 `input.transform(operatorName("range-shuffle"), TypeInformation.of(StatisticsOrRecord.class), ...)` 改为 `input.transform(operatorName("range-shuffle"), statisticsOrRecordTypeInformation, ...)`。

这样 Flink 在 shuffle 和 checkpoint 时会使用 `StatisticsOrRecordSerializer` 而非 Kryo，提升序列化效率并支持状态迁移。

#### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestStatisticsOrRecordTypeInformation.java`（新增文件，v1.19 同理）

**修改目的**：验证 `StatisticsOrRecordTypeInformation` 的类型信息契约。

**工作逻辑**：继承 Flink 的 `TypeInformationTestBase<StatisticsOrRecordTypeInformation>`，提供两个使用不同 SortOrder 的测试数据实例。该基类会自动验证 TypeInformation 的 `equals`、`hashCode`、`canEqual` 等契约方法的行为正确性。

## 小结

- **成效**：为 range distribution 模式下的 `StatisticsOrRecord` 流提供了自定义的 `TypeInformation`，替代了原先基于 Kryo 的通用序列化方式。新的 `StatisticsOrRecordTypeInformation` 使用专用的 `StatisticsOrRecordSerializer`（组合 `GlobalStatisticsSerializer` 和 `RowData` 序列化器），提升了序列化效率、支持 Flink 托管状态的状态迁移，并使类型系统更透明。
- **影响范围**：涉及 Flink 1.18 和 1.19 两个模块的 6 个文件（每模块 3 个：1 个新增源码 + 1 个修改源码 + 1 个新增测试），净增 322 行。改动仅影响 range distribution 模式的数据流类型声明，不影响其他模式。
- **回迁到 1.4.x 的注意事项**：这是性能和正确性改进的回迁。如果 1.4.x 的 Flink 1.18/1.19 模块仍使用 `TypeInformation.of(StatisticsOrRecord.class)`，**建议回迁**以获得更好的序列化效率和状态管理支持。但需注意，此改动可能与提交 1547（SortKeySerializer 版本升级）存在依赖关系——`StatisticsOrRecordTypeInformation` 内部创建了 `SortKeySerializer`，两处改动应一起回迁以确保一致性。
