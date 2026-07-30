# 提交 2204：Flink: Dynamic Iceberg Sink: Add table update code for schema comparison and evolution (#13032)

## 提交信息

- **序号**：2204 / 4088
- **哈希**：12605226736312970d787e704d4b7352a2bc2c1c
- **短哈希**：126052267
- **日期**：2025-06-04 07:35:53 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Dynamic Iceberg Sink: Add table update code for schema comparison and evolution (#13032)
- **PR/Issue**：#13032

## 总体目的

这个提交是 Flink Dynamic Iceberg Sink 功能的第一部分，为动态 sink 添加表更新代码，实现 schema 比较与演进能力。Dynamic Iceberg Sink 的目标是让 Flink sink 在运行时能够动态地写入多个不同的 Iceberg 表，并且当输入数据的 schema 或分区规格与目标表不一致时，能够自动创建表、演进 schema 和分区规格。这解决了传统 Flink sink 只能写入固定表、固定 schema 的限制。本提交引入了核心的比较与演进逻辑：`CompareSchemasVisitor` 用于比较输入 schema 与表 schema 的兼容性（SAME、DATA_CONVERSION_NEEDED、SCHEMA_UPDATE_NEEDED），`EvolveSchemaVisitor` 用于计算需要执行的 schema 变更操作，`TableUpdater` 负责协调表的创建、分支创建、schema 和分区规格的更新，`TableMetadataCache` 用于缓存表元数据以减少重复的 catalog 操作，`PartitionSpecEvolution` 用于处理分区规格的演进。

## 如何达成设计目的

- 新增 `CompareSchemasVisitor`：基于 `SchemaWithPartnerVisitor` 比较输入 schema 与表 schema，输出三种兼容性结果（SAME、DATA_CONVERSION_NEEDED、SCHEMA_UPDATE_NEEDED），通过字段名匹配进行比较。
- 新增 `EvolveSchemaVisitor`：计算从当前表 schema 演进到输入 schema 所需的具体变更操作（如添加字段、修改字段等）。
- 新增 `TableUpdater`：核心协调器，负责 find-or-create 表/分支/schema/分区规格，处理并发创建冲突（AlreadyExistsException），并利用缓存避免重复操作。
- 新增 `TableMetadataCache`：缓存表是否存在、分支、schema、分区规格等元数据，减少 catalog 远程调用。
- 新增 `PartitionSpecEvolution`：处理分区规格的演进逻辑。
- 为每个新类配备对应的测试类，验证比较、演进、缓存、更新逻辑的正确性。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/CompareSchemasVisitor.java` (新增, +266/-0 lines)

**修改目的**：比较输入 schema 与表 schema 的兼容性。

**工作逻辑**：继承 `SchemaWithPartnerVisitor`，通过字段名匹配比较两个 schema。定义 `Result` 枚举：SAME（语义相同）、DATA_CONVERSION_NEEDED（需数据转换）、SCHEMA_UPDATE_NEEDED（需更新表 schema）。提供 `visit(Schema dataSchema, Schema tableSchema, boolean caseSensitive)` 静态方法。遍历结构体字段，递归比较，合并各字段的比较结果。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/EvolveSchemaVisitor.java` (新增, +204/-0 lines)

**修改目的**：计算 schema 演进所需的变更操作。

**工作逻辑**：访问输入 schema 与表 schema，计算需要的 `UpdateSchema` 变更操作（如添加新列、类型变更等），为 `TableUpdater` 执行实际 schema 演进提供依据。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/PartitionSpecEvolution.java` (新增, +137/-0 lines)

**修改目的**：处理分区规格的演进逻辑。

**工作逻辑**：比较输入分区规格与表当前分区规格，计算是否需要新增分区规格，并生成相应的 `UpdatePartitionSpec` 操作。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (新增, +261/-0 lines)

**修改目的**：缓存表元数据，减少 catalog 远程调用。

**工作逻辑**：缓存表是否存在（含异常信息）、分支、schema、分区规格等。提供 `exists`、`branch`、`schema`、`invalidate`、`update` 等方法。当缓存未命中时返回 null/默认值，调用方据此决定是否查询 catalog。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableUpdater.java` (新增, +207/-0 lines)

**修改目的**：协调表的创建、分支创建、schema 和分区规格更新。

**工作逻辑**：
- `update(TableIdentifier, branch, schema, spec)`：主入口，依次调用 findOrCreateTable、findOrCreateBranch、findOrCreateSchema、findOrCreateSpec，返回新 schema、比较结果、新分区规格。
- `findOrCreateTable`：若表不存在则创建（必要时先创建命名空间），处理并发创建冲突（AlreadyExistsException 时刷新缓存重试）。
- `findOrCreateBranch`：若分支不存在则创建，处理并发冲突。
- `findOrCreateSchema`：通过 `CompareSchemasVisitor` 比较，若需更新则用 `EvolveSchemaVisitor` 计算变更并提交 `UpdateSchema`。
- `findOrCreateSpec`：比较分区规格，必要时提交 `UpdatePartitionSpec`。

### 测试文件（5 个新增测试类, +1414/-0 lines）

**修改目的**：为上述新类提供测试覆盖。

**工作逻辑**：`TestCompareSchemasVisitor`（209 行）验证 schema 比较逻辑；`TestEvolveSchemaVisitor`（623 行）验证 schema 演进计算；`TestPartitionSpecEvolution`（188 行）验证分区规格演进；`TestTableMetadataCache`（94 行）验证缓存行为；`TestTableUpdater`（160 行）验证表更新协调逻辑。

## 总结

该提交为 Flink Dynamic Iceberg Sink 引入了 schema 比较与演进的完整基础设施，包括 schema 比较、演进计算、表更新协调和元数据缓存。这是动态 sink 功能的关键基础组件，使 sink 能够在运行时自动创建表并演进 schema/分区规格，为后续的动态 writer 和 committer（见 #13080）奠定基础。
