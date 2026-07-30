# 提交 1562 39a4cfd6c 分析

## 提交信息
- 哈希：39a4cfd6c82883ef080bc67e9c154bc95f6a03a0
- 日期：2025-01-08（Wed Jan 8 22:54:39 2025 +0800）
- 作者：Szehon Ho <szehon.apache@gmail.com>
- 消息：Spark 3.5: Implement RewriteTablePath (#11555)

## 总体目的

Iceberg 表的所有元数据（metadata.json、manifest list、manifest、data file、delete file 路径）以及表属性中都包含绝对路径。当用户需要把表从一个存储位置迁移到另一个位置（例如从本地 FS 迁到 S3、从一个 S3 bucket 迁到另一个、从某个前缀迁到新前缀）时，仅仅物理拷贝文件是不够的——元数据中嵌套的所有路径引用都必须被改写，否则新位置的表无法正确读取。

`RewriteTablePath` 是 Iceberg actions API 中用于"表路径重写"的接口，此前在 Spark 3.5 上只有接口定义和文档，没有具体实现。本提交为 Spark 3.5 实现了 `RewriteTablePathSparkAction`，并抽取了引擎无关的核心工具类 `RewriteTablePathUtil` 到 core 模块，供各引擎复用。

实现的核心思路是：在 staging 目录中重建一份"路径已改写"的元数据（version 文件、manifest list、manifest、必要的 position delete 文件），并产出一个"拷贝计划"（copy plan）——一份 CSV 文件，列出所有需要从源路径拷贝到目标路径的文件对（源路径 -> 目标路径）。用户后续按这份拷贝计划把数据文件和已重建的元数据文件拷贝到目标位置，即可得到一个功能完整的表副本。这种方式不修改原表，所有重建都在 staging 区完成，安全且可回滚。

支持可选的 `startVersion`/`endVersion` 范围，只处理该版本区间新增的文件，避免重写整张表的历史元数据，适合增量迁移场景。

## 如何达成设计目的

整体分三层：

1. **API 层**（`api` 模块）：`RewriteTablePath` 接口已存在，本次仅补充 Javadoc 说明 `fileListLocation` 的内容格式（CSV，每行 sourcePath,targetPath）。
2. **Core 工具层**（新增 `RewriteTablePathUtil`）：引擎无关的路径改写工具，包括 `replacePaths`（改写 TableMetadata）、`rewriteManifestList`、`rewriteDataManifest`、`rewriteDeleteManifest`、`rewritePositionDeleteFile`、以及路径操作辅助方法（`newPath`/`combinePaths`/`relativize`/`stagingPath`/`fileName`）。同时增强 `ContentFileUtil`（处理 position delete 文件中 file_path 的 metrics 改写）和 `DeleteSchemaUtil`（新增 position delete 读取 schema）。
3. **Spark 实现层**（新增 `RewriteTablePathSparkAction`）：编排整个重建流程，利用 Spark 分布式能力重写 manifest 文件，最终产出 copy plan CSV。`BaseSparkAction` 新增 `newStaticTable` 辅助方法基于 metadata 文件位置加载静态表。`SparkActions` 注册新的 action 工厂方法。

### 重建流程（`RewriteTablePathSparkAction.rebuildMetadata`）

1. **确定版本范围**：`validateAndSetEndVersion`/`validateAndSetStartVersion` —— endVersion 默认为当前 metadata 文件，也可指定历史版本；startVersion 可选，用于增量迁移。版本通过文件名匹配在 metadata log 中查找并校验存在。

2. **重写 version 文件**（`rewriteVersionFiles`）：从 endVersion 开始倒序遍历 metadata log，对每个 version 文件用 `RewriteTablePathUtil.replacePaths` 改写其中所有路径（表 location、属性中的路径、snapshot 的 manifestListLocation、metadata log entry 路径），写入 staging。同时收集这些 version 涉及的所有 snapshot，作为后续需要处理的 snapshot 集合。遇到 startVersion 则停止。

3. **计算 delta snapshots 与待重写 manifest 集合**：`deltaSnapshots` = endMetadata 的 snapshot 集合减去 startMetadata 的 snapshot 集合；`manifestsToRewrite` 通过 Spark 读取 endVersion 表的 manifest DS，按 delta snapshotId 过滤得到需要重写的 manifest 路径集合。

4. **重写 manifest list**（对每个 delta snapshot）：调用 `RewriteTablePathUtil.rewriteManifestList`，读取原 manifest list，对其中的每个 manifest（若在 `manifestsToRewrite` 中）改写路径并写入 staging，产出新的 manifest list 文件。返回需要进一步重写的 manifest 集合。

5. **重写 manifest 文件**（分布式）：`rewriteManifests` 用 Spark 将待重写的 manifest 集合分区后分布式处理。每个 manifest 根据 content 类型（DATA 或 DELETE）调用 `rewriteDataManifest` 或 `rewriteDeleteManifest`：读取 manifest 条目，改写其中每个 DataFile/DeleteFile 的路径，写入新 manifest。对于 position delete 文件，还需要进一步重写其内容（因为 position delete 文件内部记录了被删除数据文件的绝对路径）。

6. **重写 position delete 文件**（`rewritePositionDeletes`）：position delete 文件内部每条记录包含 (file_path, pos, row)，其中 file_path 是被删除数据文件的绝对路径，也需改写。通过 `PositionDeleteReaderWriter` 接口（引擎特定实现）读取原 delete 文件，改写每条记录的 file_path，写入新 delete 文件。

7. **产出 copy plan**：汇总 version 文件、manifest list、manifest、position delete 文件的 (stagingPath -> targetPath) 对，加上原始数据文件的 (sourcePath -> targetPath) 对，写入 staging 目录下的 CSV 文件（`file-list/`），作为最终结果返回。

### 关键工具方法（`RewriteTablePathUtil`）

- `replacePaths(metadata, sourcePrefix, targetPrefix)`：构建新的 `TableMetadata`，改写 location、属性（OBJECT_STORE_PATH、WRITE_FOLDER_STORAGE_LOCATION、WRITE_DATA_LOCATION、WRITE_METADATA_LOCATION）、snapshot 的 manifestListLocation、metadata log 路径。注意 statistics 文件路径暂未处理（有 TODO）。
- `rewriteManifestList`：读取 manifest list，对每个 manifest 改写其 `path` 字段，写入新 manifest list，并返回 (待重写 manifest 集合, manifest 拷贝计划)。
- `rewriteDataManifest`：读取 data manifest 条目，对每个 DataFile 改写 location，按 entry status（ADDED/EXISTING/DELETED）写入新 manifest，返回 (无需进一步重写, 数据文件拷贝计划)。
- `rewriteDeleteManifest`：分两种情况——POSITION_DELETES 需要重写 delete 文件内容（因为内部含 file_path），拷贝计划指向 staging；EQUALITY_DELETES 不含绝对路径，只需改写 manifest 中的路径，无需重写文件内容。
- `rewritePositionDeleteFile`：通过 `PositionDeleteReaderWriter` 读取 position delete 记录，改写每条记录的 file_path（`record.get(0)`），用 writer 重新写入。
- `newPath`/`relativize`/`combinePaths`：路径前缀替换的核心逻辑——先从源路径中剥离 sourcePrefix（relativize），再拼接到 targetPrefix（combinePaths）。
- `stagingPath`：在 staging 目录下按原文件名生成 staging 路径。

### Metrics 处理（`ContentFileUtil.replacePathBounds`）

position delete 文件的 metrics 中包含 `file_path` 字段的 lower/upper bound（用于过滤）。如果 lower==upper（即整个 delete 文件只引用一个数据文件，file-scoped position delete），则改写该 bound 为新路径；否则清空 file_path 的 bounds（因为无法确定唯一路径，且该 bound 仅在 lower==upper 时用于过滤）。

### 修改详情

#### `api/src/main/java/org/apache/iceberg/actions/RewriteTablePath.java`

**修改目的**：补充 Javadoc 说明 `fileListLocation` 的内容格式。

**工作逻辑**：更新接口文档，说明 fileListLocation 是一个 CSV 文件，每行包含 source path 和 target path（逗号分隔），source path 可能是原表路径或 staging 路径（对于已重写的元数据），target path 是新前缀下的路径。给出示例。把"comma-separated list of source and target paths"的描述改为更清晰的"copy-plan"说明。

#### `core/src/main/java/org/apache/iceberg/RewriteTablePathUtil.java`（新增，578 行）

**修改目的**：引擎无关的表路径改写工具类。

**工作逻辑**：包含内部类 `RewriteResult<T>`（记录待重写集合和拷贝计划）、`PositionDeleteReaderWriter`（引擎特定的 position delete 读写接口）；静态方法 `replacePaths`、`rewriteManifestList`、`rewriteDataManifest`、`rewriteDeleteManifest`、`rewritePositionDeleteFile`、`newPath`、`combinePaths`、`relativize`、`stagingPath`、`fileName` 等。详见上文"关键工具方法"。

#### `core/src/main/java/org/apache/iceberg/io/DeleteSchemaUtil.java`

**修改目的**：新增 position delete 的读取 schema。

**工作逻辑**：新增 `posDeleteReadSchema(Schema rowSchema)` 方法，返回包含 `DELETE_FILE_PATH`、`DELETE_FILE_POS`、`DELETE_FILE_ROW`（optional，类型为 rowSchema.asStruct()）三个字段的 schema，供 `RewriteTablePathUtil.rewritePositionDeleteFile` 读取 position delete 记录时使用。

#### `core/src/main/java/org/apache/iceberg/util/ContentFileUtil.java`

**修改目的**：支持改写 position delete 文件 metrics 中的 file_path bound。

**工作逻辑**：新增 `replacePathBounds(DeleteFile, sourcePrefix, targetPrefix)` 方法——若 file_path 的 lower==upper bound，改写为新路径；否则清空 file_path bound。新增私有方法 `metricsWithoutPathBounds`（移除 file_path bound）和 `metricsWithPathBounds`（设置 file_path bound）。同时将原有的 `PATH_ID`/`PATH_TYPE` 提取为类常量，简化 `referencedDataFile` 方法。

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSparkAction.java`

**修改目的**：新增基于 metadata 文件位置加载静态表的方法。

**工作逻辑**：新增 `protected Table newStaticTable(String metadataFileLocation, FileIO io)`，用 `StaticTableOperations` 包装指定 metadata 文件，返回 `BaseTable`。供 `RewriteTablePathSparkAction` 加载历史版本的表元数据。

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteTablePathSparkAction.java`（新增，731 行）

**修改目的**：Spark 3.5 的 `RewriteTablePath` 实现。

**工作逻辑**：实现 `rewriteLocationPrefix`/`startVersion`/`endVersion`/`stagingLocation` 配置方法；`execute` 编排 `rebuildMetadata` 流程（详见上文"重建流程"）；`rewriteManifests` 利用 Spark 分布式重写 manifest（广播 `SerializableTable` 和 `specsById`，用 `MapFunction` 处理每个 manifest）；`rewritePositionDeletes` 也用 Spark 分布式重写 position delete 文件（实现 `PositionDeleteReaderWriter`，支持 Parquet/ORC/Avro 三种格式的 reader/writer）；最终 `saveFileList` 把 copy plan 写为 CSV。

#### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/SparkActions.java`

**修改目的**：注册新的 action 工厂方法。

**工作逻辑**：新增 `rewriteTablePath(Table table)` 方法返回 `new RewriteTablePathSparkAction(spark, table)`。

#### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteTablePathsAction.java`（新增，1052 行）

**修改目的**：全面测试 `RewriteTablePathSparkAction`。

**工作逻辑**：覆盖各种场景——基本路径改写、带 startVersion/endVersion 的增量迁移、staging 目录、position delete 文件路径改写、equality delete 文件、不同文件格式（Parquet/ORC/Avro）、校验 copy plan 正确性、校验重建后的表可读取等。

## 小结

- **成效**：为 Spark 3.5 实现了 `RewriteTablePath` action，用户可以指定 sourcePrefix/targetPrefix（以及可选的版本范围），在 staging 区重建路径已改写的元数据并产出 copy plan CSV，用于安全地迁移表到新存储位置。核心逻辑抽取为引擎无关的 `RewriteTablePathUtil`，便于其他引擎复用。处理了所有路径引用点（version 文件、manifest list、manifest、data/delete 文件路径、表属性、position delete 文件内部路径及其 metrics）。
- **影响范围**：新增 `RewriteTablePathUtil`（578 行）、`RewriteTablePathSparkAction`（731 行）、`TestRewriteTablePathsAction`（1052 行）；修改 `RewriteTablePath` 接口文档、`DeleteSchemaUtil`、`ContentFileUtil`、`BaseSparkAction`、`SparkActions`。涉及 api/core/spark 三个模块，是较大的功能新增。
- **回迁到 1.4.x 的注意事项**：这是一个较大的新功能（表路径重写 action），1.4.x 作为维护分支一般不引入新功能。且该实现依赖较新的 `StaticTableOperations`、`ContentFileUtil` 等基础设施，回迁成本较高。一般**不回迁**到 1.4.x；若 1.4.x 用户有表迁移需求，建议升级到包含此功能的版本。
