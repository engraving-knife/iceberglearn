# 提交 2896：Test: Avoid deprecated AvroParquetWriter.builder(Path file) (#14620)

## 提交信息

- **序号**：2896 / 4088
- **哈希**：6e873baa7db22429fa231d552a696248f64ea1f7
- **短哈希**：6e873baa7
- **日期**：2025-11-20 09:18:27 +0900
- **作者**：Yuya Ebihara
- **提交说明**：Test: Avoid deprecated AvroParquetWriter.builder(Path file) (#14620)
- **PR/Issue**：#14620

## 总体目的

本提交清理测试代码中对已弃用 API 的使用，将 4 个测试类里调用 `AvroParquetWriter.builder(org.apache.hadoop.fs.Path)` 的写法替换为基于 `OutputFile` 的非弃用重载 `AvroParquetWriter.builder(org.apache.parquet.io.OutputFile)`。

Parquet-mr 在演进过程中弃用了接收 Hadoop `Path` 的 `builder(Path file)` 构造方法，转而推荐使用基于 Parquet 自身 `OutputFile` 接口的 `builder(OutputFile)` 方法。`OutputFile` 是 Parquet 抽象出的存储无关输出接口，使写入器不再硬绑定 Hadoop 文件系统抽象，更利于对接不同存储后端。继续使用弃用 API 会在编译时产生弃用警告，且在未来大版本中该 API 可能被移除，届时会破坏构建。本次改动属于测试代码的技术债清理，确保测试在当前 Parquet 版本下不再触发弃用警告，并为后续升级 Parquet 或移除弃用 API 扫清障碍。

改动仅限测试代码，不影响生产逻辑，行为上等价——都是向本地临时文件写入 Parquet 数据。

## 如何达成设计目的

对每个测试文件执行相同的机械替换：移除 Hadoop `Path` 的导入（或内联全限定名），新增 `org.apache.parquet.io.LocalOutputFile` 的导入，并将 `new Path(testFile.toURI())` 替换为 `new LocalOutputFile(testFile.toPath())`。`LocalOutputFile` 是 Parquet 提供的针对本地文件系统的 `OutputFile` 实现，接收 `java.nio.file.Path`，语义上与原先基于 Hadoop `Path`（由 `File.toURI()` 构造）指向同一本地文件等价。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/parquet/TestGenericData.java` (+3/-2 lines)

**修改目的**：避免在通用数据 Parquet 测试中使用弃用的 `builder(Path)`。

**工作逻辑**：移除 `import org.apache.hadoop.fs.Path;`，新增 `import org.apache.parquet.io.LocalOutputFile;`；将构造写入器处的 `AvroParquetWriter.<GenericRecord>builder(new Path(testFile.toURI()))` 改为 `AvroParquetWriter.<GenericRecord>builder(new LocalOutputFile(testFile.toPath()))`（因换行排版调整，diff 显示 +3/-2）。其余 `withDataModel`、`withSchema`、`config` 等链式配置不变。

### `data/src/test/java/org/apache/iceberg/data/parquet/TestParquetEncryptionWithWriteSupport.java` (+3/-2 lines)

**修改目的**：避免在 Parquet 加密写入支持测试中使用弃用的 `builder(Path)`。

**工作逻辑**：与上一文件相同的替换模式——移除 `Path` 导入、新增 `LocalOutputFile` 导入，将 `builder(new Path(testFile.toURI()))` 改为 `builder(new LocalOutputFile(testFile.toPath()))`。该测试涉及加密属性（`withEncryption(fileEncryptionProperties)`），替换后写入目标文件不变，加密行为不受影响。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/data/TestFlinkParquetReader.java` (+2/-2 lines)

**修改目的**：避免在 Flink Parquet 读取器测试中使用弃用的 `builder(Path)`。

**工作逻辑**：移除 `import org.apache.hadoop.fs.Path;`，新增 `import org.apache.parquet.io.LocalOutputFile;`；将 `AvroParquetWriter.<GenericRecord>builder(new Path(testFile.toURI()))` 改为 `AvroParquetWriter.<GenericRecord>builder(new LocalOutputFile(testFile.toPath()))`。该测试用 Avro 写入 Parquet 文件后用 Flink 读取器校验，写入方式替换不影响读取验证逻辑。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java` (+2/-1 lines)

**修改目的**：避免在 Parquet 模块基础测试中使用弃用的 `builder(Path)`。

**工作逻辑**：此处原先使用的是内联全限定名 `new org.apache.hadoop.fs.Path(testFile.toURI())` 而非单独 import。改动新增 `import org.apache.parquet.io.LocalOutputFile;`，并将 `AvroParquetWriter.<GenericRecord>builder(new org.apache.hadoop.fs.Path(testFile.toURI()))` 改为 `AvroParquetWriter.<GenericRecord>builder(new LocalOutputFile(testFile.toPath()))`，从而同时消除了内联全限定名与弃用调用。

## 总结

这是一次纯测试代码的技术债清理，将 4 个测试类中对已弃用 `AvroParquetWriter.builder(Path)` 的调用统一替换为基于 `LocalOutputFile` 的非弃用重载。改动行为等价、风险极低，消除了编译弃用警告，为后续 Parquet 升级与弃用 API 的最终移除做好准备，保持测试代码与上游 API 演进同步。
