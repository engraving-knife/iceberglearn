# 提交 0001：Core: Fix view version ID reassigment and deduplication, start schema ID at 0 (#8664)

## 提交信息

- **序号**：0001 / 4088
- **哈希**：eaf7c4f2e2adc952b5a150cf0b854adebfb9fdf0
- **短哈希**：eaf7c4f2e
- **日期**：2023-09-28 09:49:10 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Fix view version ID reassigment and deduplication, start schema ID at 0 (#8664)
- **PR/Issue**：#8664

## 总体目的

这个提交修复了 Iceberg 视图（View）元数据构建过程中的几个关键问题。视图是 Iceberg 1.4 引入的较新能力，其元数据（`ViewMetadata`）由若干 `ViewVersion`（视图版本，每个版本对应一段 SQL 表示）和若干 `Schema`（视图的输出 schema）构成，每个版本与 schema 都带有一个整数 ID。在构建视图元数据时，需要为新增的版本/schema 分配 ID，并在可能的情况下复用已有的 ID（即"去重"）。本提交前，`ViewMetadata.Builder` 在这两个方面都存在缺陷。

第一个缺陷是版本 ID 重新分配逻辑有 bug。原代码在 `reuseOrCreateNewVersionId` 中遍历已有版本时，当发现某个已有版本的 ID 大于等于当前候选 ID 时，将 `newVersionId` 重置为 `viewVersion.versionId() + 1`（即"新版本自报 ID + 1"），这会忽略掉已经遍历过的更高 ID，导致在多个输入版本携带相同 ID（如全部为 1）时，新分配的 ID 会与已有版本 ID 冲突，而不是稳定递增。本提交将其改为 `version.versionId() + 1`（"遍历中遇到的最大 ID + 1"），从而正确地从已分配的最大 ID 递增。

第二个缺陷是版本去重的判定过严。原代码用 `version.equals(viewVersion)` 来判断两个版本是否等价（从而复用旧 ID），但 `ViewVersion` 的 `equals` 会比较 `versionId`、`timestampMillis`、`summary` 等字段。这意味着即使是同一段 SQL，只要创建时间或 summary 不同就会被当作新版本，无法真正去重。本提交引入 `sameViewVersion` 方法，仅比较 `representations`、`defaultCatalog`、`defaultNamespace`、`schemaId` 这些"行为相关"字段，从而实现真正语义上的版本去重。

第三个变更是 schema ID 起始值从 1 改为 0。原 `reuseOrCreateNewSchemaId` 把新 schema 的 ID 初始值设为 `newSchema.schemaId()`（即 schema 自报 ID），这与表（Table）schema ID 从 0 开始的约定不一致，且当传入的 schema 没有显式设置 ID 时会产生不稳定的分配。本提交引入常量 `INITIAL_SCHEMA_ID = 0`，使视图的 schema ID 与表保持一致，从 0 开始连续递增。

这些修复对 Iceberg 视图元数据的正确性和一致性至关重要，确保视图版本历史不会因 ID 冲突而损坏，去重能减少冗余版本，而 schema ID 从 0 起始使视图与表的 ID 约定统一，便于跨模块复用与序列化/反序列化的一致性。

## 如何达成设计目的

整体设计思路是在 `ViewMetadata.Builder` 内部修正 ID 分配与去重的两条核心逻辑，并把 schema ID 起始值常量化。改动集中在 `ViewMetadata.java` 的 `Builder` 内部类：新增 `INITIAL_SCHEMA_ID` 常量、修改 `reuseOrCreateNewVersionId` 中的递增分支与去重判定、新增 `sameViewVersion` 私有方法、修改 `reuseOrCreateNewSchemaId` 的初值。配套地，测试代码与测试资源 JSON 同步将 schema ID 从 1 调整为 0，并新增多个针对 ID 重分配与去重场景的测试用例，验证修复后的行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/view/ViewMetadata.java`

**修改目的**：修复视图版本 ID 重新分配、版本去重判定，以及 schema ID 起始值。

**工作逻辑**：

1. 新增常量 `INITIAL_SCHEMA_ID = 0`，作为 schema ID 分配的起点。

2. `reuseOrCreateNewVersionId` 中两处关键改动：
   - 去重判定由 `version.equals(viewVersion)` 改为 `sameViewVersion(version, viewVersion)`，避免 `versionId`/`timestampMillis`/`summary` 差异导致无法复用。
   - 当 `version.versionId() >= newVersionId` 时，将 `newVersionId = viewVersion.versionId() + 1` 改为 `newVersionId = version.versionId() + 1`。前者会在输入版本 ID 全部相同时（例如都为 1）导致 `newVersionId` 始终为 2，覆盖不了更高已分配 ID；后者保证新 ID 严格大于所有已分配 ID。

3. 新增私有方法 `sameViewVersion(ViewVersion one, ViewVersion two)`，仅比较 `representations`、`defaultCatalog`、`defaultNamespace`、`schemaId` 四个字段（用 `Objects.equals` 与 `==`），明确忽略 `versionId`、`timestampMillis`、`summary`，从而实现"行为等价即视为同一版本"的去重语义。方法上有详细 Javadoc 说明比较范围与目的。

4. `reuseOrCreateNewSchemaId` 中，将 `int newSchemaId = newSchema.schemaId()` 改为 `int newSchemaId = INITIAL_SCHEMA_ID`。这样当传入 schema 不复用任何已有 schema 时，新 ID 从 0 开始按已有 schema 数量递增分配，而不是依赖传入 schema 自带的 ID。这一改动使视图 schema ID 与表 schema ID 的分配约定一致。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadata.java`

**修改目的**：适配 schema ID 从 0 起始的新约定，并新增覆盖 ID 重分配与去重的测试用例。

**工作逻辑**：

1. 测试辅助方法 `newViewVersion` 重载：新增 `newViewVersion(int id, int schemaId, String sql)`，原 `newViewVersion(int id, String sql)` 委托给新方法并固定 `schemaId = 0`。这使得构造测试版本时可灵活指定 schema ID。

2. 大量既有测试中，`new Schema(1, ...)` 被改为 `new Schema(...)`（即不指定 ID，让 Builder 分配），`schemaId(1)` 改为 `schemaId(0)`，预期 schema ID 从 1 改为 0，与生产代码约定变更对齐。同时把原本手写构造的多个 `ImmutableViewVersion` 替换为 `newViewVersion(...)` 调用，使测试更简洁。

3. 新增 6 个测试用例：
   - `viewVersionIDReassignment`：三个版本 ID 全为 1，验证 Builder 将其重排为 1/2/3。
   - `viewVersionDeduplication`：在上一用例基础上加入仅时间戳或 summary 不同的重复版本，验证去重后仅剩 3 个版本，ID 为 1/2/3。
   - `schemaIDReassignment`：传入 schema ID 为 5/7/9，验证重排为 0/1/2，且 `setCurrentVersion(viewVersion, schemaThree)` 引用的 schema ID 被更新为 2。
   - `schemaDeduplication`：在上一用例基础上加入结构相同但 ID 不同的 schema（6/8/10），验证去重后仅剩 3 个 schema，ID 为 0/1/2。
   - `viewVersionAndSchemaIDReassignment`：版本 ID 与 schema ID 都需要重排，验证版本与 schema 的 ID 联动正确。
   - `viewVersionAndSchemaDeduplication`：综合版本与 schema 都有重复且 ID 相同的场景，验证去重与重排后版本为 1/2/3、schema 为 0/1/2，且当前版本正确指向最后设置的版本。

4. `viewMetadataAndMetadataChanges` 中断言从直接比较 `Schema` 对象改为比较 `Schema::asStruct`，因为传入 schema 的 ID（如 0/1）会被 Builder 重新分配，但结构不变，比较结构更稳健。

### `core/src/test/java/org/apache/iceberg/view/TestViewMetadataParser.java`

**修改目的**：适配 schema ID 从 0 起始的约定。

**工作逻辑**：测试用的 `TEST_SCHEMA` 不再显式指定 schema ID（移除 `1`），各 `ViewVersion` 的 `schemaId(1)` 改为 `schemaId(0)`，使序列化/反序列化测试与生产代码的 ID 约定一致。

### `core/src/test/resources/org/apache/iceberg/view/ValidViewMetadata.json`

**修改目的**：更新期望的 JSON 测试资源，使 schema ID 与版本引用的 schema ID 从 1 改为 0。

**工作逻辑**：`current-schema-id` 由 1 改为 0；`schemas` 数组中 `schema-id` 由 1 改为 0；两个版本的 `schema-id` 由 1 改为 0。这样解析器测试在新的 ID 约定下能通过。

## 小结

本提交通过修正 `ViewMetadata.Builder` 中版本 ID 递增、版本语义去重以及 schema ID 从 0 起始三处逻辑，使 Iceberg 视图元数据的 ID 分配与表一致、稳定且无冲突，为视图功能的正确性奠定基础。
