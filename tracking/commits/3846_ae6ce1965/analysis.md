# 提交 3846：Core: Preserve DV encryption metadata in merges (#15911)

## 提交信息

- **序号**：3846 / 4088
- **哈希**：ae6ce1965d5f0831de10a55c5abeceb125c69ef4
- **短哈希**：ae6ce1965
- **日期**：2026-06-08 22:46:48 -0600
- **作者**：Manu Zhang
- **提交说明**：Core: Preserve DV encryption metadata in merges (#15911)
- **PR/Issue**：#15911

## 总体目的

本提交修复了删除向量（Deletion Vector, DV）在合并时丢失加密元数据的问题。Iceberg 支持原生加密（native encryption），数据文件和删除文件都可以加密存储，加密密钥的元数据（encryption key metadata）需要随文件一起记录，以便读取时能重构加密密钥。

当多个 DV 文件被合并为一个时（例如 `RowDelta` 操作中检测到重复 DV 时会触发合并），合并后的新 DV 文件没有正确携带加密密钥元数据。这导致读取端无法解密合并后的 DV 文件，从而无法正确应用删除操作。

本提交通过在 `BaseDVFileWriter` 中正确传播加密输出元数据，确保合并后的 DV 文件包含正确的加密密钥元数据。特别地，对于 `NativeEncryptionKeyMetadata`，还会将最终文件长度（file length）注入到元数据中，因为原生加密密钥元数据可能依赖于文件长度进行密钥派生。

## 如何达成设计目的

整体设计分四步：

1. **将 `BaseDVFileWriter` 从使用 `OutputFile` 升级为使用 `EncryptedOutputFile`**：这样写入器能获取加密密钥元数据。旧构造器被标记为 `@Deprecated`，内部委托给新构造器并通过 `EncryptedFiles.plainAsEncryptedOutput()` 包装普通文件。

2. **在写入时提取密钥元数据并传递给 `createDV()`**：`close()` 方法中获取 `EncryptedOutputFile` 的 `keyMetadata()`，传给 `createDV()` 方法，后者在构建 `DeleteFile` 时调用 `withEncryptionKeyMetadata()` 设置元数据。

3. **处理 `NativeEncryptionKeyMetadata` 的文件长度**：新增 `encryptionKeyMetadata()` 私有方法，如果是 `NativeEncryptionKeyMetadata` 则调用 `copyWithLength(fileSizeInBytes)` 创建包含文件长度的副本，因为原生加密密钥元数据需要文件长度来重构密钥。

4. **`DVUtil` 和 `Deletes` 工具方法更新**：`DVUtil.mergeDVIndexes()` 方法现在使用 `EncryptingFileIO` 创建加密输出文件；新增 `Deletes.writeDVs()` 工厂方法创建 `BaseDVFileWriter`。

## 修改详情

### `core/src/main/java/org/apache/iceberg/DVUtil.java` (+14/-2 lines)

**修改目的**：在 DV 合并时使用加密输出文件。

**工作逻辑**：
`mergeDVIndexes()` 方法中，检测 `FileIO` 是否为 `EncryptingFileIO`，如果是则创建加密输出文件，否则包装为普通加密输出：
```java
EncryptedOutputFile dvOutputFile =
    fileIO instanceof EncryptingFileIO encryptingFileIO
        ? encryptingFileIO.newEncryptingOutputFile(dvOutputLocation)
        : EncryptedFiles.plainAsEncryptedOutput(fileIO.newOutputFile(dvOutputLocation));
try (DVFileWriter dvFileWriter = Deletes.writeDVs(dvOutputFile, path -> null)) {
```

### `core/src/main/java/org/apache/iceberg/deletes/BaseDVFileWriter.java` (+42/-2 lines)

**修改目的**：核心修复，保留加密元数据。

**工作逻辑**：

1. 字段类型从 `Supplier<OutputFile>` 改为 `Supplier<EncryptedOutputFile>`。

2. 新增内部构造器接受 `Supplier<EncryptedOutputFile>`，旧构造器标记 `@Deprecated` 并委托。

3. `close()` 方法中，获取 `EncryptedOutputFile` 和 `keyMetadata`，使用 `Puffin.write(outputFile.encryptingOutputFile())` 创建写入器：
```java
EncryptedOutputFile outputFile = dvOutputFile.get();
EncryptionKeyMetadata keyMetadata = outputFile.keyMetadata();
PuffinWriter writer =
    Puffin.write(outputFile.encryptingOutputFile())
        .createdBy(IcebergBuild.fullVersion())
        .build();
```

4. `createDV()` 方法新增 `EncryptionKeyMetadata` 参数，调用 `withEncryptionKeyMetadata()` 设置元数据。

5. 新增 `encryptionKeyMetadata()` 方法处理 `NativeEncryptionKeyMetadata` 的文件长度：
```java
private ByteBuffer encryptionKeyMetadata(
    long fileSizeInBytes, EncryptionKeyMetadata keyMetadata) {
  if (keyMetadata instanceof NativeEncryptionKeyMetadata nativeKeyMetadata) {
    return nativeKeyMetadata.copyWithLength(fileSizeInBytes).buffer();
  }
  return keyMetadata.buffer();
}
```

### `core/src/main/java/org/apache/iceberg/deletes/Deletes.java` (+6/-0 lines)

**修改目的**：新增 DV 写入器工厂方法。

**工作逻辑**：
```java
public static DVFileWriter writeDVs(
    EncryptedOutputFile outputFile, Function<String, PositionDeleteIndex> loadPreviousDeletes) {
  return new BaseDVFileWriter(loadPreviousDeletes, () -> outputFile);
}
```

### `core/src/test/java/org/apache/iceberg/TestRowDelta.java` (+73/-1 lines)

**修改目的**：添加加密 DV 合并的集成测试。

**工作逻辑**：
新增 `testDuplicateDVsAreMergedWithEncryption` 测试：创建加密表，写入两个有重叠删除位置的 DV，提交 RowDelta 触发合并，验证合并后的 DV 有 `keyMetadata()` 且删除位置正确。新增 `createEncryptedTable()` 辅助方法使用 `EncryptionTestHelpers.createEncryptionManager()` 和 `EncryptingFileIO.combine()` 创建加密表。

### `data/src/test/java/org/apache/iceberg/io/TestDVWriters.java` (+86/-0 lines)

**修改目的**：添加 DV 写入器加密元数据的单元测试。

**工作逻辑**：
新增两个测试：

1. `testDVWriterUsesNativeEncryptionKeyMetadataWithFileSize`：使用 `TestNativeEncryptionKeyMetadata`（record 实现了 `NativeEncryptionKeyMetadata`），验证写入后的 `DeleteFile` 的 `keyMetadata()` 解码后等于文件大小（因为 `copyWithLength` 会用文件长度替换原始值）。

2. `testDVWriterPreservesNonNativeEncryptionKeyMetadata`：使用普通 `ByteBuffer` 作为密钥元数据，验证写入后元数据原样保留。

新增 `TestNativeEncryptionKeyMetadata` record 实现 `NativeEncryptionKeyMetadata` 接口的所有方法，用于测试。

## 总结

本提交修复了一个影响加密表 DV 合并的重要 bug：合并后的 DV 文件丢失加密密钥元数据导致无法解密。修复方式是将 `BaseDVFileWriter` 升级为使用 `EncryptedOutputFile`，正确传播密钥元数据，并特别处理 `NativeEncryptionKeyMetadata` 的文件长度需求。测试覆盖了集成层面（加密表 RowDelta 合并）和单元层面（Native/非 Native 密钥元数据路径），确保修复的全面性。这对使用原生加密和 DV 功能的用户来说是关键的正确性修复。
