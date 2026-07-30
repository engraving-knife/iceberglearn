# 提交 0458：Docs: Enhance Java quickstart example (#9585)

## 提交信息

- **序号**：0458
- **完整哈希**：f8a4a74584f535d41c21d5cac4457c603eb448a1
- **短哈希**：f8a4a7458
- **日期**：2024-02-05 01:30:03 +0800
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Enhance Java quickstart example (#9585)
- **关联 PR**：#9585
- **修改文件**：1 个，共 21 行新增、39 行删除

## 总体目的

本提交对 Iceberg Java API 快速入门文档 `docs/docs/java-api-quickstart.md` 进行多处增强与精简，使其更准确、更易上手，并修正若干遗留问题。

首先，关于 Hive catalog 的初始化说明存在过时表述。原文档中有一条 "Note"，声称 `setConf` 对 Hive catalog 总是必需的（"Currently, `setConf` is always required for hive catalogs, but this will change in the future"）。实际上该限制已不再成立，因此本提交删除了该 Note，并将代码注释从 "Configure using Spark's Hadoop configuration" 改为 "Optionally use Spark's Hadoop configuration"，明确 `setConf` 现为可选操作。同时为代码示例补上了 `java.util.HashMap` 与 `java.util.Map` 的 import 语句，使示例可直接编译运行。

其次，文档在表创建与加载的示例中，将"加载已有表"的代码行从注释形式（`// Table table = catalog.loadTable(name);`）改为实际可执行代码（`Table table = catalog.loadTable(name);`），这样示例既展示创建又展示加载两种用法，更贴近真实使用场景。相应地，将后续引用从 "The logs schema..." 调整为 "The table's schema..."，用词更通用准确。

第三，文档删除了已不推荐的 "Using Hadoop tables"（`HadoopTables`）章节。该章节介绍了基于目录的 Hadoop 表用法，并包含一条关于 Hadoop 表不应在不支持原子重命名的文件系统上使用的危险警告。随着 Hive catalog 与基于路径的加载方式成为更主流的方案，移除该章节可避免新用户误入不推荐的路径。取而代之的是重构后的 "Tables in Spark" 章节，简化为展示通过 `HiveCatalog` 按名称访问表（`spark.table("logging.logs")`）以及通过路径加载 `HadoopCatalog` 创建的表（`spark.read.format("iceberg").load(...)`）两种方式，并删除了原来指向 Spark 查询与写入文档的多条交叉链接。

最后，提交还修正了若干 Java 命名规范问题：将变量名 `table_name` 改为驼峰式 `tableName`，将常量风格的 `small_file_1`、`small_file_2`、`compacted_file` 改为驼峰式 `SMALL_FILE_1`、`SMALL_FILE_2`、`compactedFile`，使示例代码符合 Java 命名约定。

## 如何达成设计目的

实现路径是对单个文档文件的多处定向编辑：删除过时 Note 与 import 补全、调整 `setConf` 注释、将 `loadTable` 示例从注释改为可执行、调整文字描述用词、整体替换 "Using Hadoop tables" 与 "Tables in Spark" 两个章节的内容、修正多处变量命名。所有改动均围绕"让快速入门示例更准确、更简洁、更符合 Java 规范"这一目标展开，不涉及任何代码逻辑。

## 修改详情

### docs/docs/java-api-quickstart.md

**修改目的**：增强 Java 快速入门示例的准确性、可运行性与命名规范。

**工作逻辑**：该文件的修改可归纳为以下几组：

1. **Hive catalog 初始化示例（import 与 Note）**：在 Hive catalog 代码块开头新增两行 import：`import java.util.HashMap` 与 `import java.util.Map`，使示例具备完整可编译的 import。删除了代码块前的 "Note: Currently, `setConf` is always required..." 一行，因为该限制已不成立。将 `catalog.setConf(...)` 行的注释从 "Configure using Spark's Hadoop configuration" 改为 "Optionally use Spark's Hadoop configuration"，标明该调用现为可选。

2. **Catalog 接口说明文字调整**：将 "The `Catalog` interface defines methods for working with tables... `HiveCatalog` implements the `Catalog` interface." 这两句的语序对调，改为先说 "`HiveCatalog` implements the `Catalog` interface, which defines methods..."，使表述更自然。

3. **表创建/加载示例**：在 Hive catalog 与 Hadoop catalog 两处示例中，将 `// Table table = catalog.loadTable(name);`（注释行）改为 `Table table = catalog.loadTable(name);`（可执行行），同时保留上方的 `createTable` 行。这样示例同时展示创建与加载。并将紧随其后的 "The logs [schema]..." 文字改为 "The table's [schema]..."。

4. **"Using Hadoop tables" 章节删除与 "Tables in Spark" 章节重写**：删除原 "Using Hadoop tables" 整个章节（包含 `HadoopTables` 用法示例与 !!! danger 警告块）。原 "Tables in Spark" 章节中关于 "Spark uses both `HiveCatalog` and `HadoopTables`..." 的说明文字与三条交叉链接（Spark queries、INSERT INTO、MERGE INTO）被删除，替换为两个简洁的代码示例：通过 `spark.table("logging.logs")` 按名称访问 HiveCatalog 中的表，以及通过 `spark.read.format("iceberg").load("hdfs://host:8020/warehouse_path/logging/logs")` 按路径加载 HadoopCatalog 创建的表。

5. **变量命名规范化**：在 `SparkSchemaUtil.schemaForTable(sparkSession, table_name)` 中将 `table_name` 改为 `tableName`；在 rewrite 操作示例中将 `small_file_1`、`small_file_2`、`compacted_file` 改为 `SMALL_FILE_1`、`SMALL_FILE_2`、`compactedFile`，并在对应注释中同步更新名称。

## 小结

本提交是对 Java API 快速入门文档的一次全面打磨。核心改进包括：移除已过时的 `setConf` 必需性说明并补全 import 以提升示例可运行性、将加载表示例从注释改为可执行代码、删除不推荐的 `HadoopTables` 章节并简化 Spark 表访问示例、统一变量命名为 Java 驼峰规范。修改净减 18 行（21 增 39 删），体现了"精简过时内容、增强准确性"的文档维护方向。属于纯文档变更，无代码逻辑影响。
