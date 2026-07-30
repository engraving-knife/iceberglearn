# 提交 1077：Core: Support case-insensitivity for column names in PartitionSpec (#10678)

## 提交信息

- **序号**：1077 / 4088
- **哈希**：40d5204fb7282df7a7910dfd3ff9ac785554955c
- **短哈希**：40d5204fb
- **日期**：2024-08-20 10:57:38 -0700
- **作者**：Steve Lessard <steve.lessard@teradata.com>
- **提交说明**：Core: Support case-insensitivity for column names in PartitionSpec (#10678)
- **PR/Issue**：#10678

## 总体目的

Iceberg 的 schema 本身是大小写敏感的：允许同时存在 `data` 和 `DATA` 两个不同的列。但许多上游引擎（如 Spark、Flink、Trino）在用户 SQL 中对列名做了大小写折叠处理（要么全部小写、要么大小写不敏感匹配），导致用户用 `DATA` 引用一个实际名为 `data` 的列时，Iceberg 的 `PartitionSpec.builderFor(schema).identity("DATA")` 会因 `schema.findField("DATA")` 找不到列而抛出 `Cannot find source column: DATA`。

此前 `PartitionSpec.Builder` 的所有 transform 方法（identity/year/month/day/hour/bucket/truncate/alwaysNull）都直接调用 `schema.findField(sourceName)` 做精确大小写匹配，不支持大小写不敏感查找。这与 `Schema.caseInsensitiveFindField` 已存在的能力不匹配，导致用户在 case-insensitive 引擎里写分区 spec 时体验割裂、易踩坑。

本提交的目的是为 `PartitionSpec.Builder` 增加一个 `caseSensitive(boolean)` 开关，开启大小写不敏感模式后：

1. 用 `schema.caseInsensitiveFindField` 替代 `schema.findField` 查找源列；
2. 自动生成的 target name（如 `data_bucket`、`order_date_year`）使用 schema 中**真实的列名**（canonical name）作为后缀基础，而不是用户传入的 sourceName（可能大小写不符），保证 spec 元数据中字段名稳定；
3. 同时强化 `TypeUtil.indexByLowerCaseName`，使其在 schema 中存在两个"小写后碰撞"的列名时显式报错而非静默覆盖，给用户清晰错误反馈，避免大小写不敏感查找时返回错误列。

## 如何达成设计目的

设计思路：

1. **Builder 增加 `caseSensitive` 标志**：默认 `true` 保持向后兼容；调用 `caseSensitive(false)` 后切换到大小写不敏感模式。
2. **解耦"按名查找列"与"构造分区字段"**：把每个 transform 方法（identity/year/month/day/hour/bucket/truncate/alwaysNull）拆成两个重载：
   - 公共方法接收 `String sourceName`：先调用 `findSourceColumn(sourceName)` 解析出 `Types.NestedField`，再委托给私有方法；
   - 私有方法接收 `Types.NestedField sourceColumn`：直接基于列对象构造 `PartitionField`。
   - 这样拆分的好处是：单参数重载（自动生成 target name）可以在解析出列后，用 `schema.findColumnName(sourceColumn.fieldId())` 拿到 schema 中真实的列名，再拼接后缀（如 `_year`），避免使用用户传入的 sourceName 作为后缀基础（在 case-insensitive 模式下用户传 `DATA` 时，真实列名是 `data`，target name 应为 `data_year` 而非 `DATA_year`）。
3. **`findSourceColumn` 与 `checkAndAddPartitionName` 同时尊重 `caseSensitive`**：根据标志位选择 `schema.findField` 或 `schema.caseInsensitiveFindField`。
4. **`TypeUtil.indexByLowerCaseName` 增加碰撞检测**：原实现 `put(name.toLowerCase, id)` 在同名碰撞时静默覆盖，改为显式检查 `existingId == null || existingId.equals(fieldId)`，否则抛 `IllegalArgumentException`，并在错误消息中同时给出两个冲突字段的真实名（通过 `byId.get(existingId)` 和 `byId.get(fieldId)` 反查），便于用户定位问题。
5. **配套测试**：新增两个测试类（`TestSchemaCaseSensitivity`、`TestPartitionSpecBuilderCaseSensitivity`，后者 873 行），对每个 transform 类型在 case-sensitive/case-insensitive 下的 source name 查找、target name 默认值生成、source/target 重名冲突规则做了穷尽式覆盖；并在 `TestPartitionSpecInfo`、`TestPartitioning` 中补充 case-insensitive 端到端用例。

## 修改详情

### `api/src/main/java/org/apache/iceberg/PartitionSpec.java`

**修改目的**：为 `PartitionSpec.Builder` 增加大小写不敏感查找源列的能力，并修正单参数重载中 target name 默认值的生成逻辑，使其基于 schema 真实列名而非用户传入名。

**工作逻辑**：

1. **新增 `caseSensitive` 字段与 setter**：

```diff
     // check if there are conflicts between partition and schema field name
     private boolean checkConflicts = true;
+    private boolean caseSensitive = true;
```

```java
     public Builder caseSensitive(boolean sensitive) {
       this.caseSensitive = sensitive;
       return this;
     }
```

默认 `true` 保持向后兼容。

2. **`checkAndAddPartitionName` 与 `findSourceColumn` 按标志位选择查找方法**：

```diff
     private void checkAndAddPartitionName(String name, Integer sourceColumnId) {
-      Types.NestedField schemaField = schema.findField(name);
+      Types.NestedField schemaField =
+          this.caseSensitive ? schema.findField(name) : schema.caseInsensitiveFindField(name);
```

```diff
     private Types.NestedField findSourceColumn(String sourceName) {
-      Types.NestedField sourceColumn = schema.findField(sourceName);
+      Types.NestedField sourceColumn =
+          this.caseSensitive
+              ? schema.findField(sourceName)
+              : schema.caseInsensitiveFindField(sourceName);
       Preconditions.checkArgument(
           sourceColumn != null, "Cannot find source column: %s", sourceName);
       return sourceColumn;
     }
```

3. **每个 transform 方法拆为公共+私有重载对**。以 `identity` 为例：

```diff
     Builder identity(String sourceName, String targetName) {
-      Types.NestedField sourceColumn = findSourceColumn(sourceName);
+      return identity(findSourceColumn(sourceName), targetName);
+    }
+
+    private Builder identity(Types.NestedField sourceColumn, String targetName) {
       checkAndAddPartitionName(targetName, sourceColumn.fieldId());
       ...
     }

     public Builder identity(String sourceName) {
-      return identity(sourceName, sourceName);
+      Types.NestedField sourceColumn = findSourceColumn(sourceName);
+      return identity(sourceColumn, schema.findColumnName(sourceColumn.fieldId()));
     }
```

关键点：原 `identity(String)` 直接用 `sourceName` 作为 target name；新实现改为用 `schema.findColumnName(sourceColumn.fieldId())` 取 schema 真实列名作为 target name。这样在 case-insensitive 模式下，用户传 `identity("DATA")` 时，若 schema 中真实列名为 `data`，target name 会被规范化为 `data`，避免 spec 中保留用户误传的大写形式。

4. **同样的拆分模式应用到 `year`/`month`/`day`/`hour`/`bucket`/`truncate`/`alwaysNull`**。每个方法的单参数重载中，target name 后缀基于 `schema.findColumnName(sourceColumn.fieldId())` 拼接，例如：

```java
     public Builder year(String sourceName) {
       Types.NestedField sourceColumn = findSourceColumn(sourceName);
       String columnName = schema.findColumnName(sourceColumn.fieldId());
       return year(sourceColumn, columnName + "_year");
     }
```

`bucket`/`truncate` 的单参数重载同理：`columnName + "_bucket"`、`columnName + "_trunc"`。`alwaysNull` 单参数重载：`columnName + "_null"`。

这一改造对 case-sensitive 模式也是行为改进：原先 `year("order_date")` 会生成 target name `order_date_year`（与 schema 列名一致，无差异），新实现同样生成 `order_date_year`，行为一致；但 case-insensitive 模式下 `year("ORDER_DATE")` 现在会生成 `order_date_year`（schema 真实名），而非原来的 `ORDER_DATE_year`，使 spec 元数据列名稳定可预测。

### `api/src/main/java/org/apache/iceberg/types/TypeUtil.java`

**修改目的**：强化 `indexByLowerCaseName`，在两个列名小写后碰撞时显式抛错，避免大小写不敏感查找返回错误列。

**工作逻辑**：

```diff
+  /**
+   * Creates a mapping from lower-case field names to their corresponding field IDs.
+   *
+   * <p>This method iterates over the fields of the provided struct and maps each field's name
+   * (converted to lower-case) to its ID. If two fields have the same lower-case name, an
+   * `IllegalArgumentException` is thrown.
+   ...
+   */
   public static Map<String, Integer> indexByLowerCaseName(Types.StructType struct) {
     Map<String, Integer> indexByLowerCaseName = Maps.newHashMap();
+
+    IndexByName indexer = new IndexByName();
+    visit(struct, indexer);
+    Map<String, Integer> byName = indexer.byName();
+    Map<Integer, String> byId = indexer.byId();
+
     indexByName(struct)
         .forEach(
-            (name, integer) -> indexByLowerCaseName.put(name.toLowerCase(Locale.ROOT), integer));
+            (name, fieldId) -> {
+              String key = name.toLowerCase(Locale.ROOT);
+              Integer existingId = indexByLowerCaseName.put(key, fieldId);
+              Preconditions.checkArgument(
+                  existingId == null || existingId.equals(fieldId),
+                  "Cannot build lower case index: %s and %s collide",
+                  byId.get(existingId),
+                  byId.get(fieldId));
+              indexByLowerCaseName.put(key, fieldId);
+            });
     return indexByLowerCaseName;
   }
```

- 原实现：`forEach` 中直接 `put(name.toLowerCase, integer)`，遇到同名 key 会被新值覆盖，行为静默且错误。
- 新实现：先 `put(key, fieldId)` 拿到 `existingId`（此前该 key 对应的值，若为 null 表示首次出现）；若 `existingId` 非 null 且与当前 `fieldId` 不同，说明两个不同列的小写名碰撞，抛 `IllegalArgumentException`。错误消息通过 `byId` 反查两个冲突列的真实名字（如 `data` 和 `DATA`）一起打印，便于用户定位。
- 注意：`Map.put` 返回前一个值，所以 `existingId` 是被覆盖前的值；抛错后再次 `put(key, fieldId)` 是冗余的（put 已经发生），可视为防御性写法。
- 新增 Javadoc 明确说明碰撞会抛 `IllegalArgumentException`。

### `api/src/test/java/org/apache/iceberg/TestSchemaCaseSensitivity.java`（新增）

**修改目的**：为 `Schema.caseInsensitiveFindField` 与 `TypeUtil.indexByLowerCaseName` 的碰撞检测添加单元测试。

**工作逻辑**：3 个测试用例：
- `testCaseInsensitiveFieldCollision`：schema 含 `data` 和 `DATA` 两列，调用 `caseInsensitiveFindField("DATA")` 应抛 `IllegalArgumentException`，消息包含 `Cannot build lower case index: data and DATA collide`。
- `testCaseSensitiveFindField`：精确大小写查找应当区分 `data` 和 `DATA`，分别返回 fieldId=2 和 fieldId=3。
- `testCaseInsensitiveField`：schema 仅含 `data` 一列时，`caseInsensitiveFindField("DATA")` 应正确返回 `data` 列。

### `core/src/test/java/org/apache/iceberg/TestPartitionSpecBuilderCaseSensitivity.java`（新增，873 行）

**修改目的**：对 `PartitionSpec.Builder` 的所有 transform 方法在 case-sensitive/case-insensitive 两种模式下的行为做穷尽式覆盖测试。

**工作逻辑**：定义两份 schema：
- `SCHEMA_WITHOUT_NAME_CONFLICTS`：含 `id/data/category/order_date/order_time/ship_date/ship_time`，所有列名小写无冲突，适合 case-insensitive 模式测试。
- `SCHEMA_WITH_NAME_CONFLICTS`：含 `data` 和 `DATA`、`order_date` 和 `ORDER_DATE`、`order_time` 和 `ORDER_TIME` 等大小写冲突对，适合 case-sensitive 模式区分测试。

对每个 transform 类型（identity/year/month/day/hour/bucket/truncate/alwaysNull）测试以下维度：
- **target name 默认值**：case-sensitive 模式下用真实列名拼接后缀；case-insensitive 模式下用户传大写也能得到小写后缀（如 `DATA` → `data_bucket`）。
- **source name 重复规则**：case-sensitive 下 `data` 和 `DATA` 是不同列，可分别作为 source；case-insensitive 下两者解析到同一列，再加一次会触发"redundant partition"错误。
- **target name 重复规则**：case-sensitive 下 `partition1` 和 `PARTITION1` 是不同 partition name，允许并存；case-insensitive 下不允许"不同 source、相同 target name（精确匹配）"——注意 case-insensitive 模式下 target name 仍是精确大小写匹配去重（不折叠），所以 `partition1` 和 `PARTITION1` 仍可并存，但两个 `partition1` 不允许。这一行为通过 `Cannot use partition name more than once: partition1` 错误消息验证。

### `core/src/test/java/org/apache/iceberg/TestPartitionSpecInfo.java`

**修改目的**：补充 case-insensitive 模式下创建分区表的端到端测试。

**工作逻辑**：新增两个 `@TestTemplate`：
- `testSpecInfoPartitionedTableCaseInsensitive`：用 `caseSensitive(false).identity("DATA")` 建 spec（schema 中列名为 `data`），创建表后验证 `table.spec()` 等于原 spec、`lastAssignedFieldId` 一致、`table.specs()` 包含且仅包含该 spec。
- `testSpecInfoPartitionedTableCaseSensitiveFails`：在 case-sensitive 模式下用 `identity("DATA")`（schema 中只有 `data`）应抛 `Cannot find source column: DATA`。

### `core/src/test/java/org/apache/iceberg/TestPartitioning.java`

**修改目的**：补充 case-insensitive 模式下分区 spec 演化（rename、addField）的端到端测试。

**工作逻辑**：新增两个 `@Test`：
- `testPartitionTypeWithRenamesInV1TableCaseInsensitive`：用 `caseSensitive(false).identity("DATA", "p1")` 建初始 spec，然后 `addField("category")`、`renameField("p1", "p2")`，验证最终 `Partitioning.partitionType(table)` 等于预期的 `StructType`（含 `p2` 和 `category` 两个字段）。
- `testGroupingKeyTypeWithRenamesInV1TableCaseInsensitive`：同样场景下验证 `Partitioning.groupingKeyType(table.schema(), table.specs().values())` 等于预期（仅含 `p2`）。

## 小结

- **成效**：成功为 `PartitionSpec.Builder` 增加了大小写不敏感的源列查找能力（`caseSensitive(false)` 开关），并修正了单参数 transform 重载中 target name 默认值的生成逻辑（基于 schema 真实列名而非用户传入名），同时强化了 `TypeUtil.indexByLowerCaseName` 在列名小写碰撞时的错误反馈。使 Iceberg 在与 Spark/Flink/Trino 等大小写不敏感引擎集成时，用户能用任意大小写引用列名构造分区 spec，体验与引擎侧一致。配套 938 行新增测试覆盖了所有 transform 类型在两种模式下的边界行为。
- **影响范围**：4 个核心文件改动 + 2 个新增测试类，跨 `api` 和 `core` 两个模块。改动文件：`PartitionSpec.java`（api）、`TypeUtil.java`（api）、`TestSchemaCaseSensitivity.java`（api 新增）、`TestPartitionSpecBuilderCaseSensitivity.java`（core 新增）、`TestPartitionSpecInfo.java`（core）、`TestPartitioning.java`（core）。
- **回迁到 1.4.x 的注意事项**：本提交是新功能增强，**可考虑回迁到 1.4.x**，但需注意几点风险：(1) **行为变更**：即使是 case-sensitive 模式（默认），单参数 transform 重载的 target name 生成逻辑也变了——原来用 `sourceName`，现在用 `schema.findColumnName(fieldId)`。在绝大多数场景下两者一致（用户传入名与 schema 名一致），但若用户曾依赖"传入与 schema 不同大小写的 sourceName 来生成特定大小写 target name"这种边缘行为，回迁后会改变行为。建议在 1.4.x 上扫描是否有依赖此旧行为的代码或测试。(2) **依赖前置**：本提交依赖 `Schema.caseInsensitiveFindField` 方法已存在（应在 1.4.x 已有，需确认）。(3) `TypeUtil.indexByLowerCaseName` 的碰撞检测是行为收紧——原本静默覆盖现在会抛错，若有 1.4.x 下游代码依赖静默覆盖的"最后一个生效"行为，回迁后会失败。建议先在 1.4.x 上跑全量测试验证。(4) 这是一个较大的功能改动（1093 行新增），cherry-pick 时需连同所有相关文件一起回迁，并确保 `Schema.caseInsensitiveFindField` 在 1.4.x 上签名一致。若 1.4.x 已接近 EOL 或不计划与 case-insensitive 引擎深度集成，可暂不回迁。
