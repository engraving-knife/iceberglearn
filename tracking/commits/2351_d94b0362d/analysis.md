# 提交 2351：Spark 4.0: Row Lineage support (#13310)

## 提交信息

- **序号**：2351 / 4088
- **哈希**：d94b0362de88bf0adf2d21fccd12d963cf1b2273
- **短哈希**：d94b0362d
- **日期**：2025-07-14 13:31:38 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 4.0: Row Lineage support (#13310)
- **PR/Issue**：#13310

## 总体目的

这个提交为 Spark 4.0 集成引入了行级血缘（Row Lineage）支持，是 Iceberg 与 Spark 4.0 新增的行级血缘能力对接的核心改动，总计约 1058 行新增代码。

背景：Spark 4.0 引入了行级血缘（Row Lineage）功能，允许在数据写入和更新过程中追踪每一行的来源和变更历史。具体而言，Spark 4.0 的 `MetadataColumn` 接口新增了 `preserveOnReinsert`、`preserveOnUpdate`、`preserveOnDelete` 三个语义标志，以及 `metadataInJSON()` 方法，用于声明元数据列在行级操作（INSERT/MERGE/UPDATE/DELETE）中的保留行为。当引擎执行 COPY ON WRITE 或 MERGE ON READ 操作时，可以根据这些标志决定是否保留行的原始元数据（如 row ID 和 last-updated sequence number）。

Iceberg 的 `MetadataColumns` 已定义了 `ROW_ID` 和 `LAST_UPDATED_SEQUENCE_NUMBER` 等元数据列。本提交的目标是让 Spark 4.0 能正确识别这些 Iceberg 元数据列的行级血缘语义，并在写入路径中提取和传递行血缘信息，使行在经过 MERGE/UPDATE/DELETE 等操作后仍能保持可追踪的 lineage。

## 如何达成设计目的

整体设计分为以下几个关键部分：

1. **元数据列血缘语义声明**：通过 `SparkMetadataColumn` 的 Builder 模式，为 Iceberg 元数据列（如 ROW_ID、LAST_UPDATED_SEQUENCE_NUMBER）声明 `preserveOnReinsert`、`preserveOnUpdate`、`preserveOnDelete` 标志，并通过 `metadataInJSON()` 序列化给 Spark 引擎。
2. **行血缘提取器**：新增 `ExtractRowLineage` 类，从写入路径的元数据行中提取 ROW_ID 和 LAST_UPDATED_SEQUENCE_NUMBER，构造血缘投影行。
3. **写入路径集成**：在 `SparkWrite`、`SparkCopyOnWriteOperation`、`SparkPositionDeltaWrite`、`SparkPositionDeltaWriteBuilder`、`SparkWriteBuilder` 中将行血缘提取器接入数据写入流程，使写入的数据文件能携带血缘信息。
4. **SparkTable 元数据列暴露**：在 `SparkTable` 中通过 Builder 构建带血缘语义的元数据列并暴露给 Spark。
5. **Changelog 表适配**：`SparkChangelogTable` 适配新的元数据列机制。
6. **测试体系**：新增 `TestRowLevelOperationsWithLineage` 抽象基类及其 CoW/MoR 两个子类，覆盖多种 MERGE/UPDATE/DELETE 场景下的行血缘正确性。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/ExtractRowLineage.java` (+92/-0 lines, 新文件)

**修改目的**：实现从写入元数据行中提取行血缘信息的逻辑。

**工作逻辑**：`ExtractRowLineage` 实现 `Function<InternalRow, InternalRow>`，负责从 Spark 的元数据行中投影出 `ROW_ID` 和 `LAST_UPDATED_SEQUENCE_NUMBER` 两个字段。构造时检查写 schema 是否要求行血缘（即是否包含 ROW_ID 字段）。`apply()` 方法中：若不要求血缘则返回 null；若元数据行为 null 但需要血缘则返回空血缘行；否则使用缓存的 `ProjectingInternalRow` 按字段序号投影出血缘行。序号查找通过遍历元数据行 schema 字段名完成，并缓存以提升性能。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkMetadataColumn.java` (+82/-2 lines)

**修改目的**：为 Iceberg 元数据列声明 Spark 4.0 的行血缘保留语义。

**工作逻辑**：引入 Builder 模式，新增 `preserveOnReinsert`、`preserveOnUpdate`、`preserveOnDelete` 三个布尔字段（默认值取自 `MetadataColumn` 接口常量）。新增 `metadataInJSON()` 方法，将这三个标志序列化为 JSON 元数据返回给 Spark 引擎，使引擎在行级操作时知道如何处理这些元数据列。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkWrite.java` (+54/-2 lines)

**修改目的**：将行血缘提取器集成到 Spark 写入路径。

**工作逻辑**：在数据写入器创建处实例化 `ExtractRowLineage`，并将其应用于未分区和分区写入器。使用 `JoinedRow` 将数据行与血缘行拼接，使写入的数据文件携带血缘信息。新增对 `preserveOnReinsert`/`preserveOnUpdate` 等属性的传递逻辑。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkTable.java` (+54/-2 lines)

**修改目的**：通过 Builder 构建 Iceberg 元数据列并暴露给 Spark。

**工作逻辑**：将元数据列的构建改为使用 `SparkMetadataColumn.Builder`，为 ROW_ID 等列设置正确的 `preserveOnReinsert`/`preserveOnUpdate`/`preserveOnDelete` 标志。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkChangelogTable.java` (+30/-2 lines)

**修改目的**：适配 changelog 表的元数据列构建。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkCopyOnWriteOperation.java` (+20/-2 lines)

**修改目的**：在 CoW 操作中集成行血缘处理。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaOperation.java` (+16/-1 lines)

**修改目的**：在 Position Delta 操作中集成行血缘处理。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkPositionDeltaWrite.java` (+73/-2 lines) 及 `SparkPositionDeltaWriteBuilder.java` (+14/-0 lines)

**修改目的**：在 Position Delta 写入路径中集成行血缘提取和传递。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkWriteBuilder.java` (+41/-2 lines)

**修改目的**：在写入构建器中支持行血缘相关的写入选项。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/ManifestFileBean.java` (+11/-0 lines)

**修改目的**：为 ManifestFile Bean 添加必要字段以支持血缘相关操作。

### 测试文件（+618/-0 lines, 新文件）

**修改目的**：建立行血缘测试体系。

**工作逻辑**：
- `TestRowLevelOperationsWithLineage.java`：抽象基类，继承 `SparkRowLevelOperationsTestBase`，定义含 ROW_ID 和 LAST_UPDATED_SEQUENCE_NUMBER 的 schema，覆盖 MERGE INTO（含 matched 和 non-matched）、UPDATE、DELETE 等场景，验证行血缘在操作后的正确性（行携带正确 sequence number 和 row ID）。
- `TestCopyOnWriteWithLineage.java`：设置 CoW 模式表属性的子类。
- `TestMergeOnReadWithLineage.java`：设置 MoR 模式表属性的子类。

## 总结

该提交是 Spark 4.0 行级血缘支持的核心实现，通过在元数据列声明血缘保留语义、新增行血缘提取器、集成到写入路径和行级操作路径，使 Iceberg 表的行在经过 MERGE/UPDATE/DELETE 后仍保持可追踪的 lineage。配套的测试体系覆盖了 CoW 和 MoR 两种模式下的多种行级操作场景，验证了血缘信息的正确性。这是 Iceberg 与 Spark 4.0 新能力对接的重要功能提交。
