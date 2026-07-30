# 提交 1459：Build: Bump Parquet to 1.15.0 (#11656)

## 提交信息

- **序号**：1459 / 4088
- **哈希**：c7cef9bd81d150108a56a848b7482f179ea878b8
- **短哈希**：c7cef9bd8
- **日期**：2024-12-04（Wed Dec 4 12:10:24 2024 +0100）
- **作者**：Fokko Driesprong <fokko@apache.org>
- **提交说明**：Build: Bump Parquet to 1.15.0 (#11656)
- **PR/Issue**：#11656

## 总体目的

把 Apache Parquet Java 库从 `1.14.4` 升级到 `1.15.0`。Parquet 是 Iceberg 最核心的列式文件格式依赖——Iceberg 的数据文件、删除文件、元数据读取与写入大量依赖 `parquet-avro`、`parquet-column`、`parquet-hadoop` 等模块。Parquet 1.15.0 是一个新的 minor 版本（1.14.x → 1.15.x），通常包含：

- 新的格式特性支持（如新的编码、压缩选项）；
- Bug 修复（包括读取/写入路径的 correctness 修复）；
- 性能改进（向量化读取、字典编码优化等）；
- 对新版 Hadoop/JDK 的兼容性改进。

与 patch 级升级不同，minor 版本升级可能引入少量 API 变更，因此本提交由人工（Fokko，Iceberg PMC 成员）而非 Dependabot 发起，便于在升级时同步验证兼容性。

## 如何达成设计目的

修改 Gradle 版本目录 `gradle/libs.versions.toml` 中 `parquet` 的版本号。由于 Iceberg 通过版本目录统一管理 Parquet 版本，所有依赖 `libs.parquet.*` 的模块（`parquet`、`core`、`spark`、`flink`、`mr` 等）会自动拉取 1.15.0。

## 修改详情

### `gradle/libs.versions.toml`（修改，1 行）

**修改目的**：将 Parquet 版本从 `1.14.4` 升级到 `1.15.0`。

```toml
-parquet = "1.14.4"
+parquet = "1.15.0"
```

该条目位于版本目录的依赖版本区，下游通过 `libs.parquet.avro`、`libs.parquet.column`、`libs.parquet.hadoop` 等引用，统一随版本目录解析。

## 小结

- **成效**：Parquet 依赖升级到 1.15.0，获取上游 minor 版本的 bug 修复、性能改进与新特性。
- **影响范围**：所有读写 Parquet 文件的模块（core、parquet、spark、flink、mr 等）。由于是 minor 版本升级，可能存在少量 API 变更，但本提交仅改版本号、无适配性代码修改，说明 Iceberg 现有代码与 1.15.0 API 兼容。
- **回迁到 1.4.x 的注意事项**：Parquet 是核心依赖，minor 版本升级需谨慎。回迁前应确认：① 1.4.x 分支的 `parquet` 模块代码（如 `Parquet.java`、`ParquetWriter`、`ParquetReader` 封装）与 Parquet 1.15.0 的 API 兼容——若 1.4.x 上有针对 1.14.x 的 workaround 或对内部 API 的依赖，需检查是否仍有效；② 回归 Parquet 读写测试（`TestParquet`、`SparkParquetWriter` 等），特别是向量化读取、字典编码、嵌套结构等路径；③ 若 1.4.x 同时维护多个 Spark/Flink 版本，需确认各版本的 Parquet 依赖冲突解析仍正常（Spark 自身可能强制特定 Parquet 版本，需检查 `dependencyManagement` 中的 resolutionStrategy）。建议回迁后在目标运行环境完整跑一遍 Parquet 相关测试套件。
