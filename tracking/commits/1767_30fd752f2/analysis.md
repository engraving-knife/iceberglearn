# 提交 1767：Core, Spark: Remove deprecated code for 1.9.0 (#12336)

## 提交信息

- **序号**：1767 / 4088
- **哈希**：30fd752f24d796ce548dbeef550e875941ce6fb7
- **短哈希**：30fd752f2
- **日期**：2025-02-20 15:15:03 +0100
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core, Spark: Remove deprecated code for 1.9.0 (#12336)
- **PR/Issue**：#12336

## 总体目的

本提交旨在移除 Core 和 Spark 模块中在 1.8.0 版本标记为废弃（deprecated）的 API，为 1.9.0 版本做清理准备。这是 Iceberg 项目废弃 API 管理的一部分——在 1.8.0 中标记为 `@Deprecated` 且计划在 1.9.0 中移除的 API 在此提交中被实际移除。

具体移除/修改的废弃 API 包括：

1. **`TableMetadata.updateSchema(Schema, int)`**：废弃的双参数版本，接受 `lastColumnId` 参数。被 `updateSchema(Schema)` 替代，后者自动从 schema 获取 `highestFieldId`。

2. **`TableMetadata.Builder.addSchema(Schema, int)`**：废弃的双参数版本。被 `addSchema(Schema)` 替代。

3. **`TableMetadata.Builder.setStatistics(long, StatisticsFile)`**：废弃的双参数版本，接受 `snapshotId` 参数。被 `setStatistics(StatisticsFile)` 替代，后者从 statisticsFile 获取 snapshotId。

4. **`SetStatistics.setStatistics(long, StatisticsFile)`**：废弃的实现方法。改为 default 方法委托给新版本。

5. **`MetadataUpdate.AddSchema(Schema, int)`**：废弃的双参数构造器。被 `AddSchema(Schema)` 替代，`lastColumnId` 改为从 `schema.highestFieldId()` 计算。

6. **`MetadataUpdate.SetStatistics(long, StatisticsFile)`**：废弃的双参数构造器。被 `SetStatistics(StatisticsFile)` 替代。

## 如何达成设计目的

提交通过以下策略完成移除：

1. **修改 API 接口**：
   - `UpdateStatistics` 接口中，将废弃的 `setStatistics(long, StatisticsFile)` 从抽象方法改为 default 方法，委托给 `setStatistics(StatisticsFile)`，并更新废弃说明（移除时间从"1.9.0 or 2.0.0"改为"2.0.0"）。
   - `SetStatistics` 实现类中移除废弃方法的实现（已在接口中作为 default 方法提供）。

2. **移除废弃方法/构造器**：从 `TableMetadata`、`TableMetadata.Builder`、`MetadataUpdate.AddSchema`、`MetadataUpdate.SetStatistics` 中移除废弃的方法和构造器。

3. **简化内部实现**：`MetadataUpdate.AddSchema` 不再存储 `lastColumnId` 字段，改为在 `lastColumnId()` 方法中调用 `schema.highestFieldId()` 动态计算。`MetadataUpdateParser.readAddSchema` 不再从 JSON 读取 `last-column-id`，直接使用 `AddSchema(schema)` 构造。

4. **更新所有调用方**：将 Core 和 Spark 模块中所有使用废弃 API 的调用更新为使用新 API。涉及测试文件和少量源代码文件。

5. **更新 RevAPI 配置**：在 `.palantir/revapi.yml` 中记录这些 API 变更。

## 修改详情

### `.palantir/revapi.yml`（修改, +24/-0 lines）

**修改目的**：记录 1.8.0 版本中 `iceberg-core` 的 API 破坏性变更。

**工作逻辑**：新增 6 条 `java.method.removed` 记录，分别对应被移除的 6 个废弃方法/构造器。

### `api/src/main/java/org/apache/iceberg/UpdateStatistics.java`（修改, +4/-3 lines）

**修改目的**：将废弃方法改为 default 方法，延长废弃周期到 2.0.0。

**工作逻辑**：将 `setStatistics(long snapshotId, StatisticsFile statisticsFile)` 从抽象方法改为 default 方法，实现为委托调用 `setStatistics(statisticsFile)`。更新 Javadoc 中的移除时间从"1.9.0 or 2.0.0"改为"2.0.0"。

### `core/src/main/java/org/apache/iceberg/MetadataUpdate.java`（修改, +3/-24 lines）

**修改目的**：移除 `AddSchema` 和 `SetStatistics` 的废弃构造器。

**工作逻辑**：
- `AddSchema`：移除 `lastColumnId` 字段和废弃的双参数构造器 `AddSchema(Schema, int)`。单参数构造器 `AddSchema(Schema)` 不再委托给双参数版本，直接设置 `schema`。`lastColumnId()` 方法改为返回 `schema.highestFieldId()` 而非存储的字段值。`applyTo` 方法改为调用 `metadataBuilder.addSchema(schema)` 而非 `metadataBuilder.addSchema(schema, lastColumnId)`。
- `SetStatistics`：移除废弃的双参数构造器 `SetStatistics(long, StatisticsFile)`。

### `core/src/main/java/org/apache/iceberg/MetadataUpdateParser.java`（修改, +1/-7 lines）

**修改目的**：简化 `readAddSchema` 方法，不再读取 `last-column-id`。

**工作逻辑**：移除从 JSON 节点读取 `LAST_COLUMN_ID` 的逻辑，直接使用 `new MetadataUpdate.AddSchema(schema)` 构造。

### `core/src/main/java/org/apache/iceberg/SetStatistics.java`（修改, +1/-16 lines）

**修改目的**：移除废弃的 `setStatistics(long, StatisticsFile)` 实现。

**工作逻辑**：移除废弃方法实现（已在接口中作为 default 方法提供）。`commit` 方法中调用改为 `builder.setStatistics(statistics.get())` 而非 `builder.setStatistics(snapshotId, statistics.get())`。

### `core/src/main/java/org/apache/iceberg/TableMetadata.java`（修改, +2/-39 lines）

**修改目的**：移除 `updateSchema(Schema, int)`、`Builder.addSchema(Schema, int)`、`Builder.setStatistics(long, StatisticsFile)` 废弃方法。

**工作逻辑**：
- 移除 `updateSchema(Schema newSchema, int newLastColumnId)` 方法。
- 移除 `Builder.addSchema(Schema, int)` 方法。
- 移除 `Builder.setStatistics(long, StatisticsFile)` 方法（含参数校验逻辑）。
- 内部调用 `changes.add(new MetadataUpdate.AddSchema(newSchema, lastColumnId))` 改为 `changes.add(new MetadataUpdate.AddSchema(newSchema))`。

### 测试文件（修改, 多个文件）

**修改目的**：更新所有测试中对废弃 API 的调用。

**工作逻辑**：将所有 `setStatistics(snapshotId, statisticsFile)` 调用改为 `setStatistics(statisticsFile)`，将 `updateSchema(schema, lastColumnId)` 调用改为 `updateSchema(schema)`，将 `setStatistics(43, ...)` 等双参数 Builder 调用改为单参数版本。涉及 Core 和 Spark（v3.4/v3.5）模块的多个测试文件。

## 小结

- **成效**：移除了 Core 和 Spark 模块中 6 个在 1.8.0 标记废弃的 API 方法/构造器，为 1.9.0 版本清理了废弃代码。`UpdateStatistics.setStatistics(long, StatisticsFile)` 被保留为 default 方法（延长到 2.0.0 移除），其余方法被直接移除。
- **影响范围**：涉及 API 模块（`UpdateStatistics`）、Core 模块（`TableMetadata`、`MetadataUpdate`、`SetStatistics`、`MetadataUpdateParser`）以及 Spark v3.4/v3.5 模块的测试和少量源代码。这是破坏性 API 变更。
- **回迁到 1.4.x 的注意事项**：不建议回迁到 1.4.x 分支。这些 API 是在 1.8.0 中才标记为废弃的，1.4.x 分支中可能尚未标记废弃或甚至不存在这些废弃方法。直接移除会跳过废弃过渡期。仅在 1.4.x 分支已完成废弃周期的情况下才考虑回迁。注意 `UpdateStatistics.setStatistics(long, StatisticsFile)` 被改为 default 方法而非直接移除，这是一种更温和的迁移策略。
