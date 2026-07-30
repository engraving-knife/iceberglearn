# 提交 0900：AWS: Retain Glue Catalog column comment (#10276)

## 提交信息

- **序号**：0900 / 4088
- **哈希**：9a0fc49481c232bf0eb149fa189d875cded6a231
- **短哈希**：9a0fc4948
- **日期**：2024-07-05（Fri Jul 5 15:46:24 2024 +0900）
- **作者**：Sotaro Hikita <70102274+lawofcycles@users.noreply.github.com>
- **提交说明**：AWS: Retain Glue Catalog column comment (#10276)
- **PR/Issue**：#10276

## 总体目的

Iceberg 在 AWS Glue Catalog 上持久化表元数据时，会把 Iceberg 的 schema、分区 spec、location 等信息转换成 Glue 的 `TableInput`（包括 `StorageDescriptor` 下的 `columns`），写入 Glue。这一转换是"尽力而为"（best-effort）的展示用途——真正的 source of truth 是 Iceberg 的 metadata 文件，Glue 中的信息仅供人类通过 Glue UI/CLI 查看。

问题在于：当用户通过 Glue API（或其他工具）直接为 Glue 表的列设置了 comment（注释），而 Iceberg schema 中对应字段没有设置 `doc()` 时，Iceberg 在下一次 commit（如 schema 变更、partition 变更等）更新 Glue 表时，会用 `field.doc()`（即 null）覆盖掉 Glue 中已有的 column comment，导致用户在 Glue 中设置的注释丢失。类似地，表级别的 description 也有被覆盖的风险（原代码用一个脆弱的"先设 description 再 applyMutation 让后者覆盖"的 workaround 来处理，但不够优雅）。

本提交的目的是：在 Iceberg 向 Glue 写入 `TableInput` 时，如果存在已有的 Glue 表，则保留该表中已有的列注释与表描述；只有当 Iceberg schema 的字段显式设置了 `doc()` 时才覆盖注释，否则沿用 Glue 中已有的注释。

## 如何达成设计目的

设计思路是给 `IcebergToGlueConverter.setTableInputInformation` 方法增加一个可选的 `existingTable` 参数（类型为 Glue `Table`），在构建 columns 时：

1. 先从 `existingTable.storageDescriptor().columns()` 中提取出"列名 → 注释"的映射 `existingColumnMap`；
2. 在 `addColumnWithDedupe` 中构建每个 `Column` 时，如果 Iceberg 字段的 `doc()` 非空则用它作为 comment，否则查 `existingColumnMap` 看是否有对应列名的注释，有则沿用；
3. 对表级别 description 同样处理：如果 Iceberg properties 中有 `glue.description` 则用它，否则若 `existingTable` 非空则沿用其 description。

同时更新 `GlueTableOperations` 在 persist 时把已有的 `glueTable` 传给新的重载方法，移除原来"先设 description 再让 applyMutation 覆盖"的脆弱 workaround。

保留原有两个参数的 `setTableInputInformation(builder, metadata)` 重载（内部调用三参数版并传 null），向后兼容。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/glue/IcebergToGlueConverter.java`

**修改目的**：扩展 `setTableInputInformation` 以支持从已有 Glue 表保留列注释与表描述。

**工作逻辑**：

1. 新增 import：`java.util.Collections`、`software.amazon.awssdk.services.glue.model.Table`。

2. 将原 `setTableInputInformation(TableInput.Builder, TableMetadata)` 改为委托给新的三参数重载：

```java
static void setTableInputInformation(
    TableInput.Builder tableInputBuilder, TableMetadata metadata) {
  setTableInputInformation(tableInputBuilder, metadata, null);
}
```

3. 新增三参数重载 `setTableInputInformation(builder, metadata, existingTable)`，并在其中：
   - **description 处理**：原来直接 `Optional.ofNullable(properties.get(GLUE_DESCRIPTION_KEY)).ifPresent(tableInputBuilder::description)`，改为先取 `description`，若非 null 则设；否则若 `existingTable != null`，沿用 `existingTable.description()`：
     ```java
     String description = properties.get(GLUE_DESCRIPTION_KEY);
     if (description != null) {
       tableInputBuilder.description(description);
     } else if (existingTable != null) {
       Optional.ofNullable(existingTable.description()).ifPresent(tableInputBuilder::description);
     }
     ```
   - **column comment 处理**：从 `existingTable` 的 `storageDescriptor().columns()` 中提取 `name → comment` 映射（只保留 comment 非空的列），然后传给 `toColumns(metadata, existingColumnMap)`：
     ```java
     Map<String, String> existingColumnMap = null;
     if (existingTable != null) {
       List<Column> existingColumns = existingTable.storageDescriptor().columns();
       existingColumnMap =
           existingColumns.stream()
               .filter(column -> column.comment() != null)
               .collect(Collectors.toMap(Column::name, Column::comment));
     } else {
       existingColumnMap = Collections.emptyMap();
     }
     List<Column> columns = toColumns(metadata, existingColumnMap);
     ```

4. `toColumns(TableMetadata)` 改为 `toColumns(TableMetadata, Map<String, String> existingColumnMap)`，把 `existingColumnMap` 透传给 `addColumnWithDedupe`。

5. `addColumnWithDedupe` 增加 `existingColumnMap` 参数。原来直接 `.comment(field.doc())` 无条件设置（即使 doc 为 null 也会覆盖），改为条件式设置：

```java
Column.Builder builder =
    Column.builder()
        .name(field.name())
        .type(toTypeString(field.type()))
        .parameters(...);

if (field.doc() != null && !field.doc().isEmpty()) {
  builder.comment(field.doc());
} else if (existingColumnMap != null && existingColumnMap.containsKey(field.name())) {
  builder.comment(existingColumnMap.get(field.name()));
}

columns.add(builder.build());
```

这样只有 Iceberg 显式设置了 doc 才覆盖，否则沿用 Glue 已有注释。

### `aws/src/main/java/org/apache/iceberg/aws/glue/GlueTableOperations.java`

**修改目的**：在 persist Glue 表时传入已有的 `glueTable`，并移除原来的 description workaround。

**修改逻辑**：原来在 `persist` 方法中构建 `TableInput` 时：

```java
.tableInput(
    TableInput.builder()
        // Call description before applyMutation so that applyMutation overwrites the
        // description with the comment specified in the query
        .description(glueTable.description())
        .applyMutation(
            builder ->
                IcebergToGlueConverter.setTableInputInformation(builder, metadata))
        ...)
```

修改后：

```java
.tableInput(
    TableInput.builder()
        .applyMutation(
            builder ->
                IcebergToGlueConverter.setTableInputInformation(
                    builder, metadata, glueTable))
        ...)
```

移除了 `.description(glueTable.description())` 这一行及其注释说明，改为把 `glueTable` 传给 `setTableInputInformation`，由后者统一处理 description 与 column comment 的保留逻辑。

### `aws/src/test/java/org/apache/iceberg/aws/glue/TestIcebergToGlueConverter.java`

**修改目的**：新增单元测试 `testSetTableInputInformationWithExistingTable`，验证列注释保留逻辑。

**工作逻辑**：构造一个 Iceberg schema（3 个字段 x/y/z，其中 y、z 有 doc "new comment"，x 无 doc），构造一个已有 Glue 表（x、y 有 comment "existing comment"），调用三参数 `setTableInputInformation`，断言：
- x：无 Iceberg doc → 沿用 Glue 的 "existing comment"；
- y：有 Iceberg doc "new comment" → 用 Iceberg doc 覆盖；
- z：有 Iceberg doc "new comment" → 用 Iceberg doc（Glue 表中无 z 的注释）。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/GlueTestBase.java`

**修改目的**：新增辅助方法 `updateTableColumns`，用于在集成测试中通过 Glue API 直接更新 Glue 表的列。

**工作逻辑**：`updateTableColumns(namespace, tableName, columnUpdater)` 先用 `glue.getTable` 获取已有 Glue 表，对其 `storageDescriptor().columns()` 应用传入的 `Function<Column, Column>` 更新器（如给某列加 comment），再用 `glue.updateTable` 写回。这样集成测试可以模拟"用户通过 Glue API 设置列注释"的场景。

### `aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogTable.java`

**修改目的**：新增两个集成测试，验证端到端的列注释保留行为。

**工作逻辑**：

1. `testDropColumn`：创建表 → 通过 Iceberg API 加列 c2（带 doc）、c3（不带 doc） → 通过 Glue API 给 c3 加 comment → 通过 Iceberg API 删除 c2、c3 → 断言 Glue 中 c2、c3 仍作为历史列保留（`ICEBERG_FIELD_CURRENT=false`），且 c3 的 comment "updated from Glue API" 被保留（因为 Iceberg 删了列后该列不再有 doc，应沿用 Glue 注释）。

2. `testGlueTableColumnCommentsPreserved`：创建表 → 通过 Iceberg API 加列 c2、c3（都不带 doc） → 通过 Glue API 给 c2、c3 加 comment "updated from Glue API" → 通过 Iceberg API 更新 c2 的 doc 为 "updated from Iceberg API" → 断言：
   - c1：保留原 doc "c1"；
   - c2：Iceberg 更新了 doc → 用 Iceberg doc "updated from Iceberg API"；
   - c3：Iceberg 未设 doc → 沿用 Glue 的 "updated from Glue API"。

## 小结

- **成效**：修复了 Iceberg 更新 Glue 表时丢失用户在 Glue 中设置的列注释与表描述的问题。新逻辑是"Iceberg doc 优先，否则沿用 Glue 已有注释"，既支持通过 Iceberg API 管理注释，也保留通过 Glue API 设置的注释。同时移除了原来脆弱的 description 处理 workaround。
- **影响范围**：5 个文件，310 行新增、15 行删除。主代码改动集中在 `IcebergToGlueConverter`（方法重载与 column 构建逻辑）与 `GlueTableOperations`（传参）；测试包括单元测试 1 个、集成测试 2 个、测试辅助方法 1 个。无 API 破坏性变更（保留原两参数重载）。
- **回迁到 1.4.x 的注意事项**：该提交是 bug fix（列注释丢失），**建议回迁到 1.4.x**。理由：
  1. 1.4.x 的 `IcebergToGlueConverter` 与 `GlueTableOperations` 应有同样的列注释丢失问题（`.comment(field.doc())` 无条件覆盖），用户在 Glue 中设置的注释会被 Iceberg commit 清除；
  2. 改动向后兼容（保留原两参数重载），不会破坏已有调用方；
  3. 回迁时需注意 1.4.x 中 `IcebergToGlueConverter` 的方法签名与 main 是否一致（`setTableInputInformation`、`toColumns`、`addColumnWithDedupe` 的参数列表），若 1.4.x 已有自定义改动需手动合并；
  4. 集成测试 `TestGlueCatalogTable` 需要 AWS 环境才能运行，回迁后可能无法在 CI 中验证，但单元测试 `TestIcebergToGlueConverter.testSetTableInputInformationWithExistingTable` 不需要 AWS 环境即可验证核心逻辑；
  5. 该修复涉及用户可感知的行为变化（列注释不再丢失），对 1.4.x 用户有实际价值，建议优先回迁。
