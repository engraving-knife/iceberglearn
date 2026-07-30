# 提交 0488：Spark 3.4: Move the Writer to a visitor (#9673)

## 提交信息

| 字段 | 内容 |
| --- | --- |
| 序号 | 0488 |
| 完整哈希 | 4446e4f0abe5c040712ea103594ec31ad8ce902e |
| 短哈希 | 4446e4f0a |
| 日期 | 2024-02-07（Wed Feb 7 09:44:23 2024 +0100） |
| 作者 | Fokko Driesprong <fokko@apache.org> |
| 说明 | Spark 3.4: Move the Writer to a visitor (#9673) |
| PR | #9673 |
| 上游 PR | #9440（本提交为其回port） |

提交统计：1 个文件修改，119 行新增，49 行删除。

涉及文件：`spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java`

## 总体目的

本提交是上游 PR #9440 向 Spark 3.4 模块的回port，与紧邻的前一个提交 0487（Spark 3.3 版本，提交时间仅相差 42 秒）是同一重构的姊妹提交，二者改动内容逐字节一致，仅文件路径不同（本提交针对 `spark/v3.4/spark/...`，0487 针对 `spark/v3.3/spark/...`）。Iceberg 为每个受支持的 Spark 大版本（3.3、3.4、3.5）维护一份独立的 `SparkParquetWriters.java` 副本（因为不同 Spark 版本的 Catalyst 内部 API 与类型系统存在差异，无法共享同一份源文件），因此同一重构需要按版本分别落地。

重构的核心动机与 0487 完全相同：把 `SparkParquetWriters` 中 Parquet 写入器的构建逻辑从基于已过时的 `primitive.getOriginalType()` 的 `switch` 分发，改造为基于 Parquet 新版 `LogicalTypeAnnotation` 的访问者（Visitor）模式分发，跟进 Parquet 自身的 API 演进。`OriginalType` 枚举已被 Parquet 标记为过时，上游推荐改用 `LogicalTypeAnnotation` 类层次结构配合 `LogicalTypeAnnotationVisitor` 进行类型安全的分发。旧实现还把 INT8/INT16 的判定耦合到 Spark 的 `DataType`（`ByteType`/`ShortType`）上，重构后改由 Parquet schema 自身的 `IntLogicalTypeAnnotation.getBitWidth()` 决定，更自然且解耦；同时顺带补齐了旧实现缺失的 UUID 逻辑类型支持。

重构后所有逻辑类型到 `ParquetValueWriter` 的映射集中在一个独立的访问者类 `LogicalTypeAnnotationParquetValueWriterVisitor` 中，每个 `LogicalTypeAnnotation` 子类型对应一个 `visit` 重载，返回 `Optional<ParquetValueWriter<?>>`，未来新增逻辑类型只需新增一个 `visit` 重载而不必修改集中的 `switch`。两个 Spark 版本的提交分开而非合并，是因为 Iceberg 的多版本源码副本策略要求各版本目录独立修改、独立 PR、独立回port 审批。

## 如何达成设计目的

实现路径与 0487 完全一致，仅作用于 Spark 3.4 副本：首先新增实现了 `LogicalTypeAnnotation.LogicalTypeAnnotationVisitor<ParquetValueWriter<?>>` 的静态内部类 `LogicalTypeAnnotationParquetValueWriterVisitor`，为每种 `LogicalTypeAnnotation` 子类型实现 `visit` 方法返回对应写入器；其次把 `primitive(DataType sType, PrimitiveType primitive)` 中的 `switch (primitive.getOriginalType())` 替换为 `primitive.getLogicalTypeAnnotation().accept(visitor)` 配合 `orElseThrow`；最后删除 `ints(DataType type, ColumnDescriptor desc)` 辅助方法及其 `ByteType`/`ShortType` 依赖，改由访问者按 `bitWidth` 分派，INT32 无逻辑类型回退路径直接调用 `ParquetValueWriters.ints(desc)`。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java`

**修改目的**：将 Spark 3.4 副本的 Parquet 写入器逻辑类型分发从过时的 `OriginalType` switch 改造为基于 `LogicalTypeAnnotation` 的访问者模式，与 0487 对 Spark 3.3 的改造保持一致，使两个版本的实现同步演进。

**工作逻辑**（与 0487 逐行一致，此处仅说明改动结构）：

1. **导入调整**：新增 `java.util.Optional`；移除 `ByteType`、`ShortType`。
2. **新增访问者类 `LogicalTypeAnnotationParquetValueWriterVisitor`**：承载各逻辑类型到写入器的映射——
   - String/Enum/JSON → `utf8Strings(desc)`
   - UUID → `uuids(desc)`（新增支持）
   - Map/List → `super.visit(...)`（返回空，组合类型不在 primitive 路径处理）
   - Decimal → 按 `PrimitiveTypeName` 分派 `decimalAsInteger`/`decimalAsLong`/`decimalAsFixed`，否则空
   - Date → `ParquetValueWriters.ints(desc)`
   - Time/Timestamp → 仅 `MICROS` 单位返回 `longs(desc)`，否则空
   - Int → 按 `bitWidth`（<=8 tinyints / <=16 shorts / <=32 ints / else longs）
   - Bson → `byteArrays(desc)`
3. **`primitive` 方法改造**：`if (logicalTypeAnnotation != null) return logicalTypeAnnotation.accept(visitor).orElseThrow(...)`，替换原 `switch (primitive.getOriginalType())` 整块。
4. **回退分支**：`INT32` case 由 `ints(sType, desc)` 改为 `ParquetValueWriters.ints(desc)`。
5. **删除 `ints(DataType, ColumnDescriptor)` 辅助方法**：其职责迁移到访问者的 Int 分支。

由于改动与 0487 逐字节相同（仅目录前缀 `v3.4` vs `v3.3`），详细的逐分支映射说明参见 0487 的分析文档。

## 小结

本提交是上游 #9440 向 Spark 3.4 模块的回port，与 0487（Spark 3.3 版本）是同一重构的姊妹提交，改动逐字节一致。把 `SparkParquetWriters` 中 Parquet 写入器的逻辑类型分发从过时的 `OriginalType` switch 重构为基于 `LogicalTypeAnnotation` 的访问者模式，新增 `LogicalTypeAnnotationParquetValueWriterVisitor` 集中承载映射，删除依赖 Spark `DataType` 的 `ints` 辅助方法改由访问者按 `bitWidth` 决定，并补齐 UUID 支持。Iceberg 因多版本源码副本策略需对各 Spark 版本目录分别落地，故与 0487 分开成两个 PR。属于纯内部重构，写入行为对外等价，回迁 1.4.x 风险较低但需配合测试验证。
