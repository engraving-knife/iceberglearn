# 提交 0328：Core, Spark, Flink, Data: Deliver key metadata for encryption of data files (#9359)

## 提交信息

- **序号**：0328 / 4088
- **哈希**：1288eb8e33304e2c5dbf7ea78de9f51f422cfad1
- **短哈希**：1288eb8e3
- **日期**：2024-01-04 08:55:11 -0800
- **作者**：ggershinsky
- **提交说明**：Core, Spark, Flink, Data: Deliver key metadata for encryption of data files (#9359)
- **PR/Issue**：#9359

## 总体目的

Iceberg 早已支持通过 `EncryptionManager` 对数据文件做信封加密（envelope encryption）：用 KMS 解包表主密钥，再生成 per-file 数据密钥，最后用 AES-GCM 把明文输出流包成密文流。此前实现把"加密"完全做在 Iceberg 自己的 IO 层（`StandardEncryptedOutputFile`/`StandardDecryptedInputFile` 把 `OutputFile`/`InputFile` 包装成加密流），数据密钥等 key metadata 由 Iceberg 自行持久化。这种"应用层加密"路径只对 Parquet 走得通（且 `EncryptionUtil.fromTableProperties` 里硬性校验 `fileFormat == PARQUET`），因为 Parquet 文件内部可以加 Iceberg 自定义的 key metadata 头；但它绕过了各文件格式自身的原生加密能力。

Parquet/ORC/Avro 实际上都提供了格式原生加密（format-native encryption）机制——例如 Parquet 的列级加密与模块加密（modular encryption）允许把加密元数据写进文件 footer，并支持外部密钥管理。原生加密相比应用层包裹有几个优势：可以与格式特性（如列级加密、footer 保护区）协同、能被支持该格式的工具直接读取（不必先经过 Iceberg 解密）、性能与兼容性更好。要使用格式原生加密，文件格式 writer 需要直接拿到"数据密钥 + AAD"等信息（而不是一个不透明的 `OutputFile` 包装），因此需要把 key metadata 显式"投递（deliver）"给格式层的 writer builder（如 Parquet 的 `withFileEncryptionKey`/`withAADPrefix`）。

本提交正是为了打通这条"投递"链路：引入一组 `NativeEncryption*` 接口（`NativeEncryptionOutputFile`、`NativeEncryptionInputFile`、`NativeEncryptionKeyMetadata`），让 `StandardEncryptionManager` 返回这些原生加密类型，并把 key metadata 暴露为可读的 `encryptionKey()`/`aadPrefix()`；同时给 `Avro`/`ORC`/`Parquet` 三个格式的 writer API 增加 `write(EncryptedOutputFile)` / `writeData(EncryptedOutputFile)` / `writeDeletes(EncryptedOutputFile)` 重载，让它们能根据传入对象是否为 `NativeEncryptionOutputFile` 决定走原生加密路径还是回退到应用层包裹。各引擎的 `FileAppenderFactory`/`FileWriterFactory`（Data、Flink、Spark）也统一改为直接把 `EncryptedOutputFile` 透传给格式 builder，而不是先调 `encryptingOutputFile()` 取出明文 `OutputFile`——这样格式层才能拿到加密信息。

此外，`EncryptionUtil.fromTableProperties` 里此前对 `fileFormat != PARQUET` 抛 `UnsupportedOperationException` 的硬限制被移除，因为现在 ORC/Avro 的写入路径也能接受 `EncryptedOutputFile`（即便它们暂不支持原生加密，至少能走 plaintext 回退路径而不会一开始就被拒绝）。整个改动既是为 Parquet 原生加密铺路，也是为将来 ORC 原生加密预留接口。

## 如何达成设计目的

核心设计是"接口分层 + 重载分发"：
1. 在 `core/encryption` 下新增三个接口：`NativeEncryptionKeyMetadata`（暴露 `encryptionKey()` 与 `aadPrefix()`）、`NativeEncryptionOutputFile`（继承 `EncryptedOutputFile`，额外提供 `plainOutputFile()` 与 `keyMetadata()` 返回 native 类型）、`NativeEncryptionInputFile`（同时继承 `EncryptedInputFile` 与 `InputFile`，方便读取侧也能拿到 key metadata）。
2. `StandardKeyMetadata` 从实现 `EncryptionKeyMetadata` 改为实现 `NativeEncryptionKeyMetadata`（并把 `encryptionKey()`/`aadPrefix()` 提升为 public）；`StandardEncryptionManager` 的 `encrypt`/`decrypt` 返回类型相应改为 `NativeEncryptionOutputFile`/`NativeEncryptionInputFile`，其内部 `StandardEncryptedOutputFile`/`StandardDecryptedInputFile` 也改为实现这些新接口。
3. 把 `EncryptedOutputFile.plainOutputFile()` 默认方法从 API 接口移除（迁移到 `NativeEncryptionOutputFile` 上作为抽象方法），避免不参与原生加密的实现被迫提供空实现。
4. 给 `Avro`/`ORC`/`Parquet` 的 `WriteBuilder`/`DataWriteBuilder`/`DeleteWriteBuilder` 各加一个 `write(EncryptedOutputFile)`/`writeData(EncryptedOutputFile)`/`writeDeletes(EncryptedOutputFile)` 重载：Parquet 在传入对象是 `NativeEncryptionOutputFile` 时调用 `withFileEncryptionKey`/`withAADPrefix` 走原生加密；Avro/ORC 暂不支持原生加密，仅校验"未携带 key metadata"后取明文 `OutputFile` 回退。读取侧 `Parquet.read(InputFile)`/`ORC.read(InputFile)` 也做相应分发。
5. 给 `FileAppenderFactory` 加默认方法 `newAppender(EncryptedOutputFile, FileFormat)`，默认实现回退到老的 `newAppender(outputFile.encryptingOutputFile(), ...)`，但被 Generic/Flink/Spark 的 appender factory 覆写为直接走 `Parquet/Avro/ORC.write(EncryptedOutputFile)` 路径。同时所有 `newDataWriter`/`newEqualityDeleteWriter`/`newPositionDeleteWriter` 都改为把 `EncryptedOutputFile` 整体传给格式 builder（不再先 `.encryptingOutputFile()`）。
6. 新增 `EncryptionUtil.plainAsEncryptedOutput(OutputFile)` 工具方法，把一个普通 `OutputFile` 包成无 key metadata 的 `BaseEncryptedOutputFile`，用于 appender factory 的旧 `newAppender(OutputFile, FileFormat)` 入口向后兼容。

## 修改详情

### `api/src/main/java/org/apache/iceberg/encryption/EncryptedOutputFile.java`

**修改目的**：从 API 接口移除 `plainOutputFile()` 默认方法。

**工作逻辑**：原接口末尾有 `default OutputFile plainOutputFile() { throw new UnsupportedOperationException("Not implemented"); }`，意图是给原生加密实现提供"取底层明文输出文件"的钩子，但放在 API 接口上会让所有 `EncryptedOutputFile` 实现都背上一个几乎永远抛异常的默认方法。本提交把它从 API 接口删除，迁移到新增的 `NativeEncryptionOutputFile` 上作为抽象方法。这是 API 收窄：只有声明支持原生加密的实现才需要提供 `plainOutputFile()`。

### `core/src/main/java/org/apache/iceberg/encryption/NativeEncryptionKeyMetadata.java`（新增）

**修改目的**：定义"原生加密专用"的 key metadata 接口，把加密密钥与 AAD 暴露给格式层。

**工作逻辑**：`public interface NativeEncryptionKeyMetadata extends EncryptionKeyMetadata`，新增两个方法：`ByteBuffer encryptionKey()`（数据密钥）与 `ByteBuffer aadPrefix()`（附加认证数据）。继承自 `EncryptionKeyMetadata`，因此仍可被当作通用 key metadata 使用，但只有 `NativeEncryption*` 路径上的代码会调用这两个新方法。这样既兼容老接口，又显式标注"可被原生加密消费"。

### `core/src/main/java/org/apache/iceberg/encryption/NativeEncryptionOutputFile.java`（新增）

**修改目的**：定义"原生加密专用"的输出文件接口。

**工作逻辑**：`public interface NativeEncryptionOutputFile extends EncryptedOutputFile`，两个方法：
- `@Override NativeEncryptionKeyMetadata keyMetadata();`（把父接口返回类型收窄为 native 子类型，协变返回）。
- `OutputFile plainOutputFile();`（从 `EncryptedOutputFile` API 接口迁过来的方法，但这里变成抽象方法，要求实现必须提供底层明文输出文件，原生加密 writer 通过它写出明文流再由格式层加密）。

### `core/src/main/java/org/apache/iceberg/encryption/NativeEncryptionInputFile.java`（新增）

**修改目的**：定义"原生加密专用"的输入文件接口，使读取侧也能拿到 key metadata。

**工作逻辑**：`public interface NativeEncryptionInputFile extends EncryptedInputFile, InputFile`——同时继承 `EncryptedInputFile`（提供 `keyMetadata()`）与 `InputFile`（可被当作普通输入文件直接传给格式 reader）。`@Override NativeEncryptionKeyMetadata keyMetadata();` 协变收窄返回类型。这样 Parquet reader 在收到一个 `NativeEncryptionInputFile` 时，可以同时拿到密文 InputFile 与密钥/AAD，调用 `withFileEncryptionKey`/`withAADPrefix` 走原生解密路径。

### `core/src/main/java/org/apache/iceberg/encryption/EncryptionUtil.java`

**修改目的**：(1) 移除 `fromTableProperties` 中对非 Parquet 格式直接抛异常的限制；(2) 新增 `plainAsEncryptedOutput` 工具方法用于向后兼容。

**工作逻辑**：
- 删除原来从 `tableProperties` 读 `DEFAULT_FILE_FORMAT`、判断 `!= PARQUET` 就抛 `UnsupportedOperationException` 的整段代码。这是因为新机制下 ORC/Avro 的写入路径也能接受 `EncryptedOutputFile`（在格式 builder 内部判断不是 native 时回退 plaintext），不应在工厂方法这层提前拒绝。注意 `StandardEncryptionManager` 自身仍只产生 Parquet 友好的 key metadata，但至少 ORC/Avro 调用方不会一开始就被阻挡。
- 移除对应的 `import org.apache.iceberg.FileFormat;`，新增 `import org.apache.iceberg.io.OutputFile;`。
- 新增 `public static EncryptedOutputFile plainAsEncryptedOutput(OutputFile encryptingOutputFile) { return new BaseEncryptedOutputFile(encryptingOutputFile, EncryptionKeyMetadata.empty()); }`。该方法把一个普通明文 `OutputFile` 包成 `keyMetadata` 为空的 `EncryptedOutputFile`，给 appender factory 的旧 `newAppender(OutputFile, FileFormat)` 入口使用——老入口拿到的是裸 `OutputFile`，但格式层的新重载要求 `EncryptedOutputFile`，因此用这个工具包一层（key metadata 为空表示不加密、走 plaintext）。

### `core/src/main/java/org/apache/iceberg/encryption/StandardEncryptionManager.java`

**修改目的**：让 `StandardEncryptionManager` 的 `encrypt`/`decrypt` 返回 native 加密类型，使其 key metadata 可被格式层消费。

**工作逻辑**：
- `encrypt(OutputFile)` 返回类型从 `EncryptedOutputFile` 改为 `NativeEncryptionOutputFile`；`decrypt(EncryptedInputFile)` 返回类型从 `InputFile` 改为 `NativeEncryptionInputFile`。
- 内部类 `StandardEncryptedOutputFile implements NativeEncryptionOutputFile`（原来是 `implements EncryptedOutputFile`），相应地 `keyMetadata()` 返回类型协变收窄为 `StandardKeyMetadata`。
- 内部类 `StandardDecryptedInputFile implements NativeEncryptionInputFile`（原来是 `implements InputFile`）。新增 `@Override public InputFile encryptedInputFile()` 方法，返回 `encryptedInputFile.encryptedInputFile()`——把底层密文 InputFile 暴露出来，给格式层读取密文用。`keyMetadata()` 返回类型从 `private StandardKeyMetadata` 提升为 `public StandardKeyMetadata`（因为接口要求 public），并加 `@Override`。
- `decrypt(Iterable<...>)` 上原本注释"Bulk decrypt is only applied to data files. Returning source input files for parquet."被删除，逻辑不变——这是清理过时注释。

### `core/src/main/java/org/apache/iceberg/encryption/StandardKeyMetadata.java`

**修改目的**：让 `StandardKeyMetadata` 实现 `NativeEncryptionKeyMetadata`，把密钥与 AAD 暴露出来。

**工作逻辑**：类签名从 `implements EncryptionKeyMetadata, IndexedRecord` 改为 `implements NativeEncryptionKeyMetadata, IndexedRecord`。`encryptionKey()` 与 `aadPrefix()` 原本是包级可见（无修饰符），现在加上 `@Override` 提升为 `public`——因为接口方法是 public。这两个方法返回 `ByteBuffer`，分别对应数据密钥与 AAD 前缀。其余逻辑不变（仍按 V1 schema 序列化为 Avro IndexedRecord）。

### `core/src/main/java/org/apache/iceberg/io/FileAppenderFactory.java`

**修改目的**：在 appender factory 接口上新增"接受 `EncryptedOutputFile` 的 `newAppender` 重载"，作为新链路的入口。

**工作逻辑**：新增 default 方法：
```java
default FileAppender<T> newAppender(EncryptedOutputFile outputFile, FileFormat fileFormat) {
  return newAppender(outputFile.encryptingOutputFile(), fileFormat);
}
```
默认实现回退到老的 `newAppender(OutputFile, FileFormat)`（即取明文输出文件），保证未覆写的实现仍能工作；各引擎 appender factory 会覆写该方法，直接走格式层的 `write(EncryptedOutputFile)` 路径，让加密信息能传到格式层。同时新增 `import org.apache.iceberg.encryption.EncryptedOutputFile;`。

### `core/src/main/java/org/apache/iceberg/avro/Avro.java`

**修改目的**：给 Avro 的三个 writer builder 各加 `EncryptedOutputFile` 重载，明确 Avro 不支持加密。

**工作逻辑**：新增三个静态方法 `write(EncryptedOutputFile)`、`writeData(EncryptedOutputFile)`、`writeDeletes(EncryptedOutputFile)`，每个方法体都执行 `Preconditions.checkState(file.keyMetadata() == null || file.keyMetadata() == EncryptionKeyMetadata.EMPTY, "Avro encryption is not supported")`，然后委托给既有的 `write(file.encryptingOutputFile())` 等 plaintext 方法。也就是说 Avro 路径只接受"未加密"的 `EncryptedOutputFile`，如果携带了真实 key metadata 会直接失败。这是显式声明 Avro 的能力边界，避免上层无感知地传了加密文件下来却被静默当明文写。

### `orc/src/main/java/org/apache/iceberg/orc/ORC.java`

**修改目的**：给 ORC 三个 writer builder 与 reader 各加 `EncryptedOutputFile`/`NativeEncryption*` 重载，明确 ORC 暂不支持原生加密。

**工作逻辑**：
- 新增 `write(EncryptedOutputFile)`/`writeData(EncryptedOutputFile)`/`writeDeletes(EncryptedOutputFile)` 三个方法，每个方法体执行 `Preconditions.checkState(!(file instanceof NativeEncryptionOutputFile), "Native ORC encryption is not supported")`，然后委托给既有 `write(file.encryptingOutputFile())`。即 ORC 接受"非 native 的 `EncryptedOutputFile`"（也就是 `plainAsEncryptedOutput` 包出来的那种、key metadata 为空），但拒绝 native 加密对象。这与 Avro 的策略略有差异（Avro 检查 key metadata 是否为空，ORC 检查是否为 native 实例），但目的一致：明确不支持原生加密。
- 在 `read(InputFile)` 方法开头新增 `Preconditions.checkState(!(file instanceof NativeEncryptionInputFile), "Native ORC encryption is not supported")`，读取侧也拒绝 native 加密输入。
- 新增 import `EncryptedOutputFile`、`NativeEncryptionInputFile`、`NativeEncryptionOutputFile`。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java`

**修改目的**：给 Parquet 三个 writer builder 与 reader 加 `EncryptedOutputFile`/`NativeEncryption*` 重载，真正实现"原生加密投递"。

**工作逻辑**：
- `write(EncryptedOutputFile file)`、`writeData(EncryptedOutputFile file)`、`writeDeletes(EncryptedOutputFile file)` 三个新方法采用相同的分发模式：
  ```java
  if (file instanceof NativeEncryptionOutputFile) {
    NativeEncryptionOutputFile nativeFile = (NativeEncryptionOutputFile) file;
    return write(nativeFile.plainOutputFile())
        .withFileEncryptionKey(nativeFile.keyMetadata().encryptionKey())
        .withAADPrefix(nativeFile.keyMetadata().aadPrefix());
  } else {
    return write(file.encryptingOutputFile());
  }
  ```
  即：如果是 native 加密对象，取底层明文 `OutputFile` 与 `encryptionKey`/`aadPrefix`，调用既有 WriteBuilder 的 `withFileEncryptionKey`/`withAADPrefix` 把密钥交给 Parquet 原生加密模块；否则回退到老的 `write(OutputFile)` 路径（应用层加密或明文）。这是整个提交"投递"动作的核心落点——key metadata 通过这条路径被真正传给了 Parquet writer。
- `read(InputFile file)` 同样改为分发：
  ```java
  if (file instanceof NativeEncryptionInputFile) {
    NativeEncryptionInputFile nativeFile = (NativeEncryptionInputFile) file;
    return new ReadBuilder(nativeFile.encryptedInputFile())
        .withFileEncryptionKey(nativeFile.keyMetadata().encryptionKey())
        .withAADPrefix(nativeFile.keyMetadata().aadPrefix());
  } else {
    return new ReadBuilder(file);
  }
  ```
  读取侧取底层密文 InputFile（`encryptedInputFile()`）+ 密钥/AAD，让 Parquet reader 自行解密。
- 新增 import `EncryptedOutputFile`、`NativeEncryptionInputFile`、`NativeEncryptionOutputFile`（保留 `EncryptionKeyMetadata` import）。

### `data/src/main/java/org/apache/iceberg/data/BaseFileWriterFactory.java`

**修改目的**：把 `newDataWriter`/`newEqualityDeleteWriter`/`newPositionDeleteWriter` 中调用 `Avro/Parquet/ORC.write*(...)` 的实参从 `outputFile`（明文 `OutputFile`）改为 `file`（`EncryptedOutputFile`），让加密信息能传到格式层。

**工作逻辑**：三类 writer 的三个分支（AVRO/PARQUET/ORC）原本先 `OutputFile outputFile = file.encryptingOutputFile();` 再调用 `Avro.writeData(outputFile)` 等，现在删掉这行赋值、直接调用 `Avro.writeData(file)` 等。`EncryptionKeyMetadata keyMetadata = file.keyMetadata();` 那行保留（后续 `DataWriter`/`DeleteWriter` 构造时仍需要把 key metadata 写入 manifest）。这一改动让格式 builder 收到的是 `EncryptedOutputFile`，从而触发上一节描述的 native 加密分发逻辑。同时移除不再使用的 `import org.apache.iceberg.io.OutputFile;`。

### `data/src/main/java/org/apache/iceberg/data/GenericAppenderFactory.java`

**修改目的**：覆写新的 `newAppender(EncryptedOutputFile, FileFormat)`，让 Generic 路径走格式层 `write(EncryptedOutputFile)`；同时把 equality/position delete writer 的实参也改为直接传 `EncryptedOutputFile`。

**工作逻辑**：
- 旧 `newAppender(OutputFile outputFile, FileFormat fileFormat)` 改为委托给新方法：`return newAppender(EncryptionUtil.plainAsEncryptedOutput(outputFile), fileFormat);`。这样老入口仍可用，且会走新链路（key metadata 为空、回退 plaintext）。
- 新增 `@Override public FileAppender<Record> newAppender(EncryptedOutputFile encryptedOutputFile, FileFormat fileFormat)`：内部 `switch` 三个分支分别调用 `Avro.write(encryptedOutputFile)`、`Parquet.write(encryptedOutputFile)`、`ORC.write(encryptedOutputFile)`——直接传 `EncryptedOutputFile`，触发格式层分发。
- `newDataWriter` 中 `newAppender(file.encryptingOutputFile(), format)` 改为 `newAppender(file, format)`（让 native 加密信息进入 appender）。
- `newEqualityDeleteWriter`/`newPosDeleteWriter` 中 `Avro.writeDeletes(file.encryptingOutputFile())`/`ORC.writeDeletes(file.encryptingOutputFile())`/`Parquet.writeDeletes(file.encryptingOutputFile())` 全部改为 `writeDeletes(file)`，让加密信息传到格式层。
- 新增 `import org.apache.iceberg.encryption.EncryptionUtil;`。

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkAppenderFactory.java`

**修改目的**：与 `GenericAppenderFactory` 同构改造，让 Flink 写入路径也走格式层 `write(EncryptedOutputFile)`。

**工作逻辑**：
- 旧 `newAppender(OutputFile, FileFormat)` 委托 `newAppender(EncryptionUtil.plainAsEncryptedOutput(outputFile), format)`。
- 新增 `@Override public FileAppender<RowData> newAppender(EncryptedOutputFile outputFile, FileFormat format)`。
- `newDataWriter` 中 `newAppender(file.encryptingOutputFile(), format)` 改为 `newAppender(file, format)`。
- `newEqualityDeleteWriter`/`newPosDeleteWriter` 中 `Avro/ORC/Parquet.writeDeletes(outputFile.encryptingOutputFile())` 全部改为 `writeDeletes(outputFile)`。
- 新增 `import org.apache.iceberg.encryption.EncryptionUtil;`。
- 注意：本次提交仅修改 `flink/v1.17` 目录下的 `FlinkAppenderFactory`，因为 Flink 1.16/1.18 的对应文件由后续提交单独同步（Iceberg 多版本维护策略）。从 `git log` 看 v1.16 的 `FlinkAppenderFactory` 由独立的"从 1.17 拷贝"提交维护。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkAppenderFactory.java`

**修改目的**：与 `GenericAppenderFactory`/`FlinkAppenderFactory` 同构改造，让 Spark 写入路径走格式层 `write(EncryptedOutputFile)`。

**工作逻辑**：
- 旧 `newAppender(OutputFile file, FileFormat fileFormat)` 委托 `newAppender(EncryptionUtil.plainAsEncryptedOutput(file), fileFormat)`。
- 新增 `@Override public FileAppender<InternalRow> newAppender(EncryptedOutputFile file, FileFormat fileFormat)`。
- `newDataWriter` 中 `newAppender(file.encryptingOutputFile(), format)` 改为 `newAppender(file, format)`。
- `newEqualityDeleteWriter`/`newPosDeleteWriter` 中 `Avro/ORC/Parquet.writeDeletes(file.encryptingOutputFile())` 全部改为 `writeDeletes(file)`。
- 新增 `import org.apache.iceberg.encryption.EncryptionUtil;`。
- 注意：本次提交仅修改 `spark/v3.4` 目录下的 `SparkAppenderFactory`，spark v3.5 的对应文件由后续提交单独同步。

## 小结

这个提交为 Iceberg 的加密体系打通了"把 key metadata 投递到格式层"的链路，使 Parquet 能使用格式原生加密（`withFileEncryptionKey`/`withAADPrefix`），并为 ORC/Avro 将来支持原生加密预留了统一的接口形状。新增的 `NativeEncryptionKeyMetadata`/`NativeEncryptionOutputFile`/`NativeEncryptionInputFile` 三个接口把"可被格式层消费"的加密元数据显式建模出来，`StandardEncryptionManager` 与 `StandardKeyMetadata` 相应升级实现这些接口；`Avro`/`ORC`/`Parquet` 的 writer/reader API 通过新增 `EncryptedOutputFile`/`NativeEncryption*` 重载实现分发（Parquet 走原生、Avro/ORC 显式拒绝 native 并回退明文）；各引擎 appender factory 统一改为透传 `EncryptedOutputFile`。改动横跨 core/api/data/flink/spark/orc/parquet 多个模块，但概念一致、改动模式高度对称，为后续启用 Parquet 列级/模块加密奠定了基础。
