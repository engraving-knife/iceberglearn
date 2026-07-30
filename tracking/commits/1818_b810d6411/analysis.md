# 提交 1818：Core: Change RemoveSnapshots to remove unused schemas (#12089)

## 提交信息

- **序号**：1818 / 4088
- **哈希**：b810d64112b88498c2db222abfaa10a3ab037e2e
- **短哈希**：b810d6411
- **日期**：2025-03-04 16:54:50 +0100
- **作者**：gaborkaszab
- **提交说明**：Core: Change RemoveSnapshots to remove unused schemas (#12089)
- **PR/Issue**：#12089

## 总体目的

该提交为快照过期（ExpireSnapshots / RemoveSnapshots）流程增加了清理未使用 schema 的能力。在此之前，RemoveSnapshots 在清理过期元数据时已经会移除不再被任何保留快照引用的分区 spec（partition spec），但 schema 的清理一直是一个 TODO 项（原代码注释 `// TODO: Support cleaning expired schema as well.`）。

随着表 schema 的演进（如 ALTER TABLE 替换 schema），旧的 schema 会累积在表元数据中。当这些旧 schema 对应的快照过期后，旧 schema 不再被任何快照引用，但仍残留在 TableMetadata 的 schemas 列表中，导致元数据文件膨胀、元数据加载开销增大。本提交通过让 RemoveSnapshots 在清理过期元数据时一并移除未被引用的 schema，保持表元数据精简。

此外，该提交还补充了完整的 `RemoveSchemas` MetadataUpdate 类型、对应的 JSON 序列化/反序列化支持、以及 UpdateRequirements 中的冲突检测逻辑，使 schema 移除操作在 commit 时能正确校验当前 schema 未被并发修改。

## 如何达成设计目的

整体设计沿用已有的 RemovePartitionSpecs 模式：新增 `MetadataUpdate.RemoveSchemas` 更新类型，在 RemoveSnapshots 中计算可达 schema 集合（当前 schema + 所有保留快照的 schema），移除不可达的 schema，并通过 TableMetadata.Builder 应用移除。同时重构 UpdateRequirements，将 schema/spec 变更校验逻辑抽取为可复用的私有方法，避免重复代码，并为新的 RemoveSchemas 添加相同的冲突检测（断言当前 schema 未变、断言 refs 未变）。

## 修改详情

### core/src/main/java/org/apache/iceberg/MetadataUpdate.java (新增, 17 lines)

新增 `RemoveSchemas` 内部类实现 `MetadataUpdate` 接口，包含 `Set<Integer> schemaIds` 字段、构造方法、`schemaIds()` 访问器，以及 `applyTo()` 方法委托给 `TableMetadata.Builder.removeSchemas()`。与已有的 `RemovePartitionSpecs` 结构对称。

### core/src/main/java/org/apache/iceberg/RemoveSnapshots.java (修改, 22 lines)

核心改动点：在 `cleanExpiredMetadata` 分支中，新增 `reachableSchemas` 集合，初始包含当前 schema id。在遍历保留快照时，除了收集可达的 partition spec id，同时收集每个快照的 `schemaId` 加入 reachableSchemas。随后计算 schemasToRemove（表 schemas 中不在 reachableSchemas 中的），调用 `updatedMetaBuilder.removeSchemas(schemasToRemove)`。移除了原有的 TODO 注释。

### core/src/main/java/org/apache/iceberg/TableMetadata.java (修改, 19 lines)

将 `schemas` 字段从 `final` 改为非 final（以支持移除后重新赋值）。新增 `Builder.removeSchemas(Iterable<Integer> schemaIds)` 方法，校验不能移除当前 schema，过滤掉要移除的 schema 重建列表，并记录 `RemoveSchemas` 变更。注意原 `removeSpecs` 方法末尾遗漏了 `return this;`，本次一并修复。

### core/src/main/java/org/apache/iceberg/MetadataUpdateParser.java (修改, 19 lines)

新增 `REMOVE_SCHEMAS = "remove-schemas"` 动作常量和 `SCHEMA_IDS = "schema-ids"` 字段名。注册 RemoveSchemas 类到 ACTIONS 映射，添加 write/read 分支，实现 JSON 序列化（写 schema-ids 整数数组）和反序列化（读取整数集合）。

### core/src/main/java/org/apache/iceberg/UpdateRequirements.java (修改, 45 lines)

重构冲突检测逻辑：将 `update(SetCurrentSchema)` 和 `update(SetDefaultPartitionSpec)` 中重复的校验代码抽取为 `requireCurrentSchemaNotChanged()` 和 `requireDefaultPartitionSpecNotChanged()` 私有方法。为 `update(RemovePartitionSpecs)` 复用这些方法。新增 `update(RemoveSchemas)` 处理，要求当前 schema 未变更且无分支变更，确保移除的 schema 不会被并发操作引用。

### 测试文件 (新增/修改, 多个文件)

- `TestRemoveSnapshots.java`（75 行）：新增测试验证过期快照后未使用 schema 被移除、当前 schema 保留。
- `TestTableMetadata.java`（16 行）：测试 Builder.removeSchemas 行为及当前 schema 不可移除。
- `TestMetadataUpdateParser.java`（21 行）：测试 RemoveSchemas 的 JSON 序列化/反序列化。
- `TestUpdateRequirements.java`（174 行）：测试 RemoveSchemas 的冲突检测场景。
- `TestTables.java`（29 行）：测试基础设施支持 schema 移除。
- `CatalogTests.java`（61 行）：端到端 catalog 测试验证过期快照清理 schema。

## 小结

该提交完善了快照过期流程的元数据清理能力，使表元数据在 schema 演进后保持精简。影响范围为 core 模块的元数据管理，涉及新的 MetadataUpdate 类型及其序列化支持。回迁到 1.4.x 分支时需注意：1.4.x 分支的 RemoveSnapshots、TableMetadata.Builder、UpdateRequirements、MetadataUpdateParser 等文件结构可能有所不同，需逐文件核对冲突检测逻辑的重构是否能直接合入；新增的 RemoveSchemas 类和 JSON 解析可较安全地回迁。建议整体回迁以保持功能完整性。
