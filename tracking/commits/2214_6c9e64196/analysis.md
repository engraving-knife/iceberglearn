# 提交 2214：Flink: Backport Dynamic Iceberg Sink: Add table update code for schema comparison and evolution to Flink 1.19 / 1.20 (#13247)

## 提交信息

- **序号**：2214 / 4088
- **哈希**：6c9e641968e7169da3aa63881031c40e73ac6003
- **短哈希**：6c9e64196
- **日期**：2025-06-05 16:46:25 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport Dynamic Iceberg Sink: Add table update code for schema comparison and evolution to Flink 1.19 / 1.20 (#13247) Backports #13032
- **PR/Issue**：#13247（backport #13032）

## 总体目的

这个提交是 PR #13032 的反向移植，为 Iceberg Flink 动态 Sink 功能添加表更新（schema 比较与演化、分区规格演化）的代码。在动态 sink 场景下，流中的数据可能属于不同的 Iceberg 表，且每个表的 schema 和分区规格可能随时间变化，甚至目标表可能尚不存在。因此需要一个机制来：检查输入数据 schema 与目标表 schema 的兼容性；在必要时自动演化表 schema 或分区规格以适配新数据；自动创建不存在的表、命名空间和分支。本提交引入了 `CompareSchemasVisitor`（schema 比较访问器）、`EvolveSchemaVisitor`（schema 演化访问器）、`PartitionSpecEvolution`（分区规格演化）、`TableMetadataCache`（表元数据缓存）和 `TableUpdater`（表更新协调器）等核心类，共同实现上述能力。同步到 Flink 1.19 和 1.20 两个模块。

## 如何达成设计目的

- 引入 `CompareSchemasVisitor`：基于 `SchemaWithPartnerVisitor` 比较输入 schema 和表 schema，返回三种结果：SAME（完全相同）、DATA_CONVERSION_NEEDED（数据需转换但表 schema 不变）、SCHEMA_UPDATE_NEEDED（需更新表 schema）。
- 引入 `EvolveSchemaVisitor`：根据比较结果，通过 Iceberg 的 `UpdateSchema` API 执行 schema 演化（添加/删除/修改字段）。
- 引入 `PartitionSpecEvolution`：检查两个 PartitionSpec 的兼容性，并计算需要移除和添加的分区字段（termsToRemove/termsToAdd）。
- 引入 `TableMetadataCache`：基于 Caffeine 缓存表元数据（存在性、分支、schemas、specs、schema 比较结果），带 TTL 刷新机制，避免频繁访问 catalog。缓存 schema 比较结果以加速热路径。
- 引入 `TableUpdater`：协调表/分支/schema/spec 的创建和更新流程，处理并发创建冲突（AlreadyExistsException/CommitFailedException），确保在并发环境下幂等安全。
- 提供完整的单元测试覆盖各组件。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableUpdater.java` (新增, +207 lines)

**修改目的**：协调表、分支、schema、分区规格的创建和更新。

**工作逻辑**：
- `update(tableIdentifier, branch, schema, spec)`：依次调用 findOrCreateTable、findOrCreateBranch、findOrCreateSchema、findOrCreateSpec，返回 Tuple3（新 Schema、比较结果、新 PartitionSpec）。
- `findOrCreateTable`：通过缓存检查表是否存在，不存在则创建命名空间（若需要）和表。处理并发创建冲突（AlreadyExistsException 时 invalidate 缓存并重试）。
- `findOrCreateBranch`：通过缓存检查分支是否存在，不存在则创建分支。处理并发创建冲突。
- `findOrCreateSchema`：通过缓存检查 schema 兼容性。若需更新（SCHEMA_UPDATE_NEEDED），使用 EvolveSchemaVisitor 演化 schema 并提交。处理并发提交冲突。
- `findOrCreateSpec`：通过缓存检查 spec 兼容性。若不兼容，使用 PartitionSpecEvolution 计算变更并通过 UpdatePartitionSpec 提交。处理并发提交冲突。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/TableMetadataCache.java` (新增, +261 lines)

**修改目的**：缓存表元数据以减少 catalog 访问，提升热路径性能。

**工作逻辑**：
- 使用 Caffeine 缓存按 TableIdentifier 存储 CacheItem（包含 tableExists、branches、SchemaInfo、specs）。
- `exists(identifier)`：检查表是否存在，带 TTL 刷新。
- `branch(identifier, branch)`：检查分支是否存在。
- `schema(identifier, input)`：检查输入 schema 与表 schema 的兼容性。优先查缓存中的比较结果（LimitedLinkedHashMap 最多 10 条），未命中则遍历表的所有 schemas 用 CompareSchemasVisitor 比较。
- `spec(identifier, spec)`：检查分区规格兼容性，遍历表的 specs 用 PartitionSpecEvolution.checkCompatibility。
- `needsRefresh`：基于 created + refreshMs 判断是否需要刷新。
- `SchemaInfo`：存储 schemas map 和最近比较结果（LimitedLinkedHashMap），超限时记录 warn 日志提示性能下降。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/CompareSchemasVisitor.java` (新增, +266 lines)

**修改目的**：比较输入数据 schema 与目标表 schema 的兼容性。

**工作逻辑**：
- 继承 `SchemaWithPartnerVisitor<Integer, Result>`，按字段名匹配比较。
- 三种结果枚举：SAME（语义相同）、DATA_CONVERSION_NEEDED（数据可转换为表 schema）、SCHEMA_UPDATE_NEEDED（需更新表 schema）。
- Result 提供 merge 方法用于合并多个字段的比较结果。
- 支持大小写敏感/不敏感两种模式。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/EvolveSchemaVisitor.java` (新增, +204 lines)

**修改目的**：通过 Iceberg UpdateSchema API 执行 schema 演化。

**工作逻辑**：基于 SchemaWithPartnerVisitor 遍历输入 schema 和表 schema 的差异，调用 UpdateSchema 的对应方法（addColumn、deleteColumn、updateColumn 等）执行演化操作。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/PartitionSpecEvolution.java` (新增, +137 lines)

**修改目的**：检查分区规格兼容性并计算演化变更。

**工作逻辑**：
- `checkCompatibility(spec1, spec2)`：比 PartitionSpec.compatible 更宽松，容忍不同命名的分区字段，只要 transform 和源字段匹配即可。
- `evolve(currentSpec, targetSpec)`：返回 PartitionSpecChanges，包含 termsToRemove 和 termsToAdd，用于通过 UpdatePartitionSpec 演化分区规格。

### 测试文件（新增, 共约 +1304 lines per version）

**修改目的**：验证各组件的正确性。

**工作逻辑**：
- `TestCompareSchemasVisitor.java`（+209）：测试 schema 比较的各种场景。
- `TestEvolveSchemaVisitor.java`（+623）：测试 schema 演化的各种场景，最全面的测试。
- `TestPartitionSpecEvolution.java`（+188）：测试分区规格兼容性检查和演化。
- `TestTableMetadataCache.java`（+94）：测试缓存的刷新和命中行为。
- `TestTableUpdater.java`（+160）：测试表更新协调器的端到端流程。

### Flink 1.19 模块的相同文件

**修改目的**：将相同代码同步到 Flink 1.19 模块。

**工作逻辑**：与 1.20 模块完全相同的文件集合，确保两个 Flink 版本功能一致。

## 总结

该提交为 Iceberg Flink 动态 Sink 功能引入了表元数据管理和自动演化的核心逻辑。CompareSchemasVisitor 和 EvolveSchemaVisitor 处理 schema 兼容性检查和演化，PartitionSpecEvolution 处理分区规格演化，TableMetadataCache 通过缓存优化热路径性能，TableUpdater 协调完整的创建和更新流程并处理并发冲突。该提交是动态 sink 功能的关键基础设施，为后续完整的动态 sink 实现提供了表管理能力。
