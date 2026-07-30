# 提交 1668：API, Core: Metadata Row Lineage (#11948)

## 提交信息

- **序号**：1668 / 4088
- **哈希**：1fcd6a98947ec1dfaef799c6bc5660adf066e322
- **短哈希**：1fcd6a989
- **日期**：2025-02-01（Sat Feb 1 12:00:24 2025 +0100）
- **作者**：Russell Spitzer <russell.spitzer@GMAIL.COM>
- **提交说明**：API, Core: Metadata Row Lineage (#11948)
- **PR/Issue**：#11948

## 总体目的

Iceberg 的快照（snapshot）与 manifest 已经记录了文件级的统计信息（如每个 manifest 的 `added-rows-count`），但此前缺少一种**表级的、跨快照单调递增的行级 lineage 机制**——即无法回答"这张表里每一行是在哪个快照首次加入的、全局唯一的行 id 是什么"。这种行级 lineage 对增量消费、行级去重、数据血缘追踪、CDC 等场景非常有价值。

本提交引入"Metadata Row Lineage"特性（行级血缘），核心思路是：

1. 在表元数据（TableMetadata）中新增 `row-lineage` 开关与 `next-row-id` 计数器；
2. 当 lineage 启用时，每次提交快照时由 `SnapshotProducer` 计算本快照新增行数（`added-rows`，即新 manifest 的 `addedRowsCount` 之和），并以当前 `next-row-id` 作为本快照的 `first-row-id`（即本快照新增行的起始行 id）；
3. `TableMetadata.Builder` 在 `addSnapshot` 时校验 `first-row-id` 单调递增且 `added-rows` 非负，然后把 `next-row-id` 推进 `added-rows`；
4. 快照 JSON 与表元数据 JSON 持久化这些字段。

该特性要求 format-version >= 3，一旦启用不可关闭（防止回退导致行 id 重复）。本提交是底座，只做元数据层的存储与校验，不涉及数据文件内实际写入行 id 列（那是后续工作）。

## 如何达成设计目的

通过贯穿 API/Core 的多处协同修改实现：

1. **API 层**：`Snapshot` 接口新增 `firstRowId()`/`addedRows()` 两个带默认 `null` 实现的方法，作为快照的可选字段。
2. **Core 层 - TableMetadata**：新增 `rowLineageEnabled`（Boolean）与 `nextRowId`（long）两个不可变字段；构造器与 Builder 增加对应参数；新增 `rowLineageEnabled()`/`nextRowId()` 访问器；Builder 新增 `enableRowLineage()`（校验 format-version >= 3，记录 `EnableRowLineage` 变更）与私有 `setRowLineage(Boolean)`（处理属性变更，禁止关闭）；`addSnapshot` 在 lineage 启用时校验 firstRowId >= nextRowId、addedRows 非空非负，并推进 nextRowId；`replaceProperties` 把 `row-lineage` 属性映射到 `setRowLineage`。
3. **Core 层 - SnapshotProducer**：提交快照时，若 `base.rowLineageEnabled()`，则计算 `addedRows`（新 manifest 的 addedRowsCount 之和，校验非空）与 `firstRowId = base.nextRowId()`，传入 `BaseSnapshot`。
4. **Core 层 - BaseSnapshot**：新增 `firstRowId`/`addedRows` 字段与构造器参数，实现接口方法。
5. **Core 层 - 解析器**：`SnapshotParser` 读写 `first-row-id`/`added-rows`；`TableMetadataParser` 读写 `row-lineage`/`next-row-id`；`MetadataUpdateParser` 处理 `enable-row-lineage` action。
6. **Core 层 - 元数据更新**：`MetadataUpdate` 新增 `EnableRowLineage` 内部类，`applyTo` 调 `builder.enableRowLineage()`。
7. **Core 层 - 属性与工具**：`TableProperties` 新增 `ROW_LINEAGE = "row-lineage"`；`JsonUtil` 新增 `getBoolOrNull`。
8. **Core 层 - RewriteTablePathUtil**：路径重写时透传 `rowLineageEnabled`/`nextRowId` 与快照的 `firstRowId`/`addedRows`，保证重写后 lineage 信息不丢失。
9. **测试**：新增 `TestRowLineageMetadata`（318 行）覆盖 lineage 启用、单调递增、JSON 往返、属性切换、禁用禁止等；扩展 `TestSnapshotJson`/`TestTableMetadata`/`TestMetadataUpdateParser`/`TestJsonUtil`/`TestDataTaskParser` 覆盖新字段。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Snapshot.java`（修改，25 行）

**修改目的**：在快照接口上暴露行 lineage 字段。

**工作逻辑**：新增两个带 `default null` 实现的方法：
- `Long firstRowId()`：本快照新增行的起始行 id；所有在本快照新增的行其 row-id 都 >= 该值；小于该值的行来自更早的快照。null 表示未启用 lineage。
- `Long addedRows()`：本快照新增行总数，应为各 manifest `ADDED_ROWS_COUNT` 之和；lineage 启用时必填。

### `core/src/main/java/org/apache/iceberg/BaseSnapshot.java`（修改，20 行）

**修改目的**：存储并暴露快照的 lineage 字段。

**工作逻辑**：新增 `Long firstRowId`/`Long addedRows` final 字段；主构造器（基于 manifestList）新增这两个参数并赋值；v1 manifest 构造器把它们置 null；实现 `firstRowId()`/`addedRows()`。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java`（修改，30 行）

**修改目的**：提交快照时计算 lineage 字段。

**工作逻辑**：
- 在写出 manifest list 后，若 `base.rowLineageEnabled()`：`addedRows = calculateAddedRows(manifests)`、`lastRowId = base.nextRowId()`；把这两个值传入 `new BaseSnapshot(...)`（作为 firstRowId/addedRows）。
- `calculateAddedRows(manifests)`：过滤出 `snapshotId == null || == 本快照 id` 的 manifest（即本次新增的 manifest），校验每个 `addedRowsCount() != null`（否则抛异常提示缺少 `added-rows-count`），求和返回。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`（修改，85 行）

**修改目的**：表级 lineage 状态管理与校验。

**工作逻辑**：
- 新增常量 `INITIAL_ROW_ID = 0`、`DEFAULT_ROW_LINEAGE = false`、`MIN_FORMAT_VERSION_ROW_LINEAGE = 3`；
- 新增字段 `Boolean rowLineageEnabled`、`long nextRowId`；主构造器新增两参数，并校验 `formatVersion >= 3 || !rowLineageEnabled`（V3 以下不可启用）；
- 新增 `rowLineageEnabled()`/`nextRowId()` 访问器；
- `replaceProperties`：从属性读 `row-lineage` 布尔，调 `setRowLineage(newRowLineage)`；
- Builder：新增 `boolean rowLineage`（默认 false）、`long nextRowId`（默认 0）字段；`Builder()` 初始化默认值，`Builder(base)` 从 base 继承；
- `addSnapshot(snapshot)`：若 `rowLineage` 启用，校验 `snapshot.firstRowId() >= nextRowId`（防重复 row id）、`addedRows() != null`、`addedRows() >= 0`，然后 `nextRowId += snapshot.addedRows()`；
- 新增 `setRowLineage(Boolean)`：null 直接返回；禁止从 true→false（抛异常）；false→true 调 `enableRowLineage`；true→true 无操作；
- 新增 `enableRowLineage()`：校验 format-version >= 3，置 `rowLineage = true`，记录 `MetadataUpdate.EnableRowLineage`；
- `build()`：把 `rowLineage`/`nextRowId` 传入 TableMetadata 构造器。

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java`（修改，20 行）

**修改目的**：持久化 lineage 字段。

**工作逻辑**：
- 新增常量 `ROW_LINEAGE = "row-lineage"`、`NEXT_ROW_ID = "next-row-id"`；
- `toJson`：若 `rowLineageEnabled()`，写 `row-lineage` 布尔与 `next-row-id` 数值；
- `fromJson`：用 `getBoolOrNull` 读 `row-lineage`；若为 true 则读 `next-row-id`（必填），否则用默认值 false/0；传入 TableMetadata 构造器。

### `core/src/main/java/org/apache/iceberg/SnapshotParser.java`（修改，17 行）

**修改目的**：持久化快照 lineage 字段。

**工作逻辑**：
- 新增常量 `FIRST_ROW_ID = "first-row-id"`、`ADDED_ROWS = "added-rows"`；
- `toJson`：若 `firstRowId()`/`addedRows()` 非 null 则分别写数值字段；
- `fromJson`：用 `getLongOrNull` 读两字段，传入 BaseSnapshot 构造器。

### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java`（修改，7 行）

**修改目的**：新增 lineage 启用的元数据更新。

**工作逻辑**：新增内部类 `EnableRowLineage implements MetadataUpdate`，`applyTo` 调 `metadataBuilder.enableRowLineage()`。

### `core/src/main/java/org/apache/iceberg/MetadataUpdateParser.java`（修改，6 行）

**修改目的**：序列化 `enable-row-lineage` 更新。

**工作逻辑**：新增常量 `ENABLE_ROW_LINEAGE = "enable-row-lineage"`；注册 class→action 映射；`write` 分支为空（该 update 无字段）；`read` 返回 `new EnableRowLineage()`。

### `core/src/main/java/org/apache/iceberg/TableProperties.java`（修改，2 行）

**修改目的**：定义 lineage 表属性键。

**工作逻辑**：新增 `public static final String ROW_LINEAGE = "row-lineage"`。

### `core/src/main/java/org/apache/iceberg/util/JsonUtil.java`（修改，7 行）

**修改目的**：补充可空布尔解析工具。

**工作逻辑**：新增 `getBoolOrNull(property, node)`：若 `!node.hasNonNull(property)` 返回 null，否则调 `getBool`。

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java`（修改，8 行）

**修改目的**：路径重写时保留 lineage 信息。

**工作逻辑**：构造新 TableMetadata 时透传 `metadata.rowLineageEnabled()`/`metadata.nextRowId()`；构造新快照时透传 `snapshot.firstRowId()`/`snapshot.addedRows()`。

### `core/src/test/java/org/apache/iceberg/TestRowLineageMetadata.java`（新增，318 行）

**修改目的**：lineage 特性端到端测试。

**工作逻辑**：覆盖：启用 lineage 需 V3；启用后 nextRowId 推进；firstRowId 单调递增校验；addedRows 缺失/负值校验；属性方式启用/禁止关闭；JSON 往返；快照 JSON 字段等。

### 其他测试文件（`TestSnapshotJson`/`TestTableMetadata`/`TestMetadataUpdateParser`/`TestJsonUtil`/`TestDataTaskParser`）

**修改目的**：覆盖新字段与新工具方法的解析与序列化。

## 小结

- **成效**：引入表级行 lineage 元数据底座——表元数据记录 `row-lineage`/`next-row-id`，每个快照记录 `first-row-id`/`added-rows`，提交时由 SnapshotProducer 计算并经 Builder 校验单调递增，保证全局行 id 不重复。要求 format-version >= 3，一旦启用不可关闭。为后续行级增量消费、行级血缘、CDC 等场景奠定基础。
- **影响范围**：`api`（Snapshot 接口新增两个 default 方法，向后兼容）与 `core`（TableMetadata/BaseSnapshot/SnapshotProducer/各 Parser/RewriteTablePathUtil）。元数据 JSON 新增可选字段，旧表（未启用 lineage）解析不受影响。本提交只做元数据层存储与校验，不修改数据文件格式，不在数据中写入行 id 列。
- **回迁到 1.4.x 的注意事项**：这是较重的新特性，回迁需谨慎。① 需确认 1.4.x 的 `TableMetadata`/`BaseSnapshot`/`SnapshotProducer`/各 Parser 与 main 结构兼容，本提交改动构造器签名（TableMetadata、BaseSnapshot），会波及所有调用方（如各 catalog、RewriteTablePath、测试），回迁需一并调整；② 特性要求 format-version >= 3，1.4.x 对 V3 的支持程度需评估，若 1.4.x V3 尚不完善则该特性意义有限；③ `Snapshot` 接口新增 default 方法对实现该接口的第三方代码兼容，但 `BaseSnapshot` 构造器变更是破坏性的，1.4.x 内部若有自定义 Snapshot 子类需同步修改；④ 这是底座提交，后续还有若干提交在此基础上完善（如数据文件内行 id 列写入），单独回迁此提交只能获得元数据记录能力，无法端到端使用 lineage。
