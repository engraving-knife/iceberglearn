# 提交 0588：Docs: Enhance Spark pages

## 提交信息

- **序号**：0588 / 4088
- **哈希**：71ff8a484dea72172d33d2133184af8d13481a0b
- **短哈希**：71ff8a484
- **日期**：2024-03-12 15:44:56 +0800
- **作者**：Manu Zhang <OwenZhang1990@gmail.com>
- **提交说明**：Docs: Enhance Spark pages (#9920)
  1. Fix internal links
  2. Move `Type Compatibility` section from spark-writes.md to spark-getting-started.md
- **PR/Issue**：#9920

## 总体目的

本提交针对 Iceberg 文档站点（MkDocs Material 构建）中 Spark 相关页面做两项增强：

1. **修复内部链接**：所有 `spark-*.md` 页面中存在大量指向同目录下其它文档（如 `configuration.md`、`partitioning.md`、`maintenance.md`、`branching.md` 以及其它 `spark-*.md`）的相对链接，这些链接只写了文件名（如 `configuration.md`），在 MkDocs 默认的 `use_directory_urls: true` 模式下会解析到错误的 URL 路径，导致站点上这些链接 404 或指向不存在的子路径。

2. **迁移"Type Compatibility"章节**：原来位于 `spark-writes.md` 末尾的"Type compatibility"章节（包含 Spark↔Iceberg 双向类型转换表）被迁移到 `spark-getting-started.md`。动机是类型兼容性是用户在入门阶段设计表结构时就需要了解的基础信息，放在"getting started"页面更符合阅读顺序，而不是埋在"writes"页面深处。

## 如何达成设计目的

**链接修复思路**：MkDocs 默认开启 `use_directory_urls: true`，会把 `spark-ddl.md` 渲染为 URL `/spark-ddl/`（目录式 URL）。此时页面内的相对链接 `configuration.md` 会被浏览器解析为 `/spark-ddl/configuration.md`（相对于当前"目录"），而不是期望的 `/configuration/`。修复方式是在所有跨页面相对链接前加 `../`，使其先回到上级目录再定位目标文件——`../configuration.md` 解析为 `/configuration.md` → `/configuration/`，即正确目标。这一修法统一应用于全部 7 个 Spark 页面中所有指向同目录其它文件的链接（含 `spark-configuration.md`、`spark-ddl.md`、`spark-writes.md`、`spark-queries.md`、`spark-procedures.md`、`spark-getting-started.md`、`spark-structured-streaming.md` 之间的互链，以及指向 `configuration.md`、`partitioning.md`、`maintenance.md`、`branching.md` 的链接）。

**章节迁移思路**：把 `spark-writes.md` 末尾的 `## Type compatibility` 整段（含引言、`### Spark type to Iceberg type` 子节及转换表、`!!! info` 提示块、`### Iceberg type to Spark type` 子节及转换表）整体剪切，粘贴到 `spark-getting-started.md` 中 `### Next steps` 之前的位置。同时把 `spark-ddl.md` 中引用该章节锚点的链接从 `spark-writes.md#spark-type-to-iceberg-type` 更新为 `../spark-getting-started.md#spark-type-to-iceberg-type`，保持跳转目标正确。迁移是纯文本搬运，不修改表格内容与措辞。

## 修改详情

### `docs/docs/spark-configuration.md`

**修改目的**：修复指向通用配置页的相对链接。

**工作逻辑**：将 `[catalog configuration](configuration.md#catalog-properties)` 改为 `[catalog configuration](../configuration.md#catalog-properties)`。锚点 `#catalog-properties` 保留不变，仅修正目录层级。这样在 `use_directory_urls: true` 下，从 `/spark-configuration/` 页面跳转可正确到达 `/configuration/#catalog-properties`。

### `docs/docs/spark-ddl.md`

**修改目的**：修复多处跨页面链接，并更新"Type compatibility"章节迁移后的引用路径。

**工作逻辑**：
- `[type compatibility on creating table](spark-writes.md#spark-type-to-iceberg-type)` → `[type compatibility on creating table](../spark-getting-started.md#spark-type-to-iceberg-type)`：既加了 `../` 修正目录层级，又把目标文件从 `spark-writes.md` 改为 `spark-getting-started.md`（因章节已迁移）。
- `[table configuration](configuration.md)` → `[table configuration](../configuration.md)`
- `[hidden partitions](partitioning.md)` → `[hidden partitions](../partitioning.md)`
- `[\`SparkCatalog\`](spark-configuration.md#catalog-configuration)` → `[\`SparkCatalog\`](../spark-configuration.md#catalog-configuration)`（CTAS、RTAS 段各一处，共 4 处 `spark-configuration.md` 链接）
- `[SQL extensions](spark-configuration.md#sql-extensions)` → `[SQL extensions](../spark-configuration.md#sql-extensions)`（2 处）
- `[Table configuration](configuration.md)` → `[Table configuration](../configuration.md)`

### `docs/docs/spark-getting-started.md`

**修改目的**：修复所有出站链接，并承接从 `spark-writes.md` 迁入的"Type compatibility"章节。

**工作逻辑**：
- 修复指向 `spark-configuration.md#catalogs`、`spark-ddl.md#create-table`、`spark-ddl.md#create-table-as-select`、`spark-ddl.md#alter-table`、`spark-ddl.md#drop-table`、`spark-writes.md#insert-into`、`spark-writes.md#merge-into`、`spark-writes.md#delete-from`、`spark-writes.md#writing-with-dataframes`、`spark-queries.md#inspecting-tables`、`spark-queries.md#querying-with-dataframes`、`spark-ddl.md`、`spark-queries.md`、`spark-writes.md`、`spark-procedures.md` 的链接，全部加 `../` 前缀。
- 在 `### Next steps` 之前新增 `### Type compatibility` 章节，内容为从 `spark-writes.md` 搬运的完整类型转换说明：
  - 引言段：说明 Spark 与 Iceberg 类型集合不同，Iceberg 自动转换但不覆盖所有组合。
  - `#### Spark type to Iceberg type`：18 行转换表（boolean/short/byte/integer/long/float/double/date/timestamp/timestamp_ntz/char/varchar/string/binary/decimal/struct/array/map → 对应 Iceberg 类型），并附 `!!! info` 提示块说明写入时的更宽 promotion 规则（如 short/byte/integer/long 可写入 Iceberg long；Spark binary 可写入 Iceberg fixed 但会做长度断言）。
  - `#### Iceberg type to Spark type`：17 行反向转换表，其中 Iceberg `time` 在 Spark 中"Not supported"。

### `docs/docs/spark-procedures.md`

**修改目的**：修复指向 `spark-configuration.md` 与 `configuration.md` 的链接。

**工作逻辑**：3 处改动——`[Spark catalogs](spark-configuration.md)` → `../spark-configuration.md`；`[Iceberg SQL extensions](spark-configuration.md#sql-extensions)` → `../spark-configuration.md#sql-extensions`；`expire_snapshots` 段的 `[expiration properties](configuration.md#table-behavior-properties)` → `../configuration.md#table-behavior-properties`；`rewriteDataFiles` 与 `rewriteDeleteFiles` 两处表格内的 `[table properties](configuration.md#write-properties)` → `../configuration.md#write-properties`。

### `docs/docs/spark-queries.md`

**修改目的**：修复指向 `spark-configuration.md` 的链接。

**工作逻辑**：2 处——`[Spark catalogs](spark-configuration.md)` → `../spark-configuration.md`；`[catalog name](spark-configuration.md#using-catalogs)` → `../spark-configuration.md#using-catalogs`。

### `docs/docs/spark-structured-streaming.md`

**修改目的**：修复指向 `spark-ddl.md`、`spark-writes.md`、`maintenance.md`、`spark-procedures.md` 的链接。

**工作逻辑**：4 处——`[SQL create table](spark-ddl.md#create-table)` → `../spark-ddl.md#create-table`；`[here](spark-writes.md#writing-distribution-modes)` → `../spark-writes.md#writing-distribution-modes`；Expire old snapshots 段的 `[regularly maintained](maintenance.md#expire-snapshots)` 与 `[Snapshot expiration](spark-procedures.md#expire_snapshots)` 各加 `../`；Compacting 段的 `[Compacting small files into larger files](maintenance.md#compact-data-files)` 与 `[\`rewrite_data_files\` procedure](spark-procedures.md#rewrite_data_files)` 各加 `../`；Rewrite manifests 段的 `[rewrite the number of manifest files ...](maintenance.md#rewrite-manifests)` 与 `[\`rewrite_manifests\` procedure](spark-procedures.md#rewrite_manifests)` 各加 `../`。

### `docs/docs/spark-writes.md`

**修改目的**：修复出站链接，并移除已迁移的"Type compatibility"章节。

**工作逻辑**：
- 修复指向 `spark-configuration.md`（2 处：`[Spark catalogs]` 与 `[Iceberg SQL extensions]`）、`branching.md`（`[branches](branching.md)`）、`configuration.md`（`[\`write.target-file-size-bytes\`](configuration.md#write-properties)`）的链接，均加 `../` 前缀。
- 删除文件末尾的 `## Type compatibility` 整段（含两个子节、两个转换表、一个 `!!! info` 提示块，约 60 行）。该内容已原样迁入 `spark-getting-started.md`。

## 小结

本提交是 Iceberg Spark 文档页面的一次结构性维护，包含两类改动：一是全量修复 Spark 页面间及指向通用文档的相对链接（统一加 `../` 前缀以适配 MkDocs `use_directory_urls: true` 的目录式 URL 行为），二是把"Type compatibility"章节从 `spark-writes.md` 迁到 `spark-getting-started.md` 以优化信息架构。改动涉及 7 个文件、99 行新增 / 99 行删除（增删平衡，因为迁移是"剪切+粘贴"）。

回迁到 1.4.x 分支的注意事项：这是纯文档修复，不涉及代码或构建逻辑。1.4.x 分支的 `docs/docs/spark-*.md` 若存在相同的链接问题（很可能存在，因为这是 MkDocs 通用行为），**建议回迁**以修复站点上的 404 链接。回迁时需注意：1.4.x 分支的 Spark 文档页面内容可能与 main 有差异，应逐文件比对链接是否存在；"Type compatibility"章节迁移需同时改 `spark-writes.md`（删除）与 `spark-getting-started.md`（新增）及 `spark-ddl.md`（更新引用），三者必须一起回迁。若 1.4.x 分支已自行调整过文档结构，则需手动核对而非直接 cherry-pick。
