# 提交 2856：Core, Spark: Handle unknown type during deletes (#14356)

## 提交信息

- **序号**：2856 / 4088
- **哈希**：c22bac8b170aa5e5c931c11c4c393c27245b20b9
- **短哈希**：c22bac8b1
- **日期**：2025-11-10 09:57:01 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core, Spark: Handle unknown type during deletes (#14356)
- **PR/Issue**：#14356

## 总体目的

这个提交解决了在删除操作中处理未知类型（UnknownType）的问题。在 Iceberg 中，当表的分区字段对应的源列被删除后，该分区字段的源类型会变为 null（因为 `schema.findType(field.sourceId())` 找不到已删除的列）。此前代码在这种情况下会抛出空指针异常，导致后续的删除操作（如 `deleteFile`）失败。

具体场景是：用户先删除分区字段（`DROP PARTITION FIELD`），再删除源列（`DROP COLUMN`），然后尝试删除数据文件。此时分区规格中仍保留旧的分区字段引用，但源列已不存在，导致类型解析失败。

该提交通过引入 `Types.UnknownType` 来优雅地处理这种情况，使删除操作能够正常进行。

## 如何达成设计目的

整体设计思路是在多个层面为 UnknownType 提供处理逻辑：

1. **PartitionSpec**：当源字段已删除（`findType` 返回 null）时，将类型设为 `UnknownType` 而非抛出异常。
2. **Comparators**：为 UnknownType 注册一个比较器（nullsFirst + naturalOrder），使分区值比较不会失败。
3. **Conversions**：为 UnknownType 的序列化/反序列化返回 null，而非抛出 UnsupportedOperationException。
4. **ValueWriters**：将 NullWriter 的泛型从 `Void` 改为 `Object`，使其能接受任意类型的值（包括未知类型的分区值），写入时仍然写 null。
5. **ManifestFileUtil**：在分区字段摘要的 `canContain` 检查中，对 UnknownType 直接返回 true（保守策略，确保不遗漏可能匹配的清单文件）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java` (+5/-0 lines)

**修改目的**：处理源字段已删除时类型为 null 的情况。

**工作逻辑**：在 `getReservedColumns` 方法（推测为分区类型推断逻辑）中，当 `schema.findType(field.sourceId())` 返回 null 时，将 `sourceType` 设为 `Types.UnknownType.get()`，而非继续使用 null 值。这样后续的 `field.transform().getResultType(sourceType)` 调用不会因 null 而失败。

### `api/src/main/java/org/apache/iceberg/types/Comparators.java` (+1/-0 lines)

**修改目的**：为 UnknownType 注册比较器。

**工作逻辑**：在比较器映射表中添加 `Types.UnknownType.get()` 对应的比较器 `Comparator.nullsFirst(Comparator.naturalOrder())`，使分区值比较逻辑在遇到 UnknownType 时不会因找不到比较器而失败。

### `api/src/main/java/org/apache/iceberg/types/Conversions.java` (+6/-0 lines)

**修改目的**：为 UnknownType 提供序列化和反序列化支持。

**工作逻辑**：在 `Conversions` 的序列化（`toByteBuffer`）和反序列化（`fromByteBuffer`）方法中，为 `UNKNOWN` 类型添加 case 分支，返回 null。这样在处理包含未知类型分区值的数据时不会抛出 `UnsupportedOperationException`。

### `core/src/main/java/org/apache/iceberg/avro/ValueWriters.java` (+7/-1 lines)

**修改目的**：使 NullWriter 能接受任意类型的值。

**工作逻辑**：将 `NullWriter` 的泛型参数从 `Void` 改为 `Object`，并相应调整实例创建逻辑（使用 `@SuppressWarnings` 抑制 unchecked 转换警告）。`write` 方法的参数类型也从 `Void` 改为 `Object`。这样当写入未知类型的分区值时，NullWriter 可以接受该值并写入 null。原先使用 `Void` 泛型会导致类型不匹配，无法接受非 Void 类型的值。

### `core/src/main/java/org/apache/iceberg/util/ManifestFileUtil.java` (+6/-0 lines)

**修改目的**：在清单文件匹配检查中处理 UnknownType。

**工作逻辑**：在 `FieldSummary` 类中新增 `type` 字段记录原始类型。在 `canContain` 方法中，当类型为 `UnknownType` 时直接返回 true。这是一个保守策略：当无法确定分区值的类型和范围时，假定清单文件可能包含目标数据，避免错误地跳过需要扫描的清单文件。此前如果 lowerBound 为 null（unknown 类型无法有序比较），可能会错误返回 false。

### `core/src/test/java/org/apache/iceberg/TestPartitioning.java` (+43/-0 lines)

**修改目的**：添加删除分区字段和源列后执行删除操作的测试。

**工作逻辑**：新增两个测试用例：
- `deleteFileAfterDeletingAllPartitionFields`：创建带分区表，添加数据文件，删除分区字段和源列，然后删除数据文件，验证操作成功。
- `deleteFileAfterDeletingOnePartitionField`：类似但涉及多分区字段场景，只删除其中一个分区字段和对应的源列。

### `core/src/test/java/org/apache/iceberg/util/TestManifestFileUtil.java` (+127/-0 lines)

**修改目的**：为 ManifestFileUtil 添加针对 UnknownType 的测试。

**工作逻辑**：新建测试文件，测试在分区字段类型为 UnknownType、NaN、null 以及混合类型时的 `canContainAny` 方法行为。验证所有这些边界情况下都正确返回 true。

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAlterTablePartitionFields.java` (+18/-6 lines)

**修改目的**：添加 Spark 端到端测试验证删除分区和列后执行 DELETE 操作。

**工作逻辑**：新增 `deleteAfterDroppingPartitionAndSourceColumn` 测试，通过 SQL 创建表、插入数据、添加/删除分区字段、删除列，然后执行 DELETE FROM 操作验证删除成功。同时将一处 `assertEquals` 改为 AssertJ 的 `containsExactlyElementsOf` 写法。

## 总结

这个提交解决了删除已删除列对应的分区字段后执行数据文件删除操作时出现的空指针异常问题。通过在多个层面（PartitionSpec、Comparators、Conversions、ValueWriters、ManifestFileUtil）为 UnknownType 提供处理逻辑，确保了删除操作的正常执行。修改涉及 4 个源文件和 4 个测试文件，是一个较为完整的功能修复，包含了核心逻辑修改和充分的测试覆盖。
