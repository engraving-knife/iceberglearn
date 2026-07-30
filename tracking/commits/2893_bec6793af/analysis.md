# 提交 2893：Encryption clean up (#14579)

## 提交信息

- **序号**：2893 / 4088
- **哈希**：bec6793af822cb791fa5975b41b8936631556c46
- **短哈希**：bec6793af
- **日期**：2025-11-19 09:38:04 -0800
- **作者**：ggershinsky
- **提交说明**：Encryption clean up (#14579)
- **PR/Issue**：#14579

## 总体目的

这是 Iceberg 加密模块的一次清理与健壮性改进提交，主要解决几个遗留问题和潜在 bug：

1. **`EncryptingFileIO` 中的冗余内部类**：原有一个私有的 `EmptyKeyMetadata` 类用于表示空密钥元数据，但 `EncryptionKeyMetadata` 接口本身已提供 `empty()` 工厂方法，`EmptyKeyMetadata` 是重复实现，应删除并改用统一入口。

2. **`AesGcmInputFile` 的文件长度安全问题**：原构造函数 `AesGcmInputFile(InputFile, byte[], byte[])` 不接收文件长度，而是在 `encryptedLength()` 中通过 `sourceFile.getLength()` 读取。但对于加密文件，从底层 InputFile 读取长度可能触发不必要的远程读取或返回不安全的值。正确做法是要求调用方显式传入加密文件长度，避免运行时不确定行为。同时 `AesGcmOutputFile.toInputFile()` 因为不知道文件长度而不安全，应直接抛异常。

3. **写文件时未把文件长度写入密钥元数据**：`DataWriter`、`PositionDeleteWriter`、`EqualityDeleteWriter` 在写完文件后，把 `keyMetadata` 直接传给 `DataFile`，但没有把文件长度写入密钥元数据。密钥元数据中包含文件长度是解密时验证完整性的重要信息（GCM 模式下文件长度关系到认证标签的覆盖范围），缺失会导致解密端无法正确校验。需要调用 `EncryptionUtil.setFileLength(keyMetadata, fileLength)` 把长度写入元数据。

4. **`ManifestListWriter` 中的密钥元数据处理顺序问题**：原代码先调用 `manifestListKeyMetadata.copyWithLength(writer.length())`（返回值未赋值，相当于丢弃），再 `addManifestListKeyMetadata(manifestListKeyMetadata)`（用的是没带 length 的旧对象）。这是明显的 bug——`copyWithLength` 的返回值被丢弃了。应改为先 copyWithLength 再传入。

5. **`StandardEncryptionManager` 缺少密钥类型校验**：`getManifestListKeyMetadata` 方法在解密 manifest list 密钥时，没有校验拿到的元数据是否是 manifest list 密钥而非表级 KEK（key encryption key），可能误用导致解密失败。需要加校验。

6. **废弃 API 标记**：`NativeFileCryptoParameters` 和 `NativelyEncryptedFile` 是即将在 2.0.0 移除的旧 API，应标注 `@Deprecated`。

7. **`HiveTableOperations` 中 DEK 长度解析**：原代码手动 `Integer.parseInt(dekLength)`，应改用 `PropertyUtil.propertyAsInt` 更安全。

8. **测试适配**：`TestGcmStreams` 和 `TestEncryptedAvroFileSplit` 需要适配新的 `AesGcmInputFile` 构造函数（必须传文件长度）。

## 如何达成设计目的

针对上述问题逐项处理：
- 删除 `EmptyKeyMetadata`，改用 `EncryptionKeyMetadata.empty()`。
- 在 `AesGcmInputFile` 标记旧构造函数为 `@Deprecated`，并在 `encryptedLength()` 为 null 时抛异常而非回退读 sourceFile；`AesGcmOutputFile.toInputFile()` 直接抛 `IllegalStateException`。
- 新增 `EncryptionUtil.setFileLength(ByteBuffer, long)` 工具方法，在三个 Writer（Data/Position/Equality）中调用它把文件长度写入密钥元数据。
- 修正 `ManifestListWriter.toManifestListFile` 的调用顺序：先 `copyWithLength` 再 `addManifestListKeyMetadata`。
- 在 `StandardEncryptionManager.getManifestListKeyMetadata` 加校验：如果元数据的 `encryptedById` 等于 `tableKeyId`，说明这是 KEK 而非 manifest list 密钥，抛异常。
- 给 `NativeFileCryptoParameters` 和 `NativelyEncryptedFile` 加 `@Deprecated` 注解并注明 2.0.0 移除。
- `HiveTableOperations` 改用 `PropertyUtil.propertyAsInt` 解析 DEK 长度。
- 测试改用带 length 的 `AesGcmInputFile` 构造函数，并在需要时显式调用 `copyWithLength(writer.length())`。

## 修改详情

### `api/src/main/java/org/apache/iceberg/encryption/EncryptingFileIO.java` (+1/-19 lines)

**修改目的**：删除冗余的 `EmptyKeyMetadata` 内部类。

**工作逻辑**：
`toKeyMetadata` 方法改为使用统一入口：
```java
- return buffer != null ? new SimpleKeyMetadata(buffer) : EmptyKeyMetadata.get();
+ return buffer != null ? new SimpleKeyMetadata(buffer) : EncryptionKeyMetadata.empty();
```
删除整个 `EmptyKeyMetadata` 私有静态内部类（约 16 行），它原本用单例模式实现 `buffer()` 返回 null、`copy()` 返回自身，与 `EncryptionKeyMetadata.empty()` 语义重复。

### `core/src/main/java/org/apache/iceberg/ManifestListWriter.java` (+5/-5 lines)

**修改目的**：修复 `copyWithLength` 返回值被丢弃的 bug，并修正类型转换。

**工作逻辑**：
- 类型修正：`NativeEncryptionOutputFile` 改为更通用的 `EncryptedOutputFile`，`manifestListKeyMetadata` 类型从 `NativeEncryptionOutputFile.keyMetadata()` 强转为 `NativeEncryptionKeyMetadata`：
  ```java
  EncryptedOutputFile encryptedFile = this.standardEncryptionManager.encrypt(file);
  this.outputFile = encryptedFile.encryptingOutputFile();
  this.manifestListKeyMetadata = (NativeEncryptionKeyMetadata) encryptedFile.keyMetadata();
  ```
- 修复 `toManifestListFile` 的调用顺序 bug：
  ```java
  - manifestListKeyMetadata.copyWithLength(writer.length());
  - String manifestListKeyID = standardEncryptionManager.addManifestListKeyMetadata(manifestListKeyMetadata);
  + String manifestListKeyID = standardEncryptionManager.addManifestListKeyMetadata(
  +     manifestListKeyMetadata.copyWithLength(writer.length()));
  ```
  原代码 `copyWithLength` 的返回值被丢弃，`addManifestListKeyMetadata` 用的是不带 length 的旧对象。修正后把带 length 的副本传入。

### `core/src/main/java/org/apache/iceberg/deletes/EqualityDeleteWriter.java` (+3/-1 line)

**修改目的**：把文件长度写入密钥元数据。

**工作逻辑**：
```java
- .withEncryptionKeyMetadata(keyMetadata)
+ .withEncryptionKeyMetadata(
+     EncryptionUtil.setFileLength(keyMetadata, appender.length()))
```
写完文件后，`appender.length()` 是实际文件长度，通过 `EncryptionUtil.setFileLength` 写入密钥元数据，使解密端能校验文件完整性。

### `core/src/main/java/org/apache/iceberg/deletes/PositionDeleteWriter.java` (+3/-1 line)

**修改目的**：同上，把文件长度写入密钥元数据。

**工作逻辑**：与 `EqualityDeleteWriter` 完全相同的改法：
```java
- .withEncryptionKeyMetadata(keyMetadata)
+ .withEncryptionKeyMetadata(
+     EncryptionUtil.setFileLength(keyMetadata, appender.length()))
```

### `core/src/main/java/org/apache/iceberg/encryption/AesGcmInputFile.java` (+6/-1 line)

**修改目的**：标记不安全的旧构造函数为废弃，并在缺少文件长度时抛异常。

**工作逻辑**：
- 旧构造函数加 `@Deprecated` 注解并注明 2.0.0 移除：
  ```java
  /** @deprecated will be removed in 2.0.0 This API does not receive file length, and is therefore not safe */
  @Deprecated
  public AesGcmInputFile(InputFile sourceFile, byte[] dataKey, byte[] fileAADPrefix) {
    this(sourceFile, dataKey, fileAADPrefix, null);
  }
  ```
- `encryptedLength()` 在 length 为 null 时不再回退读 `sourceFile.getLength()`，而是抛异常：
  ```java
  - this.encryptedLength = sourceFile.getLength();
  + throw new IllegalArgumentException("File length is null");
  ```
  这强制调用方必须通过新构造函数传入文件长度，避免不安全的运行时读取。

### `core/src/main/java/org/apache/iceberg/encryption/AesGcmOutputFile.java` (+1/-1 line)

**修改目的**：移除不安全的 `toInputFile()` 实现。

**工作逻辑**：
```java
- return new AesGcmInputFile(targetFile.toInputFile(), dataKey, fileAADPrefix);
+ throw new IllegalStateException("File length unknown, creating an AesGcmInputFile is not safe");
```
`OutputFile.toInputFile()` 时文件长度未知，无法安全构造 `AesGcmInputFile`，直接抛异常阻止不安全用法。

### `core/src/main/java/org/apache/iceberg/encryption/EncryptionUtil.java` (+8/-0 lines)

**修改目的**：新增 `setFileLength` 工具方法，供 Writer 调用。

**工作逻辑**：
```java
public static ByteBuffer setFileLength(ByteBuffer keyMetadata, long fileLength) {
  if (keyMetadata == null) {
    return null;
  }
  return StandardKeyMetadata.parse(keyMetadata).copyWithLength(fileLength).buffer();
}
```
解析密钥元数据为 `StandardKeyMetadata`，调用 `copyWithLength` 写入文件长度，再转回 ByteBuffer。null 入参直接返回 null（无加密的情况）。

### `core/src/main/java/org/apache/iceberg/encryption/NativeFileCryptoParameters.java` (+3/-0 lines)

**修改目的**：标记即将移除的旧 API。

**工作逻辑**：
```java
/** @deprecated will be removed in 2.0.0 */
@Deprecated
public class NativeFileCryptoParameters { ... }
```

### `core/src/main/java/org/apache/iceberg/encryption/NativelyEncryptedFile.java` (+3/-0 lines)

**修改目的**：标记即将移除的旧接口。

**工作逻辑**：
```java
/** @deprecated will be removed in 2.0.0 */
@Deprecated
public interface NativelyEncryptedFile { ... }
```

### `core/src/main/java/org/apache/iceberg/encryption/StandardEncryptionManager.java` (+5/-0 lines)

**修改目的**：在 `getManifestListKeyMetadata` 中增加密钥类型校验，防止误用 KEK。

**工作逻辑**：
```java
if (encryptedKeyMetadata.encryptedById().equals(tableKeyId)) {
  throw new IllegalArgumentException(
      manifestListKeyID + " is a key encryption key, not manifest list key metadata");
}
```
如果拿到的元数据是用表级 KEK 加密的，说明它本身是 KEK 而非 manifest list 数据密钥，继续解密会出错，提前抛异常给出明确错误信息。

### `core/src/main/java/org/apache/iceberg/io/DataWriter.java` (+3/-1 line)

**修改目的**：把文件长度写入密钥元数据（与两个 DeleteWriter 相同）。

**工作逻辑**：
```java
- .withEncryptionKeyMetadata(keyMetadata)
+ .withEncryptionKeyMetadata(
+     EncryptionUtil.setFileLength(keyMetadata, appender.length()))
```

### `core/src/test/java/org/apache/iceberg/avro/TestEncryptedAvroFileSplit.java` (+16/-8 lines)

**修改目的**：适配新的密钥元数据处理方式——显式写入文件长度。

**工作逻辑**：
- 写文件的 try-with-resources 改为先 build writer、写数据、手动 close，以便拿到 `writer.length()`：
  ```java
  FileAppender<Object> writer = Avro.write(eOut)...build();
  // 写数据
  writer.close();
  NativeEncryptionKeyMetadata kmWithLength =
      ((NativeEncryptionKeyMetadata) eOut.keyMetadata()).copyWithLength(writer.length());
  EncryptedInputFile encryptedIn = EncryptedFiles.encryptedInput(out.toInputFile(), kmWithLength);
  ```
  显式调用 `copyWithLength` 把文件长度写入密钥元数据，与产品代码的新行为一致。

### `core/src/test/java/org/apache/iceberg/encryption/TestGcmStreams.java` (+30/-18 lines)

**修改目的**：适配 `AesGcmInputFile` 必须传入文件长度的新要求。

**工作逻辑**：
所有 `new AesGcmInputFile(Files.localInput(testFile), key, aadPrefix)` 调用改为传入第四个参数 `encryptedStream.storedLength()`：
```java
AesGcmInputFile decryptedFile = new AesGcmInputFile(
    Files.localInput(testFile), key, aadPrefix, encryptedStream.storedLength());
```
同时多处写文件的 try-with-resources 改为先写后 close，以便从 `encryptedStream.storedLength()` 拿到加密后的文件长度传给 InputFile。涉及空文件、小文件、大文件、随机读等多个测试场景。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveTableOperations.java` (+4/-3 lines)

**修改目的**：用安全的属性解析工具替代手动 parseInt。

**工作逻辑**：
```java
- String dekLength = tableProperties.get(TableProperties.ENCRYPTION_DEK_LENGTH);
- encryptionDekLength = (dekLength == null)
-     ? TableProperties.ENCRYPTION_DEK_LENGTH_DEFAULT
-     : Integer.parseInt(dekLength);
+ encryptionDekLength = PropertyUtil.propertyAsInt(
+     tableProperties,
+     TableProperties.ENCRYPTION_DEK_LENGTH,
+     TableProperties.ENCRYPTION_DEK_LENGTH_DEFAULT);
```
`PropertyUtil.propertyAsInt` 统一处理 null 和格式解析，避免手动 `Integer.parseInt` 在格式错误时抛 `NumberFormatException` 的风险。

## 总结

这是一次加密模块的综合清理提交，包含一个明确的 bug 修复（`ManifestListWriter` 中 `copyWithLength` 返回值被丢弃）、一个安全加固（`AesGcmInputFile` 强制要求文件长度，移除不安全的 `toInputFile`）、一个完整性增强（三个 Writer 把文件长度写入密钥元数据，供解密端校验）、一个防御性校验（`StandardEncryptionManager` 区分 KEK 与 manifest list 密钥）、若干 API 废弃标记和工具方法替换。整体提升了加密模块的健壮性和代码质量，修复了潜在的解密完整性校验缺失问题，属于重要的安全相关改进。
