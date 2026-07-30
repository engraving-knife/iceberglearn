# 提交 1374：Spark: Update tests which assume file format to use enum instead of string literal (#11540)

## 提交信息

- **序号**：1374 / 4088
- **哈希**：3659ded18d50206576985339bd55cd82f5e200cc
- **短哈希**：3659ded18
- **日期**：2024-11-13（Wed Nov 13 13:09:31 2024 -0800）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark: Update tests which assume file format to use enum instead of string literal (#11540)
- **PR/Issue**：#11540

## 总体目的

`TestSparkReaderDeletes` 是 Spark 读取器在有删除文件场景下的核心测试类，通过参数化在多种文件格式（Parquet/ORC/Avro）、是否向量化、规划模式等组合下运行。该测试类的 `@Parameter` 字段 `format` 的类型此前已被改为 `FileFormat` 枚举（参数取值为 `FileFormat.PARQUET`、`FileFormat.ORC`、`FileFormat.AVRO`），但其中 `testPosDeletesOnParquetFileWithMultipleRowGroups` 测试方法里的前置假设语句 `assumeThat(format).isEqualTo("parquet")` 没有同步更新，仍在用字符串字面量 `"parquet"` 与枚举对象比较。

由于 AssertJ 的 `isEqualTo` 用 `equals` 比较，一个 `FileFormat` 枚举对象永远不会 `equals` 一个 `String`，因此该假设在所有参数组合下都会失败，导致 `assumeThat` 直接跳过测试——也就是说，这个本应只在 Parquet 格式下运行的测试，实际上在所有格式下都被跳过了，等于从未真正执行。

本提交将三处 Spark 版本（v3.3/v3.4/v3.5）的该假设从字符串字面量 `"parquet"` 改为枚举常量 `FileFormat.PARQUET`，使假设能够正确匹配，从而让该测试在 Parquet 参数组合下真正运行。

## 如何达成设计目的

直接把 `assumeThat(format).isEqualTo("parquet")` 改为 `assumeThat(format).isEqualTo(FileFormat.PARQUET)`。由于 `format` 字段已是 `FileFormat` 类型，且类中已 `import org.apache.iceberg.FileFormat;`，无需新增 import。改动后，当参数化传入 `FileFormat.PARQUET` 时假设成立、测试执行；传入 ORC/AVRO 时假设不成立、测试被跳过，恢复正确的测试筛选行为。

## 修改详情

### `spark/v3.3/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java`

修改目的：修复 `testPosDeletesOnParquetFileWithMultipleRowGroups` 中格式假设的字符串比较错误。

工作逻辑：将 `assumeThat(format).isEqualTo("parquet")` 改为 `assumeThat(format).isEqualTo(FileFormat.PARQUET)`。该方法测试 Parquet 文件在多个 row group 上应用 position delete 的读取行为，本就只应在 Parquet 格式下运行。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java`

修改目的与逻辑同上，v3.4 版本同步修复。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestSparkReaderDeletes.java`

修改目的与逻辑同上，v3.5 版本同步修复。

三处改动各仅 1 行，合计 3 行变更（3 insertions, 3 deletions）。

## 小结

- 成效：修复了一个长期存在的测试 bug——`testPosDeletesOnParquetFileWithMultipleRowGroups` 因字符串与枚举类型不匹配而始终被跳过，导致 Parquet 多 row group + position delete 的读取路径实际上没有测试覆盖。修复后该测试在 Parquet 参数组合下会真正运行，恢复对相关代码的回归保护。
- 影响范围：仅测试代码，3 个 Spark 版本各 1 行改动，无生产代码变更，无对外行为影响。
- 回迁到 1.4.x 的注意事项：**建议回迁**（若 1.4.x 的对应测试存在同样问题）。这是一个纯测试修复，风险极低：
  1. 需先确认 1.4.x 分支的 `TestSparkReaderDeletes` 中 `format` 字段是否已是 `FileFormat` 枚举类型。若仍是 `String` 类型，则原 `isEqualTo("parquet")` 是正确的，无需回迁；若已是枚举，则应回迁本修复。
  2. 1.4.x 对应的 Spark 版本可能与 main 不同（如 1.4.x 可能只支持到 Spark 3.3/3.4），需检查对应版本目录下的测试文件是否存在同样的字符串比较。
  3. 回迁后应确认该测试在 Parquet 组合下确实通过（而非因其它原因失败），因为此前从未真正运行过，可能暴露被掩盖的既有问题。
