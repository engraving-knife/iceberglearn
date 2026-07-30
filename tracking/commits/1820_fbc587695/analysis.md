# 提交 1820：Spark 3.5: Infer partition spec in ADD_FILES procedure for FileTable (#12457)

## 提交信息

- **序号**：1820 / 4088
- **哈希**：fbc5876955be3394b3bd5ff64d017a58d4aa47c8
- **短哈希**：fbc587695
- **日期**：2025-03-04 18:08:26 -0600
- **作者**：Bharath Krishna
- **提交说明**：Spark 3.5: Infer partition spec in ADD_FILES procedure for FileTable (#12457)
- **PR/Issue**：#12457（注：tsv 中记录为 #12327）

## 总体目的

该提交修复了 Spark 3.5 的 ADD_FILES 存储过程在导入 FileTable（如 Parquet/ORCT 文件目录）时的分区 spec 推断问题。在此之前，ADD_FILES 过程对 FileTable 导入直接使用目标 Iceberg 表的当前 spec（`table.spec()`）来解析源文件的分区布局，而非从源文件目录的实际分区结构推断。

问题在于：当目标 Iceberg 表有多个分区 spec（例如表经历过分区演进），直接使用 `table.spec()`（即默认/最新 spec）可能不匹配源文件目录的实际分区列。例如源目录按 `date` 分区，但目标表最新 spec 是按 `date, category` 分区，使用最新 spec 会导致分区列不匹配或错误。正确做法是利用 Spark 的 `InMemoryFileIndex` 从源文件目录自动推断分区结构，再在 Iceberg 表的 specs 中查找匹配的 spec。

此外，该提交还将分区过滤校验逻辑从 AddFilesProcedure 移至 SparkTableUtil，并移除了原先对非 identity 分区转换的禁止校验（改为通过 spec 兼容性匹配来保证正确性）。

## 如何达成设计目的

整体思路是让 ADD_FILES 在导入 FileTable 时，通过 Spark 的 `InMemoryFileIndex` 自动推断源目录的分区列，再调用 `SparkTableUtil.findCompatibleSpec()` 在 Iceberg 表的 specs 中找到与源分区列匹配的 spec。新增 `Spark3Util.getInferredSpec()` 利用 Spark 文件索引推断分区 schema。重构 `findCompatibleSpec()` 为接受分区列名列表的公开方法。分区过滤校验 `validatePartitionFilter` 移至 SparkTableUtil 复用。importFileTable 不再接收 spec 参数，而是内部推断。

## 修改详情

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/Spark3Util.java (修改, 17 lines)

新增 `getInferredSpec(SparkSession spark, Path rootPath)` 方法：利用 `FileStatusCache` 和 `InMemoryFileIndex` 构建文件索引，传入空 schema 让 Spark 自动推断分区结构，返回 `PartitionSpec`（Spark 内部类型）。关键参数 `Option.empty()` 使 Spark 自动执行分区 schema 推断。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java (修改, 84 lines)

- 将原 `findCompatibleSpec(Table, SparkSession, String)` 私有方法重构：提取核心逻辑为公开的 `findCompatibleSpec(List<String> partitionNames, Table)`，接收分区列名列表而非从 Spark catalog 获取。原方法保留为包装调用。
- 新增 `validatePartitionFilter(PartitionSpec, Map<String,String>, String)` 公开方法：校验分区过滤条件合法（分区列数足够、过滤列存在、非分区表不传过滤）。
- 在 `addFiles` 路径调用 `validatePartitionFilter`。

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/procedures/AddFilesProcedure.java (修改, 94 lines)

- `importFileTable` 方法不再接收 `PartitionSpec spec` 参数。改为内部调用 `Spark3Util.getInferredSpec()` 推断源分区列，再调用 `SparkTableUtil.findCompatibleSpec()` 查找匹配的 Iceberg spec，最后用推断的 compatibleSpec 进行分区列举和导入。
- 移除了原 `validatePartitionSpec()` 私有方法（含非 identity 转换禁止校验），改用 SparkTableUtil.validatePartitionFilter。
- `importPartitions` 新增 `PartitionSpec spec` 参数，使用推断的 spec 而非 table.spec()。
- 移除了 `PartitionField`、`Set` 等不再需要的导入。

### spark/v3.5/spark-extensions/src/test/.../TestAddFilesProcedure.java (修改, 100 lines)

新增测试覆盖：FileTable 导入时使用推断 spec 而非最新 table spec 的场景，包括源目录分区列与目标表不同 spec 匹配的情况。

## 小结

该提交改善了 ADD_FILES 过程对 FileTable 导入的分区 spec 处理，从使用目标表最新 spec 改为从源目录推断并匹配兼容 spec。影响范围为 Spark 3.5 模块。回迁到 1.4.x 分支时需注意：1.4.x 分支的 Spark 3.5 模块的 AddFilesProcedure、SparkTableUtil、Spark3Util 结构需核对；重构涉及方法签名变更，需保证调用方同步更新。由于该修复解决实际分区不匹配问题，建议回迁。注意完整哈希对应的短哈希为 `fbc587695`（取前9位）。
