# 提交 3414：Spark: Remove Apache DataFusion Comet integration (#15674)

## 提交信息

- **序号**：3414 / 4088
- **哈希**：0d3544062ead4c2c8de3039e20e52726ae52811e
- **短哈希**：0d3544062e
- **日期**：2026-03-18 12:06:36 -0700
- **作者**：Andy Grove
- **提交说明**：Spark: Remove Apache DataFusion Comet integration (#15674)
- **PR/Issue**：#15674

## 总体目的

移除 Iceberg Spark 集成中对 Apache DataFusion Comet 的支持。Comet 是一个基于 DataFusion 的 Spark 加速引擎，此前 Iceberg 为其提供了专用的向量化读取器。由于 Comet 集成不再需要或维护成本过高，本提交从 Spark 3.4、3.5、4.0 和 4.1 四个版本中移除所有 Comet 相关代码，包括依赖、读取器类、配置属性和测试基础设施。

## 如何达成设计目的

1. 从各 Spark 版本的 `build.gradle` 中移除 `datafusion-comet` 依赖
2. 从版本目录 `gradle/libs.versions.toml` 中移除 Comet 依赖声明
3. 删除所有 Comet 专用向量化读取器类（7个类，每个版本一份）
4. 删除 `ParquetReaderType` 枚举类
5. 从 `SparkReadConf`、`SparkSQLProperties`、`ParquetBatchReadConf` 中移除 Comet 相关配置
6. 从 `VectorizedSparkParquetReaders`、`SparkBatch`、`BaseBatchReader` 等类中移除 Comet 分支逻辑
7. 删除 `TestParquetCometVectorizedScan` 测试类
8. 更新 `TestSparkReaderDeletes` 移除 Comet 相关测试
9. 更新文档移除 Comet 相关配置说明
10. 更新 checkstyle 抑制规则

## 修改详情

### 构建文件
- `gradle/libs.versions.toml`：移除 datafusion-comet 依赖声明
- `spark/v{3.4,3.5,4.0,4.1}/build.gradle`：移除 Comet 依赖（-3/-5 lines each）

### 删除的类文件（每个 Spark 版本一份）
- `ParquetReaderType.java`（-47 lines each）：定义读取器类型枚举（ICEBERG、COMET等）
- `CometColumnReader.java`（-140 lines each）：Comet 列读取器
- `CometColumnarBatchReader.java`（-137 lines each）：Comet 列式批处理读取器
- `CometConstantColumnReader.java`（-65 lines each）：Comet 常量列读取器
- `CometDeleteColumnReader.java`（-74 lines each）：Comet 删除列读取器
- `CometDeletedColumnVector.java`（-155 lines each）：Comet 删除列向量
- `CometPositionColumnReader.java`（-62 lines each）：Comet 位置列读取器
- `CometVectorizedReaderBuilder.java`（-138 lines each）：Comet 向量化读取器构建器
- `TestParquetCometVectorizedScan.java`（-33 lines each）：Comet 向量化扫描测试

### 修改的源文件（每个 Spark 版本一份）
- `SparkReadConf.java`（-8 lines）：移除 `cometVectorizationEnabled()` 等方法
- `SparkSQLProperties.java`（-3/-4 lines）：移除 Comet 相关属性常量
- `ParquetBatchReadConf.java`（-2 lines）：移除 readerType 相关逻辑
- `VectorizedSparkParquetReaders.java`（-22 lines）：移除 Comet 分支构建逻辑
- `SparkBatch.java`（-30/-1 lines）：移除 readerType 参数和 Comet 支持
- `BaseBatchReader.java`（+/-10 lines）：移除 Comet 相关分支
- `SparkFormatModels.java`（-8 lines）：移除 Comet 相关方法

### 文档和配置
- `docs/docs/spark-configuration.md`（-1 line）：移除 reader-type 配置说明
- `.baseline/checkstyle/checkstyle-suppressions.xml`（-5 lines）：移除 Comet 相关抑制规则

## 总结

本提交从 Spark 3.4/3.5/4.0/4.1 四个版本中完全移除了 Apache DataFusion Comet 集成，共删除约 3756 行代码。移除内容包括 Comet 向量化读取器类、配置属性、构建依赖和测试。这使得 Spark 读取路径更加简洁，减少了维护负担。
