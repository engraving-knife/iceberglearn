# 提交 0877：Core, Flink: Add task-type field to JSON serde of scan task / Add JSON serde for StaticDataTask. (#9728)

## 提交信息

- **序号**：0877 / 4088
- **哈希**：9ed33839e7a8820b5069cbb51cd54a80785d6a62
- **短哈希**：9ed33839e
- **日期**：2024-06-26 10:37:37 +0200
- **作者**：Steven Zhen Wu
- **提交说明**：Core, Flink: Add task-type field to JSON serde of scan task / Add JSON serde for StaticDataTask. (#9728)
- **PR/Issue**：#9728

## 总体目的

Iceberg 的扫描任务（scan task）有不同类型，主要是 `FileScanTask`（普通文件扫描任务，对应 `BaseFileScanTask` 及其 `SplitScanTask`）和 `DataTask`（静态数据任务，对应 `StaticDataTask`，用于元数据表（如 `SnapshotsTable`）这类不读取数据文件、而是直接返回内存中行的扫描）。

此前 Core 模块只有 `FileScanTaskParser` 负责 `FileScanTask` 的 JSON 序列化/反序列化，没有对 `StaticDataTask` 的 JSON serde 支持，也没有在 JSON 中区分任务类型。这意味着 Flink 等下游系统在序列化 split（`IcebergSourceSplit`）时只能正确处理普通文件扫描任务；如果 split 中包含 `StaticDataTask`（例如读取元数据表的查询被下推到 Flink source），序列化会失败或反序列化得到错误结果。

本提交解决两个相关问题：
1. 引入新的统一入口 `ScanTaskParser`，在 JSON 中加入 `task-type` 字段（取值 `file-scan-task` 或 `data-task`），让反序列化时能正确分发到对应的 parser。
2. 新增 `DataTaskParser`，专门负责 `StaticDataTask` 的 JSON 序列化/反序列化，覆盖 `schema`、`projection`、`metadata-file`、`rows` 四个字段。

为了让 `DataTaskParser` 能访问 `StaticDataTask` 的内部字段，本提交还在 `StaticDataTask` 上增加了若干包级访问的 getter 和一个用于反序列化重建对象的构造器。Flink 三个版本（v1.17/v1.18/v1.19）的 `IcebergSourceSplit` 同步切换到新的 `ScanTaskParser`。

## 如何达成设计目的

整体设计采用"统一入口 + 类型分发"模式：

1. **新入口 `ScanTaskParser`**：对外暴露 `toJson(FileScanTask)` 和 `fromJson(String, boolean)`。序列化时根据运行时类型（`StaticDataTask` vs `BaseFileScanTask`/`SplitScanTask`）写入不同的 `task-type` 字段，并委托给 `DataTaskParser` 或 `FileScanTaskParser` 写入具体字段。反序列化时读取 `task-type` 字段（若不存在则默认 `file-scan-task` 以保持向后兼容），按类型分发到对应 parser。
2. **`DataTaskParser`**：内部类，序列化 `StaticDataTask` 的 `schema`、`projectedSchema`、`metadataFile`、`rows`；反序列化时重建 `StaticDataTask`。
3. **`FileScanTaskParser` 重构**：原有 `toJson(FileScanTask, JsonGenerator)` 和 `fromJson(JsonNode, boolean)` 从 `private` 改为 package-private（`static`），以便 `ScanTaskParser` 复用。原来在 `toJson` 内部写的 `writeStartObject`/`writeEndObject` 移除（改由 `ScanTaskParser` 包裹），让 `FileScanTaskParser` 只负责字段写入。旧的无参 `toJson(FileScanTask)` 和 `fromJson(String, boolean)` 标记为 `@Deprecated`（计划 1.7.0 移除），但保留兼容性——内部补上 `writeStartObject`/`writeEndObject` 包裹。
4. **`StaticDataTask` 扩展**：新增包级构造器 `StaticDataTask(DataFile, Schema, Schema, StructLike[])`，新增 `schema()`（覆盖接口方法）、`projectedSchema()`、`metadataFile()`、`tableRows()` 等 getter，供 parser 读写。
5. **Flink `IcebergSourceSplit` 切换**：把对 `FileScanTaskParser.toJson` / `FileScanTaskParser.fromJson` 的调用替换为 `ScanTaskParser.toJson` / `ScanTaskParser.fromJson`，让 Flink split 序列化自动获得对 `StaticDataTask` 的支持。
6. **向后兼容**：反序列化时 `task-type` 字段缺失则默认为 `file-scan-task`，保证旧版序列化的 split 仍能被读取。测试中专门增加了"无 task-type 字段"的回归用例。

## 修改详情

### `core/src/main/java/org/apache/iceberg/ScanTaskParser.java`（新文件）

**修改目的**：提供统一的 scan task JSON 序列化/反序列化入口，根据任务类型分发。

**工作逻辑**：
- 定义枚举 `TaskType`，包含 `FILE_SCAN_TASK("file-scan-task")` 和 `DATA_TASK("data-task")` 两个值，并提供 `fromTypeName` 反查方法（未知类型抛 `IllegalArgumentException`）。
- `toJson(FileScanTask)`：生成 JSON 字符串。内部 `toJson(FileScanTask, JsonGenerator)` 写入 `writeStartObject`，根据运行时类型写 `task-type` 字段并委托：
  - `StaticDataTask` → 写 `data-task`，调用 `DataTaskParser.toJson(...)`；
  - `BaseFileScanTask` 或 `BaseFileScanTask.SplitScanTask` → 写 `file-scan-task`，调用 `FileScanTaskParser.toJson(...)`；
  - 其他类型抛 `UnsupportedOperationException`。
  最后 `writeEndObject`。
- `fromJson(String, boolean)`：解析 JSON 字符串。内部读取 `task-type`（缺失时默认 `FILE_SCAN_TASK`），按类型分发：`FILE_SCAN_TASK` → `FileScanTaskParser.fromJson(...)`；`DATA_TASK` → `DataTaskParser.fromJson(...)`。

### `core/src/main/java/org/apache/iceberg/DataTaskParser.java`（新文件）

**修改目的**：负责 `StaticDataTask` 的 JSON 字段级序列化/反序列化。

**工作逻辑**：
- 字段常量：`SCHEMA`、`PROJECTED_SCHEMA`（"projection"）、`METADATA_FILE`（"metadata-file"）、`ROWS`。
- `toJson(StaticDataTask, JsonGenerator)`：依次写入 `schema`（`SchemaParser`）、`projection`（`SchemaParser`）、`metadata-file`（`ContentFileParser`，使用 `PartitionSpec.unpartitioned()`）、`rows`（数组，每行用 `SingleValueParser.toJson`）。
- `fromJson(JsonNode)`：依次解析 `schema`、`projection`、`metadata-file`（强转为 `DataFile`）、`rows`（数组，逐行用 `SingleValueParser.fromJson` 还原为 `StructLike`），最后用新构造器 `new StaticDataTask(metadataFile, schema, projectedSchema, rows)` 重建对象。

### `core/src/main/java/org/apache/iceberg/FileScanTaskParser.java`

**修改目的**：重构为可被 `ScanTaskParser` 复用的字段级 parser，同时保留旧公共 API 的向后兼容。

**工作逻辑**：
- `toJson(FileScanTask)`（旧 public API）标记 `@Deprecated`（1.7.0 移除），内部补上 `writeStartObject`/`writeEndObject` 包裹后调用字段级 `toJson(FileScanTask, JsonGenerator)`。同时新增 `Preconditions.checkArgument` 空值校验。
- `fromJson(String, boolean)`（旧 public API）标记 `@Deprecated`，委托给字段级 `fromJson(JsonNode, boolean)`。
- 字段级 `toJson(FileScanTask, JsonGenerator)` 和 `fromJson(JsonNode, boolean)` 由 `private` 改为 package-private（`static`），移除 `toJson` 内的 `writeStartObject`/`writeEndObject`（改由调用方 `ScanTaskParser` 包裹），让本方法只负责字段写入。

### `core/src/main/java/org/apache/iceberg/StaticDataTask.java`

**修改目的**：暴露 parser 所需的内部状态访问与重建能力。

**工作逻辑**：
- 新增包级构造器 `StaticDataTask(DataFile metadataFile, Schema tableSchema, Schema projectedSchema, StructLike[] rows)`，用于反序列化时重建对象。
- 覆盖 `public Schema schema()` 返回 `tableSchema`（`DataTask` 接口需要）。
- 新增包级 getter `projectedSchema()`、`metadataFile()`、`tableRows()`（注释说明 `tableRows` 返回投影前的原始行）。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/split/IcebergSourceSplit.java`、`flink/v1.18/...`、`flink/v1.19/...`

**修改目的**：将 Flink source split 的序列化从 `FileScanTaskParser` 切换到 `ScanTaskParser`，使其能处理 `StaticDataTask`。

**工作逻辑**：每个文件改动 2 处：
- 导入：`import org.apache.iceberg.FileScanTaskParser;` → `import org.apache.iceberg.ScanTaskParser;`
- 序列化（`write`）：`FileScanTaskParser.toJson(fileScanTask)` → `ScanTaskParser.toJson(fileScanTask)`；
- 反序列化（`read`）：`FileScanTaskParser.fromJson(taskJson, caseSensitive)` → `ScanTaskParser.fromJson(taskJson, caseSensitive)`。

### `core/src/test/java/org/apache/iceberg/TestDataTaskParser.java`（新文件）

**修改目的**：为 `DataTaskParser` 与 `StaticDataTask` serde 路径提供测试覆盖。

**工作逻辑**：包含 `nullCheck`、`invalidJsonNode`、`missingFields`、`roundTripSerde` 等用例。`roundTripSerde` 通过 `ScanTaskParser.toJson(dataTask)` 序列化、`ScanTaskParser.fromJson` 反序列化，验证 JSON 串与期望一致、对象字段相等。测试中使用与 `SnapshotsTable` 相同的 schema 构造 `StaticDataTask`。

### `core/src/test/java/org/apache/iceberg/TestFileScanTaskParser.java`

**修改目的**：扩展既有测试，覆盖 `ScanTaskParser` 入口与无 `task-type` 字段的向后兼容场景。

**工作逻辑**：
- `nullCheck` 增加 `ScanTaskParser.toJson(null)` 与 `ScanTaskParser.fromJson(null, true)` 的断言。
- 原 `testParser` 拆分/重命名为 `testFileScanTaskParser`、`testFileScanTaskParserWithoutTaskTypeField`、`testScanTaskParser`、`testScanTaskParserWithoutTaskTypeField` 四个参数化测试，分别覆盖 `FileScanTaskParser`（无 task-type）、`ScanTaskParser`（有 task-type）两条路径，并验证"无 task-type 字段"时仍能正确反序列化（向后兼容）。
- 新增 `fileScanTaskJson()` 返回带 `"task-type":"file-scan-task"` 前缀的期望 JSON；原 `expectedFileScanTaskJson()` 重命名为 `fileScanTaskJsonWithoutTaskType()`。
- `assertFileScanTaskEquals` 中 schema 比较改用 `actual.schema().asStruct()` 与 `expected.schema().asStruct()` 比对。

### `core/src/test/java/org/apache/iceberg/TestScanTaskParser.java`（新文件）

**修改目的**：测试 `ScanTaskParser` 对异常输入的处理。

**工作逻辑**：
- `nullCheck`：验证 `toJson(null)` 和 `fromJson(null, true)` 抛 `IllegalArgumentException`。
- `invalidTaskType`：验证 `task-type` 为未知值（如 `"junk"`）时抛 `IllegalArgumentException("Unknown task type: junk")`。
- `unsupportedTask`：用 Mockito mock 一个 `FileScanTask`（既非 `StaticDataTask` 也非 `BaseFileScanTask`），验证 `toJson` 抛 `UnsupportedOperationException`。

## 小结

- **成效**：为 Iceberg Core 引入了统一的 scan task JSON serde 入口 `ScanTaskParser`，通过 `task-type` 字段区分任务类型；新增 `DataTaskParser` 支持 `StaticDataTask` 的序列化；Flink 三个版本的 source split 切换到新入口，从而支持包含元数据表（`StaticDataTask`）的查询在 Flink 中正确序列化与恢复。对旧格式 JSON 保持向后兼容（缺失 `task-type` 时按 `file-scan-task` 处理）。
- **影响范围**：Core 模块新增 2 个主代码文件（`ScanTaskParser`、`DataTaskParser`）、修改 2 个（`FileScanTaskParser`、`StaticDataTask`），新增 2 个测试文件（`TestDataTaskParser`、`TestScanTaskParser`）、修改 1 个测试文件（`TestFileScanTaskParser`）；Flink v1.17/v1.18/v1.19 各修改 1 个文件（`IcebergSourceSplit`）。
- **回迁到 1.4.x 的注意事项**：本提交属于功能增强（扩展 serde 覆盖范围），可酌情回迁。回迁时需注意：(1) 1.4.x 分支的 `StaticDataTask`、`FileScanTaskParser`、`SingleValueParser`、`ContentFileParser` 等是否与本提交所基于的版本一致；(2) Flink split 序列化格式变化（新增 `task-type` 字段）属于向前兼容——新版读旧版无字段 JSON 没问题，但旧版读新版带字段 JSON 会忽略该字段并按 `file-scan-task` 处理，对 `data-task` 类型会反序列化错误，因此回迁后需保证 Flink source split 序列化/反序列化两端版本一致；(3) `FileScanTaskParser` 旧 API 被标记 `@Deprecated`（1.7.0 移除），1.4.x 回迁后可保留或同步标记。建议结合 1.4.x 是否已支持 `StaticDataTask` 序列化需求来决定回迁优先级。
