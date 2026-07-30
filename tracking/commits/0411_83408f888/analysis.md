# 提交 0411：AWS: Support setting description for Glue table (#9530)

## 提交信息

- **序号**：0411
- **哈希**：83408f88830b6e9276095dd8ff6606d6a8037946
- **短哈希**：83408f888
- **日期**：Fri Jan 26 02:45:40 2024 +0200
- **作者**：Levani Kokhreidze <levani.codes@gmail.com>
- **提交说明**：AWS: Support setting description for Glue table (#9530)
- **PR/Issue**：#9530

## 总体目的

这个提交的核心目标是扩展 Iceberg 的 AWS Glue Catalog，使其支持在 Glue 表（table）级别设置 description（描述），而不仅仅是数据库（namespace/database）级别。在 AWS Glue 数据目录中，description 是一个一等公民字段（一等表元数据），它独立于普通的 `parameters` map 而存在，常被 Athena、Glue、EMR 等下游服务以及数据目录 UI 直接展示，是数据治理、可发现性、文档化的关键载体。

在此次改动之前，Iceberg 的 `IcebergToGlueConverter` 只在数据库层面将 `comment` 属性映射到 `DatabaseInput.description`，而创建 `TableInput` 时完全没有把任何属性映射到 Glue 表的 `description` 字段。这意味着：即使用户在表属性里设置了 `comment=...`，Glue 表对象本身的 `description` 始终为空，下游工具无法看到该描述，造成元数据丢失。这违背了 Iceberg 作为"开放表格式"应当最大化兼容底层目录能力的理念。

为了实现这一目标，作者采取了几个相关联的设计动作：第一，把原本只用于数据库级别的常量 `GLUE_DB_DESCRIPTION_KEY` 重命名为更通用的 `GLUE_DESCRIPTION_KEY`，使其语义既能覆盖数据库也能覆盖表；第二，在 `setTableInputInformation` 方法中读取表的 `comment` 属性，并通过 `TableInput.Builder#description` 写入 Glue 表元数据；第三，更新 `GlueCatalog` 中读取数据库属性的代码以使用新常量名；第四，同步更新所有相关单元测试和集成测试。

上下游影响方面，由于 `GLUE_DB_DESCRIPTION_KEY` 是 `public static final` 常量，原本被多处测试引用。重命名理论上对下游外部使用者属于源码不兼容变更（binary compat 仍可，因为是 `static final` String 字段，常量内联到调用方的字节码里），但对仓库内部代码而言只需一次性替换。这种重命名体现了"用更准确的命名表达更宽泛的语义"的代码演进模式。

## 如何达成设计目的

实现路径比较直接：复用已有的 `comment` 属性键作为单一来源（single source of truth），在两个不同的转换点（数据库层、表层）分别将其映射到对应的 Glue 元数据字段。这样用户只需在 Iceberg 表属性中设置一次 `comment`，即可同时影响 Iceberg 元数据、Glue 数据库描述和 Glue 表描述，符合"DRY"和"约定优于配置"的原则。核心设计思路是不引入新的属性键，而是扩展现有键的应用范围。

## 修改详情

### aws/src/main/java/org/apache/iceberg/aws/glue/IcebergToGlueConverter.java

**修改目的**：核心转换逻辑所在文件，需要在此完成常量重命名以及表级别 description 的写入。

**工作逻辑**：
1. 新增 `import java.util.Optional`，用于安全地从 properties 中取值。
2. 把常量 `GLUE_DB_DESCRIPTION_KEY = "comment"` 重命名为 `GLUE_DESCRIPTION_KEY = "comment"`，并在注释中明确说明该键同时用于数据库和表级别的描述定义（"Utilized for defining descriptions at both the Glue database and table levels"）。
3. 在 `toDatabaseInput` 方法中把对 `GLUE_DB_DESCRIPTION_KEY` 的判断改为 `GLUE_DESCRIPTION_KEY`，行为不变。
4. 在 `setTableInputInformation` 方法（构建 `TableInput` 时调用）中：先把 `metadata.properties()` 缓存到局部变量 `properties`，避免重复调用；然后新增一行 `Optional.ofNullable(properties.get(GLUE_DESCRIPTION_KEY)).ifPresent(tableInputBuilder::description);`，当表属性中存在 `comment` 时将其设为 `TableInput.description`。这一行是本次功能性变更的核心——它将 Iceberg 表属性桥接到 Glue 原生表描述字段。

### aws/src/main/java/org/apache/iceberg/aws/glue/GlueCatalog.java

**修改目的**：在加载 namespace 属性时使用重命名后的常量。

**工作逻辑**：`loadNamespaceMetadata` 方法中原本 `result.put(IcebergToGlueConverter.GLUE_DB_DESCRIPTION_KEY, database.description())` 改为 `GLUE_DESCRIPTION_KEY`，仅是引用名变更，运行时行为完全一致。

### aws/src/test/java/org/apache/iceberg/aws/glue/TestIcebergToGlueConverter.java

**修改目的**：单元测试，验证新行为并适配常量重命名。

**工作逻辑**：
1. `testToDatabaseInput` 和 `testToDatabaseInputEmptyLocation` 中将常量引用从 `GLUE_DB_DESCRIPTION_KEY` 改为 `GLUE_DESCRIPTION_KEY`。
2. 新增 `testSetTableDescription` 测试：构造一个带 `comment=hello world!` 属性的 `TableMetadata`，调用 `IcebergToGlueConverter.setTableInputInformation`，断言生成的 `TableInput.description()` 等于 `"hello world!"`。这是验证新功能的直接测试，确保表属性中的 `comment` 被正确写入 Glue 表描述字段。

### aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogTable.java

**修改目的**：集成测试，验证端到端的表创建流程能正确把 `comment` 写到 Glue。

**工作逻辑**：在 `testCreateTable` 中扩展为带 `IcebergToGlueConverter.GLUE_DESCRIPTION_KEY = "Test table"` 的属性 map，然后在加载表后断言 `table.properties().get(GLUE_DESCRIPTION_KEY)` 等于该描述，并断言 `response.table().description()` 也等于该描述。后者验证了 Glue 表对象原生 `description` 字段确实被填充，是端到端的关键证据。

### aws/src/integration/java/org/apache/iceberg/aws/glue/TestGlueCatalogNamespace.java

**修改目的**：集成测试适配常量重命名。

**工作逻辑**：4 处 `GLUE_DB_DESCRIPTION_KEY` 引用替换为 `GLUE_DESCRIPTION_KEY`，覆盖 namespace 创建、`setProperties`、`removeProperties` 等场景，确保 namespace 行为未受重命名影响。

## 小结

这是一个聚焦、低风险但用户价值明确的功能增强：它打通了 Iceberg 表属性 `comment` 与 AWS Glue 原生表描述字段的双向映射，使得依赖 Glue `description` 的下游工具（Athena、Glue Catalog UI 等）能直接看到 Iceberg 表的描述。同时通过常量重命名 `GLUE_DB_DESCRIPTION_KEY → GLUE_DESCRIPTION_KEY` 让命名反映其跨层级（database + table）的通用语义。该提交体现了 Iceberg 在保持 API 简洁的同时尽量贴合底层目录原生能力的设计哲学，对依赖 Glue Catalog 做数据治理的用户有实际收益。
