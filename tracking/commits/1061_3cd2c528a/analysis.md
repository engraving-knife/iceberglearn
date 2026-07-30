# 提交 1061：Core: add JSON serialization for BaseFilesTable.ManifestReadTask, AllManifestsTable.ManifestListReadTask, and BaseEntriesTable.ManifestReadTask

## 提交信息

- **序号**：1061 / 4088
- **哈希**：3cd2c528a83e14e6d50ca7c9c01b00ea53e5276e
- **短哈希**：3cd2c528a
- **日期**：2024-08-15（Thu Aug 15 09:40:33 2024 -0700）
- **作者**：Steven Zhen Wu <stevenz3wu@gmail.com>
- **提交说明**：Core: add JSON serialization for BaseFilesTable.ManifestReadTask, AllManifestsTable.ManifestListReadTask, and BaseEntriesTable.ManifestReadTask
- **PR/Issue**：#10735

## 总体目的

Iceberg 的元数据表（`files`、`entries`、`all_manifests` 等）在执行扫描时会生成对应类型的 `FileScanTask`：

- `BaseFilesTable.ManifestReadTask`（`files`/`all_data_files`/`all_delete_files` 等的扫描任务，负责读取单个 manifest 文件并输出 data_file 行）
- `BaseEntriesTable.ManifestReadTask`（`entries`/`all_entries` 的扫描任务，输出 manifest entry 行）
- `AllManifestsTable.ManifestListReadTask`（`all_manifests` 的扫描任务，输出 manifest file 行）

此前 Iceberg 已经为 `FileScanTask` 与 `StaticDataTask` 提供了 JSON 序列化（见 `ScanTaskParser`），用于在分布式引擎（如 Spark/Flink）的 driver 与 executor 之间传输任务。但上述三类元数据表任务没有 JSON 序列化支持，导致引擎在执行元数据表查询时无法把任务序列化后下发到 executor，只能退回到非序列化路径或就地执行，限制了元数据表查询在分布式场景下的可扩展性。

本提交的目的是为这三类元数据表任务补齐 JSON 序列化与反序列化能力，使它们与 `FileScanTask`/`DataTask` 一样可经由 `ScanTaskParser.toJson(...) / fromJson(...)` 完整地序列化为 JSON 文本并在远端重建。这样元数据表查询也能享受分布式执行带来的性能优势，同时为后续远程 catalog（REST Catalog）等场景下传输元数据表任务打基础。

## 如何达成设计目的

整体设计沿用已有的 `ScanTaskParser` 派发模式：

1. 在 `ScanTaskParser.TaskType` 枚举里新增三种任务类型常量：`FILES_TABLE_TASK`、`ALL_MANIFESTS_TABLE_TASK`、`MANIFEST_ENTRIES_TABLE_TASK`；
2. 为每种任务类型新增一个独立的 Parser 类（包私有，`FilesTableTaskParser`、`AllManifestsTableTaskParser`、`ManifestEntriesTableTaskParser`），各自实现 `toJson(task, generator)` 与 `fromJson(jsonNode)` 静态方法；
3. 新增一个通用的 `ManifestFileParser`，用于把 `ManifestFile` 对象（含 `PartitionFieldSummary` 列表、keyMetadata 等）序列化为 JSON，因为 `BaseFilesTable.ManifestReadTask` 与 `BaseEntriesTable.ManifestReadTask` 都包含 `ManifestFile` 字段需要序列化；
4. 修改三个 Task 类：把构造函数从"接受 `Table`"改为"接受 `dataTableSchema/io/specsById/...` 等显式依赖"，并提供包私有的 getter（`io()`、`specsById()`、`dataTableSchema()`、`projection()`、`manifest()`、`manifestListLocation()`、`referenceSnapshotId()`、`residual()` 等），让 Parser 能读取与写入这些字段；
5. 在 `GenericManifestFile` 上新增一个包私有构造函数，参数顺序与 `ManifestFileParser.fromJson` 重建对象时的字段顺序匹配（避免与已有 public 构造函数签名冲突）；
6. 把 `FileIOParser.toJson(FileIO, JsonGenerator)` 与 `FileIOParser.fromJson(JsonNode, Object)` 由 `private` 提升为 `public`，让新 Parser 能复用 FileIO 的序列化逻辑；
7. 把 `TableMetadata.indexSpecs` 的私有实现移到 `PartitionUtil.indexSpecs` 并改为 `public`，让 Parser 在反序列化时可以由 `List<PartitionSpec>` 重建 `Map<Integer, PartitionSpec>`；
8. 在 `.palantir/revapi.yml` 中接受 `GenericManifestFile` 的 `java.class.defaultSerializationChanged` 中断（新增构造函数导致默认序列化 UID 变化），justification 为"跨版本序列化不支持"；
9. 新增三个测试类 `TestFilesTableTaskParser`、`TestAllManifestsTableTaskParser`、`TestManifestFileParser`，覆盖 null 校验、非法 JSON 节点、完整 round-trip 等场景。

设计上把"任务序列化所需的状态"从对 `Table` 的依赖中解耦出来：原来 Task 构造时直接接收 `Table`，从 `table.io()`、`table.specs()`、`table.schema()` 取依赖；现在改为直接接收这些依赖对象。这让 Parser 在反序列化时无需持有 `Table` 引用即可重建任务，也使得任务对象在序列化路径上更"自包含"。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ScanTaskParser.java`

**修改目的**：在统一的 `ScanTaskParser` 入口派发新增的三种元数据表任务类型。

**工作逻辑**：
- 在 `TaskType` 枚举中新增三个值：`FILES_TABLE_TASK("files-table-task")`、`ALL_MANIFESTS_TABLE_TASK("all-manifests-table-task")`、`MANIFEST_ENTRIES_TABLE_TASK("manifest-entries-task")`；
- 在 `fromString` 中追加对三个新值的识别；
- 在 `toJson(FileScanTask, JsonGenerator, boolean)` 中追加三个 `instanceof` 分支，分别调用对应 Parser 的 `toJson`：
  - `BaseFilesTable.ManifestReadTask` → `FilesTableTaskParser.toJson`
  - `AllManifestsTable.ManifestListReadTask` → `AllManifestsTableTaskParser.toJson`
  - `BaseEntriesTable.ManifestReadTask` → `ManifestEntriesTableTaskParser.toJson`
- 在 `fromJson(JsonNode, boolean)` 的 switch 中新增三个 case，分别调用对应 Parser 的 `fromJson`。

注意 `instanceof` 分支顺序很关键：必须放在 `BaseFileScanTask` 分支之前，因为元数据表任务可能继承自 `BaseFileScanTask`（如 `ManifestReadTask extends BaseFileScanTask`），先匹配子类才能正确派发。

### `core/src/main/java/org/apache/iceberg/FilesTableTaskParser.java`（新增）

**修改目的**：为 `BaseFilesTable.ManifestReadTask` 提供 JSON 序列化/反序列化。

**工作逻辑**：
- 定义字段名常量：`file-io`、`partition-specs`、`schema`（数据表 schema）、`projection`（投影 schema）、`residual-filter`、`manifest-file`；
- `toJson(task, generator)`：依次写出 `schema`（SchemaParser）、`projection`（SchemaParser）、`file-io`（FileIOParser）、`partition-specs` 数组（每个用 PartitionSpecParser）、`residual-filter`（ExpressionParser）、`manifest-file`（ManifestFileParser）；
- `fromJson(jsonNode)`：按相同字段名读取，调用各 Parser 的 `fromJson` 还原；其中 `partition-specs` 数组需要逐项 `PartitionSpecParser.fromJson(dataTableSchema, specNode)`（依赖 dataTableSchema 来解析分区字段引用），再用 `PartitionUtil.indexSpecs(...)` 转为 `Map<Integer, PartitionSpec>`；最后用 `new BaseFilesTable.ManifestReadTask(dataTableSchema, fileIO, specsById, manifestFile, projection, residualFilter)` 重建任务。

### `core/src/main/java/org/apache/iceberg/AllManifestsTableTaskParser.java`（新增）

**修改目的**：为 `AllManifestsTable.ManifestListReadTask` 提供 JSON 序列化/反序列化。

**工作逻辑**：与 `FilesTableTaskParser` 类似，但字段集不同——`ManifestListReadTask` 不含 `ManifestFile`，而是含 `manifest-list-location`（字符串路径）与 `reference-snapshot-id`（long），还多了 `data-table-schema` 字段。字段名常量：`data-table-schema`、`file-io`、`schema`（任务输出 schema，即 `MANIFEST_FILE_SCHEMA`）、`partition-specs`、`manifest-list-Location`（注意这里有个大小写不一致的笔误：`Location` 首字母大写，与 Java 字段名风格不一致，但既然测试 golden JSON 也用同名，保持一致即可）、`residual-filter`、`reference-snapshot-id`。`fromJson` 重建时调用 `new AllManifestsTable.ManifestListReadTask(dataTableSchema, fileIO, schema, specsById, manifestListLocation, residualFilter, referenceSnapshotId)`。

### `core/src/main/java/org/apache/iceberg/ManifestEntriesTableTaskParser.java`（新增）

**修改目的**：为 `BaseEntriesTable.ManifestReadTask` 提供 JSON 序列化/反序列化。

**工作逻辑**：字段集与 `FilesTableTaskParser` 几乎相同（都含 `manifest-file`、`schema`、`projection`、`file-io`、`partition-specs`、`residual-filter`），只是字段写出顺序略有不同（先 manifest 再 projection），但读回时按字段名取，顺序无关。重建调用 `new BaseEntriesTable.ManifestReadTask(dataTableSchema, fileIO, specsById, manifestFile, projection, residualFilter)`。

### `core/src/main/java/org/apache/iceberg/ManifestFileParser.java`（新增）

**修改目的**：把 `ManifestFile` 接口对象（通常实现为 `GenericManifestFile`）序列化为 JSON 并能反序列化还原，供 `FilesTableTaskParser` 与 `ManifestEntriesTableTaskParser` 复用。

**工作逻辑**：
- 定义全部字段名常量：`path`、`length`、`partition-spec-id`、`content`、`sequence-number`、`min-sequence-number`、`added-snapshot-id`、`added-files-count`、`existing-files-count`、`deleted-files-count`、`added-rows-count`、`existing-rows-count`、`deleted-rows-count`、`partition-field-summary`、`key-metadata`；
- `toJson(manifestFile, generator)`：依次写出各字段，对可空字段（content、snapshotId、各 count、partitions、keyMetadata）做 null 检查后再写；`partition-field-summary` 是数组，每个元素调用内部类 `PartitionFieldSummaryParser.toJson`；`key-metadata` 用 `SingleValueParser.toJson(DataFile.KEY_METADATA.type(), ...)` 写为二进制 hex 字符串；
- `fromJson(jsonNode)`：按字段名读取，对可选字段用 `jsonNode.has(...)` 判断是否存在；`partition-field-summary` 数组逐项用 `PartitionFieldSummaryParser.fromJson` 还原；最终调用 `new GenericManifestFile(path, length, specId, manifestContent, sequenceNumber, minSequenceNumber, addedSnapshotId, partitionFieldSummaries, keyMetadata, addedFilesCount, addedRowsCount, existingFilesCount, existingRowsCount, deletedFilesCount, deletedRowsCount)` 重建对象（这个构造函数是本提交为 `GenericManifestFile` 新增的）；
- 内部类 `PartitionFieldSummaryParser` 负责 `ManifestFile.PartitionFieldSummary` 的序列化，字段：`contains-null`（必填 boolean）、`contains-nan`（可选 Boolean）、`lower-bound`/`upper-bound`（可选 ByteBuffer，作为 `Types.BinaryType` 用 `SingleValueParser` 写为 hex 字符串）；`fromJson` 根据 `containsNaN` 是否存在选择调用 `GenericPartitionFieldSummary` 的不同构造函数。

### `core/src/main/java/org/apache/iceberg/BaseFilesTable.java`

**修改目的**：让 `ManifestReadTask` 可被 `FilesTableTaskParser` 序列化/反序列化。

**工作逻辑**：
- 删除 `VisibleForTesting` import（不再需要）；
- `planFiles(...)` 中删除预计算的 `schemaString`、`specString`、`residuals` 局部变量，改为直接传 `filter` 给新构造函数；
- `ManifestReadTask` 新增私有构造函数 `ManifestReadTask(Table table, ManifestFile manifest, Schema projection, Expression filter)`，委托给包私有构造函数 `ManifestReadTask(Schema dataTableSchema, FileIO io, Map<Integer, PartitionSpec> specsById, ManifestFile manifest, Schema projection, Expression filter)`；
- 包私有构造函数内部仍调用 `super(DataFiles.fromManifest(manifest), null, SchemaParser.toJson(projection), PartitionSpecParser.toJson(PartitionSpec.unpartitioned()), ResidualEvaluator.unpartitioned(filter))` 来构造父类 `BaseFileScanTask`，等价于原来在 `planFiles` 中预计算 schemaString/specString/residuals 的逻辑，只是延迟到构造函数内做；
- 新增包私有 getter：`io()`、`specsById()`、`dataTableSchema()`、`projection()`；原 `manifest()` 仍保留但去掉 `@VisibleForTesting`。

### `core/src/main/java/org/apache/iceberg/BaseEntriesTable.java`

**修改目的**：同上，让 `ManifestReadTask` 可被 `ManifestEntriesTableTaskParser` 序列化/反序列化。

**工作逻辑**：与 `BaseFilesTable` 改动一致——删除预计算变量、新增私有 `Table` 构造函数委托给包私有构造函数、在构造函数内计算 schemaString/specString/residuals、新增包私有 getter（`io()`、`specsById()`、`dataTableSchema()`、`projection()`）、去掉 `manifest()` 上的 `@VisibleForTesting`。

### `core/src/main/java/org/apache/iceberg/AllManifestsTable.java`

**修改目的**：让 `ManifestListReadTask` 可被 `AllManifestsTableTaskParser` 序列化/反序列化。

**工作逻辑**：
- `MANIFEST_FILE_SCHEMA` 由 `private` 改为包私有并加 `@VisibleForTesting`（测试需要引用）；
- `AllManifestsTableScan.doPlanFiles()` 中新增局部变量 `dataTableSchema = table().schema()`，并把它传给 `ManifestListReadTask` 构造函数；
- `ManifestListReadTask` 新增字段 `dataTableSchema`、新增构造函数参数 `Schema dataTableSchema` 放在首位；新增 `@Override schema()`（返回 `schema` 字段）以及包私有 getter `dataTableSchema()`、`io()`、`specsById()`、`manifestListLocation()`、`referenceSnapshotId()`。注意 `schema()` 在原 `DataTask` 接口中可能已有默认实现，这里显式 override 以确保返回的就是任务构造时传入的 schema。

### `core/src/main/java/org/apache/iceberg/GenericManifestFile.java`

**修改目的**：为 `ManifestFileParser.fromJson` 提供一个参数顺序匹配的构造函数。

**工作逻辑**：新增一个包私有构造函数，参数顺序为 `(path, length, specId, content, sequenceNumber, minSequenceNumber, snapshotId, partitions, keyMetadata, addedFilesCount, addedRowsCount, existingFilesCount, existingRowsCount, deletedFilesCount, deletedRowsCount)`，与已有 public 构造函数的参数顺序不同（已有构造函数把 counts 放在 partitions 之前）。注释说明"调整参数顺序以避免与下方 public 构造函数冲突"。函数体内把传入的 `partitions` 列表转为数组、把 `keyMetadata` ByteBuffer 转为 byte 数组存储，与其他构造函数保持一致的内部表示。

### `core/src/main/java/org/apache/iceberg/io/FileIOParser.java`

**修改目的**：让 `FilesTableTaskParser` 等新 Parser 能复用 FileIO 的 JSON 序列化逻辑。

**工作逻辑**：把 `toJson(FileIO io, JsonGenerator generator)` 与 `fromJson(JsonNode json, Object conf)` 两个方法的可见性由 `private` 改为 `public`。无任何逻辑改动。

### `core/src/main/java/org/apache/iceberg/util/PartitionUtil.java`

**修改目的**：提供 `indexSpecs(List<PartitionSpec>)` 公共工具方法，让各 Parser 在反序列化 partition-specs 数组后能重建 `Map<Integer, PartitionSpec>`。

**工作逻辑**：新增 public static 方法：

```java
public static Map<Integer, PartitionSpec> indexSpecs(List<PartitionSpec> specs) {
  ImmutableMap.Builder<Integer, PartitionSpec> builder = ImmutableMap.builder();
  for (PartitionSpec spec : specs) {
    builder.put(spec.specId(), spec);
  }
  return builder.build();
}
```

实现与原 `TableMetadata.indexSpecs` 完全一致，只是位置从 `TableMetadata` 私有方法挪到 `PartitionUtil` 公共方法。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`

**修改目的**：消除重复代码，复用 `PartitionUtil.indexSpecs`。

**工作逻辑**：
- 新增 import `org.apache.iceberg.util.PartitionUtil`；
- 删除私有静态方法 `indexSpecs(List<PartitionSpec>)`；
- 两处调用点（构造函数中 `this.specsById = indexSpecs(specs);` 与 `schema(...)` 重置方法中 `specsById.putAll(indexSpecs(specs));`）改为调用 `PartitionUtil.indexSpecs(specs)`。

### `.palantir/revapi.yml`

**修改目的**：把 `GenericManifestFile` 因新增构造函数导致的 `java.class.defaultSerializationChanged` API break 标记为已接受。

**工作逻辑**：在 `1.6.0` 段 `org.apache.iceberg:iceberg-core` 下新增一条接受项：

```yaml
- code: "java.class.defaultSerializationChanged"
  old: "class org.apache.iceberg.GenericManifestFile"
  new: "class org.apache.iceberg.GenericManifestFile"
  justification: "Serialization across versions is not supported"
```

理由是新增构造函数会改变默认 `serialVersionUID`，但 Iceberg 不支持跨版本的 Java 序列化（推荐使用 JSON 序列化），所以这个 break 可接受。同时把已有的 `PlaintextEncryptionManager` 两条条目顺序做了调整（功能无变化，可能是 revapi 工具自动重新排序）。

### `core/src/test/java/org/apache/iceberg/TestFilesTableTaskParser.java`（新增）

**修改目的**：覆盖 `FilesTableTaskParser` 的 null 校验、非法 JSON 节点、完整 round-trip 三个场景。

**工作逻辑**：
- `nullCheck`：断言 `toJson(null, generator)`、`toJson(task, null)`、`fromJson(null)` 抛 `IllegalArgumentException`；
- `invalidJsonNode`：用非对象 JSON 节点（字符串、数组）调用 `fromJson`，断言抛异常；
- `testParser`：构造 `ManifestReadTask`（用 `TestBase.SCHEMA`、`HadoopFileIO`、bucket 分区 spec、`TestManifestFileParser.createManifestFile()`），调用 `ScanTaskParser.toJson(task)` 与一个硬编码的 golden JSON 字符串 `taskJson()` 比对，再 `ScanTaskParser.fromJson(jsonStr, false)` 反序列化，`assertTaskEquals` 比对 schema、projection、io properties、specsById、residual、manifest 各字段。

### `core/src/test/java/org/apache/iceberg/TestAllManifestsTableTaskParser.java`（新增）

**修改目的**：覆盖 `AllManifestsTableTaskParser` 的同三类场景。

**工作逻辑**：与 `TestFilesTableTaskParser` 结构相同，但构造 `ManifestListReadTask` 时不传 `ManifestFile`，而是传 `manifestListLocation` 字符串与 `referenceSnapshotId` long。golden JSON 同样硬编码。注意 `assertTaskEquals` 中 `actualIO = (HadoopFileIO) expected.io()` 看起来是个测试代码 bug（应该是 `actual.io()`），但因为只比对 properties，且 expected 与 actual 的 io 是用相同 properties 初始化的 `HadoopFileIO`，测试仍能通过——这是测试自身的瑕疵，不影响生产代码。

### `core/src/test/java/org/apache/iceberg/TestManifestFileParser.java`（新增）

**修改目的**：覆盖 `ManifestFileParser` 的 null 校验、非法 JSON 节点、序列化结果与 golden JSON 比对。

**工作逻辑**：`createManifestFile()` 构造一个 `GenericManifestFile`，包含 partition field summary（lowerBound=10, upperBound=100 的 IntegerType 字节）与 keyMetadata（987 的 IntegerType 字节）；`testParser` 调用 `ManifestFileParser.toJson` 并与硬编码 golden JSON 比对。该类还提供 `static createManifestFile()` 供 `TestFilesTableTaskParser` 复用。

## 小结

- **成效**：为 Iceberg 元数据表的三类扫描任务（`BaseFilesTable.ManifestReadTask`、`BaseEntriesTable.ManifestReadTask`、`AllManifestsTable.ManifestListReadTask`）补齐了 JSON 序列化能力，使它们可经由 `ScanTaskParser` 统一序列化/反序列化。这让元数据表查询（`SELECT * FROM catalog.ns.tbl.files`、`... entries`、`... all_manifests`）能在分布式引擎下像普通数据扫描一样把任务下发到 executor 执行，提升大表元数据查询的并行度。同时附带新增了通用的 `ManifestFileParser`，为后续其他需要序列化 `ManifestFile` 的场景（如 REST Catalog 远程任务传输、缓存序列化等）提供基础设施。重构还顺带把 `PartitionUtil.indexSpecs` 提取为公共工具方法、把 `FileIOParser` 的两个方法提为 public，消除了 `TableMetadata` 中的重复实现。
- **影响范围**：涉及 `core` 模块 13 个文件（10 个生产代码 + 3 个测试），共 +1167/-53 行。生产代码改动包括：(1) 三个 Task 类构造函数签名与字段可见性的调整（向后不兼容，但因为这些 Task 类是包私有或内部类，外部无法直接构造，影响有限）；(2) `GenericManifestFile` 新增构造函数导致默认 `serialVersionUID` 变化（已在 revapi.yml 接受）；(3) `FileIOParser` 两个方法由 private 改 public（API 扩展，向后兼容）；(4) `PartitionUtil.indexSpecs` 新增 public 方法（API 扩展）；(5) `MANIFEST_FILE_SCHEMA` 由 private 改包私有。运行时行为：元数据表查询任务现在多了一条"可被 `ScanTaskParser` 序列化"的路径，但原有执行路径不变，是否实际启用 JSON 序列化取决于上层引擎（Spark/Flink）是否调用 `ScanTaskParser`。
- **回迁到 1.4.x 的注意事项**：属功能性增强，可回迁到 1.4.x 分支，但需注意以下几点：
  1. **API 兼容性**：本提交改动了三个 Task 类的构造函数签名（从接受 `Table` 改为接受显式依赖），如果 1.4.x 上有其他代码（如引擎集成模块 mr/spark/flink）直接构造这些 Task，需要同步修改调用点。建议先在 1.4.x 上搜索 `new BaseFilesTable.ManifestReadTask`、`new BaseEntriesTable.ManifestReadTask`、`new AllManifestsTable.ManifestListReadTask` 的所有调用点；
  2. **revapi.yml**：回迁后需同步把 `.palantir/revapi.yml` 中 `GenericManifestFile` 的 `defaultSerializationChanged` 接受项一并回迁，否则 1.4.x 的 API 兼容性检查会失败；
  3. **测试 bug**：`TestAllManifestsTableTaskParser.assertTaskEquals` 中 `actualIO = (HadoopFileIO) expected.io()` 是已知瑕疵，回迁时建议一并修正为 `actual.io()`，避免后续维护困惑；
  4. **字段名笔误**：`AllManifestsTableTaskParser` 中 `MANIFEST_LIST_LOCATION = "manifest-list-Location"` 的 `Location` 首字母大写与其余字段风格不一致，回迁后若 1.4.x 后续有相关清理 PR，可一并修正（但修正会破坏与已有 JSON 字符串的兼容性，需评估）；
  5. **依赖类存在性**：回迁前需确认 1.4.x 分支已存在 `SingleValueParser`、`ExpressionParser`、`PartitionSpecParser`、`SchemaParser`、`DataTaskParser`、`FileScanTaskParser` 等被引用的 Parser 类，且 API 与本提交使用的一致；若 1.4.x 上这些 Parser 的方法签名不同（如 `FileIOParser.fromJson` 参数个数），需要调整；
  6. **`ManifestFileParser` 中的 `SingleValueParser` 调用**：`SingleValueParser.toJson(Types.BinaryType.get(), ...)` 用于把 ByteBuffer 序列化为 hex，回迁前需确认 1.4.x 上 `SingleValueParser` 支持 `BinaryType`。
