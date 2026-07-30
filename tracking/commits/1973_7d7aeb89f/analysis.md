# 提交 1973：Core: Enable row lineage for all v3 tables (#12593)

## 提交信息

- **序号**：1973 / 4088
- **哈希**：7d7aeb89f26e3325e76afb8e20d6cd5b0ba74711
- **短哈希**：7d7aeb89f
- **日期**：2025-04-07 09:52:23 -0700
- **作者**：Ryan Blue
- **提交说明**：Core: Enable row lineage for all v3 tables (#12593)
- **PR/Issue**：#12593

## 总体目的

本提交将 row lineage（行血缘/行 ID 追踪）从"v3 表的可选开启功能"改为"所有 v3 表默认强制启用"。此前，row lineage 需要通过表属性 `row-lineage=true` 或 `EnableRowLineage` 元数据更新来显式启用，并且 `TableMetadata` 维护一个独立的 `rowLineageEnabled` 布尔状态。这种"可选"设计带来了复杂度：需要处理启用/禁用转换、属性同步、元数据序列化中额外的 `row-lineage` 字段等。

由于 row lineage 是 v3 格式的核心能力（为行级更新/删除提供稳定的 row id），本提交将其确立为 v3 表的固有行为：只要 `format-version >= 3`，就自动启用 row lineage，无需也无法再单独开关。原有的 `EnableRowLineage` 更新、`ROW_LINEAGE` 属性、`enableRowLineage()` 构造器方法、`rowLineageEnabled` 字段等被标记为 `@Deprecated`（计划在 1.10.0 移除）或直接删除，相关序列化逻辑也改为基于 format-version 判断。

同时本提交加强了 `BaseSnapshot` 对 `firstRowId`/`addedRows` 的校验（非负、firstRowId 设置时 addedRows 必填），并修正了 `SnapshotProducer.calculateAddedRows` 只统计 DATA 内容 manifest（排除删除文件），确保删除操作不会错误计入新增行数。

## 如何达成设计目的

整体设计是把"row lineage 是否启用"的判定从独立状态字段收敛为"format-version >= 3"的派生判断：

1. **TableMetadata**：删除 `rowLineageEnabled` 字段与构造参数；`rowLineageEnabled()` 改为返回 `formatVersion >= MIN_FORMAT_VERSION_ROW_LINEAGE`（标记 `@Deprecated`）；删除 `setRowLineage` 私有方法与属性解析逻辑；`enableRowLineage()` 改为对 v3 表的 no-op（返回 null）并标记 `@Deprecated`，对低于 v3 的表抛 `UnsupportedOperationException`；`addSnapshot` 中将 `if (rowLineage)` 改为 `if (formatVersion >= MIN_FORMAT_VERSION_ROW_LINEAGE)`，并增加 firstRowId 非空校验。
2. **TableMetadataParser**：序列化时不再写 `row-lineage` 布尔字段，只要 `formatVersion >= 3` 就写 `next-row-id`；反序列化时按 `formatVersion >= 3` 决定读取 `next-row-id`。
3. **MetadataUpdateParser**：移除 `enable-row-lineage` action 的读写支持。
4. **SnapshotProducer**：用 `base.formatVersion() >= 3` 替代 `base.rowLineageEnabled()` 决定是否计算 addedRows/firstRowId；变量 `lastRowId` 重命名为 `firstRowId`（语义更准确，记录快照首行 id 而非末行）；`calculateAddedRows` 增加 `.filter(manifest -> manifest.content() == ManifestContent.DATA)`，排除删除文件 manifest。
5. **BaseSnapshot**：构造时校验 firstRowId/addedRows 非负、firstRowId 非空时 addedRows 必须非空；当 firstRowId 为 null 时强制 addedRows 也为 null。
6. **MetadataUpdate.EnableRowLineage / TableProperties.ROW_LINEAGE**：标记 `@Deprecated`。
7. **RewriteTablePathUtil**：适配 TableMetadata 新构造签名（nextRowId 与 changes 顺序调整、移除 rowLineageEnabled 参数）。
8. **测试**：移除所有 `enableRowLineage()` 调用与属性开关测试；新增 `testSnapshotRowIDValidation`、`testPositionDeletes`、`testEqualityDeletes`（验证删除文件不计入 addedRows）；调整已有断言。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Snapshot.java` (修改, +2/-3 lines)

**修改目的**：更新 `firstRowId()`/`addedRows()` 的 Javadoc，反映 row lineage 现在由表版本决定而非创建时开关。

**工作逻辑**：将"row lineage was not enabled when the table was created"改为"row lineage is not supported"；"required when row lineage is enabled"改为"required when the table version supports row lineage"。

### `core/src/main/java/org/apache/iceberg/BaseSnapshot.java` (修改, +12/-1 lines)

**修改目的**：强化快照 row lineage 字段的校验与一致性。

**工作逻辑**：构造函数新增三个 `Preconditions.checkArgument`：firstRowId 为 null 或 >=0；addedRows 为 null 或 >=0；firstRowId 非 null 时 addedRows 必须非 null。赋值时 `this.addedRows = firstRowId != null ? addedRows : null`，保证 firstRowId 缺失时 addedRows 也置空。

### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java` (修改, +6/-0 lines)

**修改目的**：标记 `EnableRowLineage` 更新为废弃。

**工作逻辑**：为 `EnableRowLineage` 类添加 `@Deprecated` 注解与 Javadoc，说明将在 1.10.0 移除，因 v3+ 表 row lineage 已强制启用。

### `core/src/main/java/org/apache/iceberg/MetadataUpdateParser.java` (修改, +0/-6 lines)

**修改目的**：移除 `enable-row-lineage` action 的序列化支持。

**工作逻辑**：删除 `ENABLE_ROW_LINEAGE` 常量、class↔action 映射项、write switch case、read switch case。

### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java` (修改, +2/-3 lines)

**修改目的**：适配 TableMetadata 构造签名变更。

**工作逻辑**：构造 TableMetadata 时参数顺序改为 `nextRowId, changes`，移除 `metadata.rowLineageEnabled()` 参数。

### `core/src/main/java/org/apache/iceberg/SnapshotProducer.java` (修改, +6/-3 lines)

**修改目的**：基于 format-version 判定 row lineage，并正确统计新增行。

**工作逻辑**：将 `if (base.rowLineageEnabled())` 改为 `if (base.formatVersion() >= 3)`，变量 `lastRowId` 改名 `firstRowId`（值仍取自 `base.nextRowId()`）。`calculateAddedRows` 流中增加 `.filter(manifest -> manifest.content() == ManifestContent.DATA)`，确保只对数据文件 manifest 累加 `ADDED_ROWS_COUNT`。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java` (修改, +44/-57 lines)

**修改目的**：将 row lineage 改为 v3 表的派生属性，移除独立状态。

**工作逻辑**：
- 删除 `DEFAULT_ROW_LINEAGE` 常量；保留 `MIN_FORMAT_VERSION_ROW_LINEAGE = 3`。
- 新表构造 `buildReplacement` 中移除从属性读取 `rowLineage` 与 `setRowLineage` 调用。
- 字段删除 `rowLineageEnabled`，构造参数移除该布尔并调整 `nextRowId`/`changes` 顺序；移除"format < 3 时不可启用 row lineage"的校验。
- `rowLineageEnabled()` 标记 `@Deprecated`，返回 `formatVersion >= MIN_FORMAT_VERSION_ROW_LINEAGE`。
- `updateProperties` 路径移除 `newRowLineage` 解析与 `setRowLineage` 调用。
- Builder 删除 `rowLineage` 字段与初始化；`enableRowLineage()` 重写为 `@Deprecated` 的 no-op（v3+ 返回 null，<v3 抛 `UnsupportedOperationException`）；删除原 `enableRowLineage()`（会校验版本、置 true、添加 EnableRowLineage change）与 `setRowLineage(Boolean)`。
- `addSnapshot` 中 `if (rowLineage)` 改为 `if (formatVersion >= MIN_FORMAT_VERSION_ROW_LINEAGE)`，校验改为 firstRowId 非空且 >= nextRowId，移除 addedRows 非空/非负的重复校验（已移至 BaseSnapshot）。
- `build()` 构造 TableMetadata 时参数顺序改为 `nextRowId, changes`。

### `core/src/main/java/org/apache/iceberg/TableMetadataParser.java` (修改, +4/-9 lines)

**修改目的**：序列化基于 format-version 而非 rowLineageEnabled。

**工作逻辑**：删除 `ROW_LINEAGE` 常量；写元数据时 `if (metadata.rowLineageEnabled())` 改为 `if (metadata.formatVersion() >= 3)`，不再写 `row-lineage` 布尔，仍写 `next-row-id`；读元数据时 `if (rowLineage != null && rowLineage)` 改为 `if (formatVersion >= 3)` 决定读 `next-row-id`，否则用 `INITIAL_ROW_ID`；构造 TableMetadata 参数顺序调整。

### `core/src/main/java/org/apache/iceberg/TableProperties.java` (修改, +6/-1 lines)

**修改目的**：废弃 `ROW_LINEAGE` 属性。

**工作逻辑**：为 `ROW_LINEAGE` 常量添加 `@Deprecated` 注解与 Javadoc，说明将在 1.10.0 移除。

### `core/src/test/java/org/apache/iceberg/TestMetadataUpdateParser.java` (修改, +0/-14 lines)

**修改目的**：移除已废弃 action 的测试。

**工作逻辑**：删除 `testEnableRowLineage` 测试及 `assertEquals` 中对应 case。

### `core/src/test/java/org/apache/iceberg/TestRowLineageMetadata.java` (修改, +110/-77 lines)

**修改目的**：适配新行为并扩充覆盖。

**工作逻辑**：
- `baseMetadata()` 移除 `.enableRowLineage()`。
- 删除 `testRowLineageSupported`（基于版本的启用校验），新增 `testSnapshotRowIDValidation`（Junit `@Test`，覆盖 BaseSnapshot 的 firstRowId/addedRows 校验：null、0、负数、缺失 addedRows 等场景）。
- 各测试移除 `enableRowLineage()` commit 与 `rowLineageEnabled()` 断言；新增 `testPositionDeletes`、`testEqualityDeletes`（验证 Puffin 位图删除与等值删除文件的快照 addedRows=0、nextRowId 不变）。
- `testReplace` 增加 `addedRows()` 断言。
- 删除 `testEnableRowLineageViaProperty`、`testEnableRowLineageViaPropertyAtTableCreation`（属性开关已废弃）。

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java` (修改, +20/-42 lines)

**修改目的**：适配移除 rowLineageEnabled 字段后的构造与断言。

### `core/src/test/java/org/apache/iceberg/rest/responses/TestLoadTableResponseParser.java` (修改, +60/-19 lines)

**修改目的**：更新元数据 JSON 解析测试以匹配不再含 `row-lineage` 字段、v3 表含 `next-row-id` 的新格式。

## 总结

本提交将 row lineage 从 v3 表的可选特性转为强制默认行为：以 `format-version >= 3` 作为唯一启用判据，移除 `rowLineageEnabled` 独立状态字段及 `EnableRowLineage` 更新/`ROW_LINEAGE` 属性的实质作用（标记 `@Deprecated`，计划 1.10.0 移除）。同时强化了 `BaseSnapshot` 的字段校验，修正 `SnapshotProducer` 只统计 DATA manifest 的新增行数（删除文件不计入）。涉及 TableMetadata 构造签名变更，调用方（RewriteTablePathUtil、Parser、测试）均同步适配。
