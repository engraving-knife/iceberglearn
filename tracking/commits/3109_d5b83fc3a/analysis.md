# 提交 3109：Include key metadata in manifest tables (Spark 4.1) (#15041)

## 提交信息

- **序号**：3109 / 4088
- **哈希**：d5b83fc3a5035244ba279a1204e52efe118e7f46
- **短哈希**：d5b83fc3a
- **日期**：2026-01-13
- **作者**：Thomas Powell
- **提交说明**：Include key metadata in manifest tables (Spark 4.1) (#15041)
- **PR/Issue**：#15041

## 总体目的

Iceberg 提供"manifest 表"（通过 `manifests` 元表读取），让用户能够以 DataFrame 的形式查询一张表所有 manifest 文件的元信息（路径、长度、分区 spec id、added snapshot id 等）。然而在 Spark 4.1 的实现中，`BaseSparkAction` 构造 manifest 元表 DataFrame 时并没有把 manifest 的 `key_metadata`（加密密钥元数据）字段投影出来，`ManifestFileBean.keyMetadata()` 也直接返回 `null`。这意味着对于启用了加密的表，用户通过 manifest 表无法看到每个 manifest 关联的密钥元数据，丢失了原本存在于 `ManifestFile` 接口上的信息。

本提交的目的是补齐这一缺失：在 manifest 元表的列投影中加入 `key_metadata as keyMetadata`，并在 `ManifestFileBean` 中真正承载和返回该字段，使 manifest 表完整暴露 `ManifestFile` 接口已有的 `keyMetadata()` 信息。这对于加密表的运维与审计（例如核对每个 manifest 的加密密钥标识）有实际价值。提交说明中的 "(Spark 4.1)" 表明该改动只落在 spark/v4.1 模块，属于该版本专有的补齐。

## 如何达成设计目的

改动很集中：在 `BaseSparkAction` 构建 manifest DataFrame 的 `select(...)` 列表里追加 `key_metadata as keyMetadata` 一列；在 `ManifestFileBean` 中新增 `byte[] keyMetadata` 字段及其 getter/setter，让 `fromManifest` 工厂方法把源 manifest 的密钥元数据拷贝进 bean，并把 `keyMetadata()` 的实现从恒为 `null` 改为基于该字段返回 `ByteBuffer`。这样从 Spark 元表扫描出来的 manifest 行就携带了密钥元数据，并可通过 bean 的 `ManifestFile` 接口正常访问。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/BaseSparkAction.java` (+2/-1 lines)

**修改目的**：在 manifest 元表 DataFrame 的投影中加入 `key_metadata` 列。

**工作逻辑**：在构造 manifest 元表的 `select(...)` 调用里，于 `added_snapshot_id as addedSnapshotId` 之后追加 `"key_metadata as keyMetadata"`。这样从 manifest 文件元数据中读取的 `key_metadata` 列会被选中并别名映射到 `ManifestFileBean` 的 `keyMetadata` 属性（Spark 会按列名匹配 bean 的 setter），随后 `.as(ManifestFileBean.ENCODER)` 把该列编码进 bean。此前该列被忽略，导致 bean 的 `keyMetadata` 始终为默认 `null`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/actions/ManifestFileBean.java` (+11/-1 lines)

**修改目的**：让 `ManifestFileBean` 真正承载并返回 `keyMetadata` 字段。

**工作逻辑**：
- 新增 `private byte[] keyMetadata = null;` 字段，以及对应的 `getKeyMetadata()`/`setKeyMetadata(byte[])` 访问器，使 Spark 的 bean 编码器能按属性名读写该列。
- `fromManifest(ManifestFile)` 中新增 `bean.setKeyMetadata(manifest.keyMetadata() == null ? null : manifest.keyMetadata().array());`，把源 manifest 的 `ByteBuffer` 密钥元数据复制为 `byte[]` 存入 bean，注意对 `null` 做了判空以避免 NPE。
- `keyMetadata()` 方法（`ManifestFile` 接口实现）由原先 `return null;` 改为 `return keyMetadata == null ? null : ByteBuffer.wrap(keyMetadata);`，即把存储的 `byte[]` 重新包装成 `ByteBuffer` 返回，保持接口契约（返回 `ByteBuffer`）不变，同时真正暴露数据。

## 总结

本提交为 Spark 4.1 的 manifest 元表补齐了加密密钥元数据（`key_metadata`）的展示：通过在 `BaseSparkAction` 的列投影中追加该列、并在 `ManifestFileBean` 中新增字段与访问器、修正 `keyMetadata()` 的返回实现，使 manifest 表不再丢失 `ManifestFile` 接口本就提供的密钥元数据信息。改动小而聚焦，价值在于让加密表的运维与审计能够通过 manifest 元表查看每个 manifest 关联的密钥元数据，填补了此前的信息缺口。
