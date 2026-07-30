# 提交 0765：AWS: Retain Glue Catalog table description after updating Iceberg table (#10199)

## 提交信息

- **序号**：0765 / 4088
- **哈希**：2058053b0c6e5b1c7e91fa029162f22d109aafb1
- **短哈希**：2058053b0
- **日期**：2024-05-15 09:08:47 +0900
- **作者**：Akira Ajisaka（Amazon）
- **提交说明**：AWS: Retain Glue Catalog table description after updating Iceberg table (#10199)
- **PR/Issue**：#10199

## 总体目的

本提交修复一个**数据丢失缺陷**：当 Iceberg 表通过 `GlueTableOperations` 提交更新（如 schema 变更、属性变更）时，Glue Data Catalog 中该表的 `description` 字段会被**意外清空**。

### 缺陷根因

`GlueTableOperations` 在 commit 时会构造一个 `UpdateTableRequest`，其中 `TableInput` 通过 `applyMutation`（内部调用 `IcebergToGlueConverter.setTableInputInformation`）来填充 Iceberg 元数据对应的 StorageDescriptor、columns 等信息。但构造 `TableInput.Builder` 时，**没有显式设置 `description` 字段**。

Glue 的 `UpdateTableRequest` 语义是"整体替换 TableInput"而非"局部更新"——若 `TableInput` 中未设置 `description`，则 Glue 会将表描述置空。因此，只要用户通过非 Iceberg 渠道（如 Glue 控制台、Athena、Terraform 或直接调用 Glue API）为表设置了 description，下一次 Iceberg commit 就会把它抹掉。

### 修复目标

在 `TableInput.Builder` 构造时，**先从当前 Glue 表读取已有的 `description` 并设置到 builder**，然后再执行 `applyMutation`。这样：
- 若用户未在 Iceberg 表属性中指定 comment（`glue.description`），则 description 保持 Glue 表原值不变。
- 若用户在 Iceberg 表属性中指定了 comment（通过 `updateProperties().set(GLUE_DESCRIPTION_KEY, ...)`），则 `applyMutation` 内部的 `setTableInputInformation` 会用新值覆盖先设置的旧值——因为 `applyMutation` 在 `.description(...)` 之后执行，Builder 后设置的值生效。

注释中明确说明了这一顺序意图："Call description before applyMutation so that applyMutation overwrites the description with the comment specified in the query"。

## 如何达成设计目的

### 设计逻辑：先保留后覆盖的两段式构造

修复采用"防御性保留 + 显式覆盖"策略，核心是利用 `TableInput.Builder` 的"后设值覆盖先设值"特性：

1. **保留阶段**：`.description(glueTable.description())` —— 从 commit 前的 Glue 表对象读取当前 description，设置到 builder。若表无 description，此处设置 null（Glue 表 description 为 null 时等于未设置，不影响）。
2. **覆盖阶段**：`.applyMutation(builder -> IcebergToGlueConverter.setTableInputInformation(builder, metadata))` —— `setTableInputInformation` 会检查 Iceberg 表属性中是否有 `glue.description`（即 `GLUE_DESCRIPTION_KEY = "comment"`），若有则调用 `builder.description(commentValue)` 覆盖。

这一顺序保证：
- 无 comment 属性时：description = 保留的 Glue 原值（不被清空）。
- 有 comment 属性时：description = comment 属性值（Iceberg 主导，符合"通过表属性管理描述"的预期）。

### 为什么不在 applyMutation 中直接处理？

理论上也可以在 `setTableInputInformation` 中读取 Glue 表原 description 并设置，但该方法的签名只接收 `TableInput.Builder` 和 `TableMetadata`，不持有 `glueTable` 引用。修改签名会波及所有调用点。而当前修复点在 `GlueTableOperations` 的 commit 路径，此处 `glueTable` 已在作用域内，只需在 builder 链上多加一行 `.description(...)` 即可，改动最小且意图清晰。

### 与 #9530 的关系

本提交的测试部分依赖先前提交 #9530（commit 83408f888，"AWS: Support setting description for Glue table"）：
- #9530 将常量 `GLUE_DB_DESCRIPTION_KEY` 重命名为 `GLUE_DESCRIPTION_KEY`，并在 `setTableInputInformation` 中新增了"从 Iceberg 表属性 `comment` 读取并设置 description 到 TableInput"的逻辑。
- 本提交（#10199）的集成测试 `TestGlueCatalogTable` 引用了 `IcebergToGlueConverter.GLUE_DESCRIPTION_KEY` 来设置表属性并验证 description 更新。若 #9530 未回迁，该常量不存在，测试无法编译。
- 本提交的**生产代码修复**（`GlueTableOperations` 的 `.description(glueTable.description())`）**不依赖 #9530**——即使没有 #9530 的 `setTableInputInformation` description 逻辑，保留旧 description 的修复本身仍有效（只是无法通过 Iceberg 属性覆盖 description）。

### 测试验证策略

测试通过两条路径验证：

1. **description 不被清空**：先用 Glue API 直接设置表 description（绕过 Iceberg），再触发 Iceberg 表 refresh + commit（refresh 不改表，但验证 schema 同步），最后检查 Glue 表 description 仍为原值。
2. **description 可被 Iceberg 属性覆盖**：通过 `table.updateProperties().set(GLUE_DESCRIPTION_KEY, "test updated comment").commit()` 更新表属性，commit 后检查 Glue 表 description 已变为新值。

两条路径共同证明"保留 + 覆盖"逻辑正确工作。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/glue/GlueTableOperations.java`

**修改目的**：在 commit 时保留 Glue 表已有 description，再由 applyMutation 决定是否覆盖。

**工作逻辑**：
- 在 `TableInput.builder()` 链上，`.tableInput(TableInput.builder()...)` 内部新增 `.description(glueTable.description())`，置于 `.applyMutation(...)` 之前。
- 新增 3 行（含 2 行注释说明顺序意图）。
- `glueTable` 是 commit 前通过 `getGlueTable()` 获取的当前 Glue 表对象，其 `description()` 方法返回 Glue 表的 description 字段（可能为 null）。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/GlueTestBase.java`

**修改目的**：提供测试辅助方法，绕过 Iceberg 直接通过 Glue API 设置表 description。

**工作逻辑**：
- 新增 import：`GetTableRequest`、`GetTableResponse`、`Table`、`TableInput`、`UpdateTableRequest`（均为 Glue SDK 模型类）。
- 新增 public static 方法 `updateTableDescription(String namespace, String tableName, String description)`，共 25 行：
  - 先 `glue.getTable(...)` 获取当前 Glue 表对象。
  - 构造 `UpdateTableRequest`，`TableInput` 中完整复制原表信息（catalogId、databaseName、name、partitionKeys、tableType、owner、parameters、storageDescriptor），仅将 `description` 替换为指定值。
  - 调用 `glue.updateTable(request)` 写回。
- 这是直接调用 Glue API 而非走 Iceberg，模拟"用户通过非 Iceberg 渠道设置 description"的场景。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogTable.java`

**修改目的**：验证 description 保留与覆盖两条路径。

**工作逻辑**：
- 新增 import（GetTableRequest）。
- 在已有测试方法中（验证 createTable + refresh 的流程）新增：
  - `String description = "test description"; updateTableDescription(namespace, tableName, description);` —— 通过 Glue API 设置 description。
  - refresh 后断言 Glue 表 description 仍为 `description`（未被清空）。
  - `table.updateProperties().set(IcebergToGlueConverter.GLUE_DESCRIPTION_KEY, "test updated comment").commit();` —— 通过 Iceberg 更新表属性。
  - 再次 `glue.getTable(...)` 并断言 description 已变为 `"test updated comment"`（被 Iceberg 属性覆盖）。
- 新增 13 行。

## 小结

- **成效**：修复了 Iceberg 通过 `GlueTableOperations` commit 表更新时清空 Glue 表 description 的缺陷。修复采用"先保留 Glue 原值、再由 applyMutation 按需覆盖"的最小化策略，既保护了用户通过非 Iceberg 渠道设置的 description，又不影响 Iceberg 通过表属性管理 description 的能力。集成测试覆盖保留与覆盖两条路径。
- **影响范围**：影响 AWS Glue Catalog 集成的表 commit 路径（`GlueTableOperations`）。所有使用 Iceberg GlueCatalog 且表 commit 频繁的场景均受益——此前每次 commit 都会丢失 description，修复后 description 得以保留。仅影响 AWS 模块，不涉及其他 catalog 实现（HiveCatalog、JDBCCatalog 等不受影响）。
- **回迁注意事项**：
  1. **生产代码修复（`GlueTableOperations.java`）可独立回迁**，不依赖其他提交。`.description(glueTable.description())` 一行修复在任何具备 `glueTable` 引用的版本都能工作。cherry-pick 到 1.4.x 分支冲突风险低。
  2. **集成测试依赖 #9530（commit 83408f888）**：测试中引用的 `IcebergToGlueConverter.GLUE_DESCRIPTION_KEY` 常量由 #9530 引入（将旧名 `GLUE_DB_DESCRIPTION_KEY` 重命名）。若 1.4.x 分支未回迁 #9530，则：
     - 需先将测试中的 `GLUE_DESCRIPTION_KEY` 改为 `GLUE_DB_DESCRIPTION_KEY`（1.4.x 既有常量名），或
     - 一并回迁 #9530 后再回迁本提交的测试部分。
     - 经验证，当前 1.4.x 基线中 `IcebergToGlueConverter` 仅有 `GLUE_DB_DESCRIPTION_KEY`（line 89），`GLUE_DESCRIPTION_KEY` 不存在，且 #9530（83408f888）不是当前 HEAD 的祖先，确认 1.4.x 缺少 #9530。
  3. **生产代码修复在无 #9530 时仍有效但语义略有不同**：若无 #9530，`setTableInputInformation` 不会从 Iceberg 表属性读取 comment 设置 description，因此本修复的"保留"行为生效（description 不被清空），但"通过 Iceberg 表属性覆盖 description"的能力不存在。若 1.4.x 用户需要通过 Iceberg 管理描述，应一并回迁 #9530。
  4. 集成测试（`TestGlueCatalogTable`、`GlueTestBase`）位于 `aws/src/integration/java/`，需要真实 AWS Glue 环境才能运行（integration test），回迁后默认不执行，仅在 CI 集成测试环境触发。
  5. `updateTableDescription` 辅助方法完整复制原表字段（partitionKeys、storageDescriptor 等）以构造 TableInput，这是 Glue `UpdateTableRequest` 整体替换语义的要求；若 1.4.x 的 Glue SDK 版本字段有差异（如 additionalLocations），需相应调整。
