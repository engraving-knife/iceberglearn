# 提交序号 1507 短哈希 e3628c18c 分析

## 提交信息
- 哈希：e3628c18c2009e796ee404ac31c994e3a90b268b
- 日期：2024-12-18
- 作者：big face cat <731030576@qq.com>（合作者：huyuanfeng <huyuanfeng@huya.com>）
- 消息：Flink: make `StatisticsOrRecord` to be correctly serialized and deser… (#11557)

## 总体目的

本提交修复了 Flink Iceberg sink 在使用范围分布（range distribution）时，`StatisticsOrRecord` 对象无法被正确序列化和反序列化的问题。当 Flink 作业在范围分布 shuffle 阶段传输 `StatisticsOrRecord` 数据时，需要通过 Flink 的类型系统对其进行序列化。修改前，代码使用 `TypeInformation.of(StatisticsOrRecord.class)` 来获取类型信息，这种通用方式无法为 `StatisticsOrRecord` 内部包含的 `RowData`（记录）和 `GlobalStatistics`（统计信息）提供正确的序列化器，导致 shuffle 数据传输出现序列化错误。

`StatisticsOrRecord` 是一个联合类型（union type），它既可以承载范围分布所需的统计信息（`GlobalStatistics`），也可以承载实际的数据记录（`RowData`）。这两类内容的序列化方式完全不同：`RowData` 需要使用 Flink 的 `RowDataSerializer`（依赖于具体的行类型 `RowType`），而 `GlobalStatistics` 需要使用 Iceberg 自定义的 `GlobalStatisticsSerializer`（依赖于 schema 和 sortOrder）。通用的 `TypeInformation.of()` 无法感知这些依赖，因而无法创建正确的 `TypeSerializer`。

本提交通过引入一个自定义的 `StatisticsOrRecordTypeInformation`，将 `RowType`、`Schema`、`SortOrder` 等上下文信息封装其中，从而能在 `createSerializer` 时正确构建包含上述两个序列化器的 `StatisticsOrRecordSerializer`，保证 shuffle 过程中数据的正确传输。

## 如何达成设计目的

本提交通过新增 `StatisticsOrRecordTypeInformation` 类并在 `FlinkSink` 中使用它替代 `TypeInformation.of(StatisticsOrRecord.class)` 来达成目的。同时新增测试验证该 TypeInformation 的正确性。

### 修改详情

#### flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/StatisticsOrRecordTypeInformation.java（新增）

这是本次提交的核心新增类，继承自 Flink 的 `TypeInformation<StatisticsOrRecord>`。其工作逻辑：

1. 构造时接收 `RowType flinkRowType`、`Schema schema`、`SortOrder sortOrder` 三个参数，这些是正确序列化 `StatisticsOrRecord` 所需的全部上下文。

2. 在构造器中预先构建两个关键组件：
   - `rowTypeInformation`：通过 `FlinkCompatibilityUtil.toTypeInfo(flinkRowType)` 将 Flink 行类型转为 TypeInformation，用于后续创建 `RowData` 的序列化器。
   - `globalStatisticsSerializer`：通过 `new GlobalStatisticsSerializer(new SortKeySerializer(schema, sortOrder))` 创建统计信息的序列化器，其中 `SortKeySerializer` 依赖 schema 和 sortOrder 来正确序列化排序键。

3. `createSerializer(SerializerConfig config)` 是关键方法：先用 `rowTypeInformation.createSerializer(config)` 创建 `RowData` 的序列化器，再与预建的 `globalStatisticsSerializer` 一起构造 `StatisticsOrRecordSerializer`。这样得到的序列化器能正确处理 `StatisticsOrRecord` 的两种可能内容。

4. 实现了标准的 TypeInformation 方法：`isBasicType`/`isTupleType` 返回 false，`getArity`/`getTotalFields` 返回 1，`isKeyType` 返回 false。

5. 重写 `equals`/`hashCode`/`canEqual`：基于 `rowTypeInformation`、`sortOrder`、`globalStatisticsSerializer` 三个字段判断相等性。这一点很重要——Flink 在作业图优化阶段会基于 TypeInformation 的相等性来合并/校验算子，若不正确实现相等性，可能导致类型检查失败。

#### flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java

该文件是 Flink Iceberg sink 的主入口。修改点在于范围分布流的 transform 调用：

1. 新增 import `StatisticsOrRecordTypeInformation`。

2. 在构建 range-shuffle 流时，原先使用 `TypeInformation.of(StatisticsOrRecord.class)` 作为 transform 的输出类型，现改为先构造 `StatisticsOrRecordTypeInformation` 实例（传入 `flinkRowType`、`iSchema`、`sortOrder`），再将其传入 `transform` 方法。这样 Flink 在该算子输出端会使用自定义的 TypeInformation，从而在 shuffle 时正确序列化 `StatisticsOrRecord`。

#### flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/shuffle/TestStatisticsOrRecordTypeInformation.java（新增）

该测试继承自 Flink 的 `TypeInformationTestBase<StatisticsOrRecordTypeInformation>`，这是 Flink 提供的用于验证 TypeInformation 实现合规性的标准测试基类。它通过反射等机制检查 TypeInformation 的 `equals`/`hashCode`/`canEqual` 等方法是否正确实现。

测试中定义了带时间戳、UUID、字符串字段的 schema，以及两个不同的 SortOrder（分别按 ts 和 data 排序），并提供两个不同的 `StatisticsOrRecordTypeInformation` 实例作为测试数据。两个实例的 sortOrder 不同，因此应被认为不相等——这验证了相等性判断能正确区分不同排序配置的 TypeInformation。

## 小结

本提交修复了 Flink Iceberg sink 范围分布功能中 `StatisticsOrRecord` 序列化不正确的问题。根因是原先使用通用的 `TypeInformation.of()` 无法为联合类型 `StatisticsOrRecord`（内含 `RowData` 和 `GlobalStatistics` 两种内容）提供正确的序列化器。解决方案是引入自定义的 `StatisticsOrRecordTypeInformation`，封装行类型、schema、sortOrder 上下文，在 `createSerializer` 时正确构建组合序列化器，并正确实现相等性方法以满足 Flink 作业图优化的要求。配套的基于 Flink 标准测试基类的测试保证了 TypeInformation 实现的合规性。该修复对保障 Flink 范围分布写入的数据正确性具有重要意义。
