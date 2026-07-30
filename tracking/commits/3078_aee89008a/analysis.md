# 提交 3078：Include key metadata in manifest tables (#14750)

## 提交信息

- **序号**：3078 / 4088
- **哈希**：aee89008aaebb99eb26ddbd3f79220a30a7ef6c9
- **短哈希**：aee89008a
- **日期**：2026-01-07
- **作者**：Thomas Powell
- **提交说明**：Include key metadata in manifest tables (#14750)
- **PR/Issue**：#14750

## 总体目的

Iceberg 的 `AllManifestsTable` 是一个元数据表，允许用户查询表中所有 manifest 文件的信息（如路径、分区统计、快照引用等）。然而此前该表的 schema 和输出中不包含 manifest 文件的加密密钥元数据（`key_metadata`），这意味着当表启用了加密（encryption）时，用户无法通过 manifest 表查看每个 manifest 文件关联的加密密钥 ID。

在启用了数据加密的场景中，manifest 文件本身也可能被加密，每个 manifest 关联一个 `keyMetadata`（`ByteBuffer` 形式）。将此信息暴露到 manifest 元数据表中，可以让用户和管理员审计加密状态、排查密钥相关问题，以及在进行表维护操作时了解 manifest 的加密情况。本提交的核心目的就是将 `key_metadata` 字段加入 `AllManifestsTable` 的 schema 和输出，并在 Spark 的 `BaseSparkAction`（用于 `remove_orphan_files` 等表维护操作）中也将该字段一并投影，确保密钥元数据在各相关路径中可用。

此外，由于 manifest 列表文件（manifest list file）的位置此前以纯 `String` 传递，而密钥 ID 也需要随同传递，本提交引入了 `ManifestListFile` 抽象（包含 `location` 和 `encryptionKeyID`），将两者绑定在一起，避免在任务序列化时丢失密钥信息。

## 如何达成设计目的

改动分三层：核心层在 `AllManifestsTable` 的 schema 中新增 `key_metadata`（id=19，BinaryType 可选字段），并在构建行数据时从 `ManifestFile.keyMetadata()` 提取并通过 `ByteBuffers.toByteArray()` 转为 `byte[]` 填充；同时将任务中传递的 `manifestListLocation`（String）替换为 `ManifestListFile` 对象，使其能携带快照的 `keyId`。序列化层在 `AllManifestsTableTaskParser` 中新增 `manifest-list-key-id` 字段的 JSON 读写。Spark 层在 `BaseSparkAction` 的 manifest 投影中新增 `key_metadata as keyMetadata` 列，并在 `ManifestFileBean` 中新增 `keyMetadata` 字段及其 getter/setter，使 `keyMetadata()` 方法不再返回 null 而是返回实际值。

## 修改详情

### `core/src/main/java/org/apache/iceberg/AllManifestsTable.java` (+17/-9 lines)

**修改目的**：在 manifest 表 schema 中新增 `key_metadata` 字段，并将 manifest list 文件位置封装为 `ManifestListFile` 以携带密钥 ID。

**工作逻辑**：
1. 在 `MANIFEST_FILE_SCHEMA` 中新增字段 `Types.NestedField.optional(19, "key_metadata", Types.BinaryType.get())`，紧跟 `REF_SNAPSHOT_ID`（id=18）之后。
2. 在构建 `AllManifestsTableTask` 时，将 `snap.manifestListLocation()` 替换为 `new BaseManifestListFile(snap.manifestListLocation(), snap.keyId())`，把快照的加密密钥 ID 一并传入任务。
3. 将任务类中 `String manifestListLocation` 字段改为 `ManifestListFile manifestList`，构造函数参数同步变更。所有使用 `manifestListLocation` 的地方改为 `manifestList`（读取时 `io.newInputFile(manifestList)`、错误信息中 `manifestList.location()`、getter 方法改为返回 `ManifestListFile`）。
4. 在 `rows()` 方法的 `transform` 中，将 `manifest.keyMetadata()`（`ByteBuffer`）通过 `ByteBuffers.toByteArray()` 转为 `byte[]` 填入新字段的第 14 列位置（null 时填 null）。

### `core/src/main/java/org/apache/iceberg/AllManifestsTableTaskParser.java` (+7/-2 lines)

**修改目的**：在任务 JSON 序列化/反序列化中支持 manifest list 的密钥 ID。

**工作逻辑**：
新增常量 `MANIFEST_LIST_KEY_ID = "manifest-list-key-id"`。在序列化（`toJson`）时，先写 `manifest-list-Location` 字段（值改为 `task.manifestList().location()`），若 `task.manifestList().encryptionKeyID()` 不为 null 则额外写 `manifest-list-key-id` 字段。在反序列化（`fromJson`）时，用 `JsonUtil.getStringOrNull` 读取 `manifest-list-key-id`（可能不存在于旧格式中），然后用 `new BaseManifestListFile(manifestListLocation, manifestListKeyId)` 构建任务。使用 `getStringOrNull` 保证了向后兼容——旧的序列化数据没有该字段时不会报错。

### `core/src/test/java/org/apache/iceberg/TestAllManifestsTableTaskParser.java` (+7/-3 lines)

**修改目的**：更新测试以覆盖新增的 `key_metadata` 字段和 `manifest-list-key-id` 序列化。

**工作逻辑**：
1. 构建测试任务时将 `"/path/manifest-list-file.avro"` 替换为 `new BaseManifestListFile("/path/manifest-list-file.avro", "a")`，携带密钥 ID `"a"`。
2. 在期望的 JSON 字符串中新增 `{"id":19,"name":"key_metadata","required":false,"type":"binary"}` 字段定义和 `"manifest-list-key-id":"a"` 值。
3. 在断言中新增对 `actual.manifestList().encryptionKeyID()` 与 `expected.manifestList().encryptionKeyID()` 相等的校验，并将原 `manifestListLocation()` 断言改为 `manifestList().location()`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSparkAction.java` (+2/-1 lines)

**修改目的**：在 Spark v3.4 的 manifest 文件投影中新增 `key_metadata` 列。

**工作逻辑**：
在 `loadMetadataTable` 读取 manifest 表数据时，Spark SQL 的 `select` 列表中新增 `"key_metadata as keyMetadata"`，使 manifest 文件的密钥元数据被投影到 DataFrame 中。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/ManifestFileBean.java` (+10/-2 lines)

**修改目的**：在 Spark v3.4 的 `ManifestFileBean` 中支持 `keyMetadata` 字段的存储与访问。

**工作逻辑**：
1. 新增 `private byte[] keyMetadata = null` 字段及对应 getter/setter。
2. 在 `fromManifest` 工厂方法中，`bean.setKeyMetadata(manifest.keyMetadata() == null ? null : manifest.keyMetadata().array())`，将 `ByteBuffer` 转为 `byte[]` 存储。
3. 将 `keyMetadata()` 方法从 `return null` 改为 `return keyMetadata == null ? null : ByteBuffer.wrap(keyMetadata)`，返回实际值。
该 Bean 实现 `ManifestFile` 接口，用于在 Spark DataFrame 编解码中承载 manifest 信息，此前 `keyMetadata()` 恒返回 null，现在能正确传递密钥元数据。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSparkAction.java` (+2/-1 lines)

**修改目的**：同 v3.4，为 Spark v3.5 的 manifest 投影新增 `key_metadata` 列。逻辑与 v3.4 完全一致。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/ManifestFileBean.java` (+10/-2 lines)

**修改目的**：同 v3.4，为 Spark v3.5 的 `ManifestFileBean` 新增 `keyMetadata` 支持。逻辑与 v3.4 完全一致。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSparkAction.java` (+2/-1 lines)

**修改目的**：同 v3.4，为 Spark v4.0 的 manifest 投影新增 `key_metadata` 列。逻辑与 v3.4 完全一致。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/ManifestFileBean.java` (+10/-2 lines)

**修改目的**：同 v3.4，为 Spark v4.0 的 `ManifestFileBean` 新增 `keyMetadata` 支持。逻辑与 v3.4 完全一致。

## 总结

本提交为 Iceberg 的 `AllManifestsTable` 元数据表和 Spark 表维护操作（`BaseSparkAction`）增加了 manifest 文件加密密钥元数据（`key_metadata`）的暴露能力。通过引入 `ManifestListFile` 抽象将 manifest list 位置与加密密钥 ID 绑定，确保在任务序列化和分布式执行中不丢失密钥信息，同时保持了与旧格式数据的向后兼容。这对启用了加密的 Iceberg 表的审计、运维和密钥管理具有实际价值。改动覆盖 core 模块和 Spark v3.4/v3.5/v4.0 三个版本。
