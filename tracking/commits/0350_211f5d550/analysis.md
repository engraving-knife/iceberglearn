# 提交分析：Spark 3.5: Support encrypted output files (#9435)

## 提交信息

- 哈希：211f5d550b1d505d0c2da1e190551919448e0605
- 短哈希：211f5d550
- 日期：2024-01-11 16:20:50 -0800
- 作者：ggershinsky (ggershinsky@users.noreply.github.com)
- 说明：Spark 3.5: Support encrypted output files (#9435)

## 总体目的

本提交的核心目的是修复 Spark 3.5 写入路径在"表启用了原生加密（native Parquet/ORC encryption）"时静默写入明文文件的严重缺陷，并让 Spark 的 `SparkAppenderFactory` 真正走通 Iceberg 加密写入的契约。改动范围非常小（仅 1 个文件、净增 6 行），但触及的是数据安全的关键路径——属于 Iceberg 加密能力在 Spark 引擎落地时的"最后一公里"对齐。

要理解本提交，先要理解 Iceberg 的加密机制。Iceberg 支持两种加密写入模式：

1. **流式加密（AES-GCM，envelope-style）**：通过 `AesGcmOutputFile` 包装底层 `OutputFile`，把明文字节按固定块用 AES-GCM 加密后再写出。`EncryptedOutputFile.encryptingOutputFile()` 返回的就是这个 `AesGcmOutputFile`（它本身就是一个 OutputFile，写入即加密）。Avro 等不支持原生加密的格式走这条路。

2. **原生加密（Native encryption，Parquet/ORC modular encryption）**：Parquet 与 ORC 格式自身支持模块化加密（每列/每块用 footer key + column key 加密）。Iceberg 通过 `NativeEncryptionOutputFile`（同时实现 `EncryptedOutputFile` 与 `NativelyEncryptedFile`）暴露"明文 OutputFile + 密钥元数据"两个视图。`Parquet.write(EncryptedOutputFile)` 内部会检查 `if (file instanceof NativeEncryptionOutputFile)`，若是则取出 `plainOutputFile()` 与 `keyMetadata().encryptionKey()/aadPrefix()`，把密钥参数注入 Parquet 写入器，由 Parquet 自身的加密能力完成加密。

关键问题在于：在本次修复之前，`SparkAppenderFactory` 只覆写了 `newAppender(OutputFile, FileFormat)`，而 `newDataWriter` / `newEqDeleteWriter` / `newPosDeleteWriter` 这三个写数据/删除文件的方法在调用 `Parquet.writeDeletes` / `Avro.writeDeletes` / `ORC.writeDeletes` 与 `newAppender` 时，都先调用 `file.encryptingOutputFile()` 把 `EncryptedOutputFile` 拆出底层 `OutputFile` 再传入。这种拆包在两种加密模式下都会丢失信息：

- 对**原生加密**（最严重）：`NativeEncryptionOutputFile.encryptingOutputFile()` 返回的是它的 `plainOutputFile()`（即明文 OutputFile），密钥元数据被完全丢弃。`Parquet.writeDeletes(OutputFile)` 没有任何密钥参数，写出的就是**完全未加密**的 Parquet 文件——但表的元数据却声称这些文件已加密。这是数据泄露级别的 bug。
- 对**流式加密**：如果上层调用者传入的就是 `BaseEncryptedOutputFile`（持有 `AesGcmOutputFile` 作为 encryptingOutputFile），拆包后传给 `Parquet.write(OutputFile)` 仍能加密，因为 AesGcm 加密在字节流层完成；但这种"恰好能用"依赖于具体实现，且无法支持原生加密。

修复方案是让 `SparkAppenderFactory` 直接消费 `EncryptedOutputFile`：1) 覆写新增的 `newAppender(EncryptedOutputFile, FileFormat)` 方法，把 `EncryptedOutputFile` 整体传给 `Parquet.write(file)` / `Avro.write(file)` / `ORC.write(file)`，由这些写构建器内部判断是走原生加密（NativeEncryptionOutputFile 分支）还是走流式加密（encryptingOutputFile 分支）；2) 把 `newAppender(OutputFile, FileFormat)` 改为一个薄包装，用 `EncryptionUtil.plainAsEncryptedOutput(file)` 把裸 `OutputFile` 包装成"无加密"的 `EncryptedOutputFile`（持有空密钥元数据），再委托给新的 `newAppender(EncryptedOutputFile, FileFormat)`；3) 在 `newDataWriter` / `newEqDeleteWriter` / `newPosDeleteWriter` 中，把原本对 `file.encryptingOutputFile()` 的调用全部改为直接传 `file`（`EncryptedOutputFile`）给 `Parquet.writeDeletes(file)` / `Avro.writeDeletes(file)` / `ORC.writeDeletes(file)` 的 `EncryptedOutputFile` 重载。这样无论表配置哪种加密模式（或无加密），Spark 写入路径都能正确执行，密钥元数据不再丢失。

## 如何达成设计目的

本次修复之所以如此简洁，是因为 Iceberg 的加密基础设施（`EncryptedOutputFile` 接口、`NativeEncryptionOutputFile`、`Parquet/Avro/ORC.write(EncryptedOutputFile)` 重载、`FileAppenderFactory.newAppender(EncryptedOutputFile, FileFormat)` 默认方法、`EncryptionUtil.plainAsEncryptedOutput`）在前序提交中已就位。本提交只需把 Spark 这一侧的 appender factory 从"绕过加密契约"的旧写法切到"尊重加密契约"的新写法即可。`FileAppenderFactory` 接口里 `newAppender(EncryptedOutputFile, FileFormat)` 是一个 default 方法，默认实现是 `newAppender(outputFile.encryptingOutputFile(), fileFormat)`——也就是说默认行为仍是"拆包"，等价于不加密（适合流式加密的 AES-GCM 场景，因为 `encryptingOutputFile()` 返回的 AesGcmOutputFile 会自己加密；但对原生加密是错的）。`SparkAppenderFactory` 必须显式覆写该 default 方法才能走对原生加密分支。`EncryptionUtil.plainAsEncryptedOutput(OutputFile)` 创建 `BaseEncryptedOutputFile(encryptingOutputFile, EncryptionKeyMetadata.empty())`，表示"该文件无加密"，让 `Parquet.write(EncryptedOutputFile)` 的 else 分支（非 NativeEncryptionOutputFile）调用 `write(file.encryptingOutputFile())`，最终走 plain OutputFile，保持"无加密"语义不变。

## 修改详情

### spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkAppenderFactory.java
**修改目的**：让 Spark 写入路径正确支持 Iceberg 加密（尤其是原生 Parquet/ORC 加密），修复此前因调用 `encryptingOutputFile()` 拆包导致密钥元数据丢失、原生加密文件被静默写成明文的 bug。

**工作逻辑**：

1. **新增 import**：`org.apache.iceberg.encryption.EncryptionUtil`，用于把裸 `OutputFile` 包装为"无加密"的 `EncryptedOutputFile`。

2. **`newAppender(OutputFile, FileFormat)` 改为薄包装**：
   ```java
   public FileAppender<InternalRow> newAppender(OutputFile file, FileFormat fileFormat) {
     return newAppender(EncryptionUtil.plainAsEncryptedOutput(file), fileFormat);
   }
   ```
   原 `OutputFile` 入口现在委托给新的 `EncryptedOutputFile` 入口。`plainAsEncryptedOutput` 用空密钥元数据包装，表示无加密。

3. **新增 `newAppender(EncryptedOutputFile, FileFormat)` 覆写**：把原 `newAppender(OutputFile, ...)` 方法体迁过来，但 `Parquet.write(file)` / `Avro.write(file)` / `ORC.write(file)` 现在接收的是 `EncryptedOutputFile`（而非 `OutputFile`）。每个 write 构建器都有 `EncryptedOutputFile` 重载，会判断是否为 `NativeEncryptionOutputFile` 来决定是否启用原生加密；非原生则调用 `file.encryptingOutputFile()` 走流式加密（AES-GCM）或 plain（无加密）。

4. **`newDataWriter` 修复**：
   ```java
   // 旧：newAppender(file.encryptingOutputFile(), format)
   // 新：newAppender(file, format)
   ```
   注意 `newDataWriter` 仍从 `file.encryptingOutputFile().location()` 取文件路径用于 metadata（这是合理的——路径不带加密信息，加密发生在字节流层或 Parquet 内部）。但写入器入口改为直接传 `file`（`EncryptedOutputFile`）。

5. **`newEqDeleteWriter` 修复**：PARQUET / AVRO / ORC 三个 case 中：
   ```java
   // 旧：Parquet.writeDeletes(file.encryptingOutputFile())
   // 新：Parquet.writeDeletes(file)
   ```
   三处 `file.encryptingOutputFile()` 全部改为 `file`。`Parquet/Avro/ORC.writeDeletes(EncryptedOutputFile)` 重载内部会处理加密。

6. **`newPosDeleteWriter` 修复**：与 `newEqDeleteWriter` 完全对称，PARQUET / AVRO / ORC 三处 `file.encryptingOutputFile()` → `file`。

整体效果：Spark 写入数据文件与删除文件时，`EncryptedOutputFile` 整体（含密钥元数据）一路传递到底层格式写入器；底层写入器据 `NativeEncryptionOutputFile` 判断走原生加密，否则走 `encryptingOutputFile()`（AES-GCM 流式加密或 plain）。无加密表的行为不变（仍写明文），原生加密表的行为从"错误写明文"变为"正确写加密文件"。

## 小结

本提交是 Iceberg 加密能力在 Spark 3.5 写入路径的"契约对齐"修复。改动极小（1 文件、净增 6 行），但修复的是"原生加密表被静默写成明文文件"的高危缺陷。根因是 `SparkAppenderFactory` 旧实现对 `EncryptedOutputFile` 调用 `encryptingOutputFile()` 拆包，丢失了 `NativeEncryptionOutputFile` 携带的密钥元数据。修复方式是覆写 `FileAppenderFactory` 新增的 `newAppender(EncryptedOutputFile, FileFormat)` 默认方法，并把 `newDataWriter`/`newEqDeleteWriter`/`newPosDeleteWriter` 中对 `Parquet/Avro/ORC.writeDeletes` 的调用从传 `OutputFile` 改为传 `EncryptedOutputFile`，让格式写入器自行决定走原生加密还是流式加密。旧的 `newAppender(OutputFile, FileFormat)` 入口通过 `EncryptionUtil.plainAsEncryptedOutput` 包装为"无加密"的 `EncryptedOutputFile` 后委托新方法，保持向后兼容。这一改动与 Iceberg 加密基础设施（`EncryptedOutputFile`、`NativeEncryptionOutputFile`、各格式 write 构建器的 `EncryptedOutputFile` 重载）协同工作，是数据安全落地 Spark 引擎的关键一步。
