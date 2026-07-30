# 提交 2248：AWS, GCP: Fix double-checked-locking pattern in S3FileIO, GCSFileIO

## 提交信息

- **序号**：2248 / 4088
- **哈希**：0eb9728a466de3c419a5db68f80028b5c09ce76e
- **短哈希**：0eb9728a4
- **日期**：2025-06-17 23:36:14 +0530
- **作者**：ChaladiMohanVamsi
- **提交说明**：AWS, GCP: Fix double-checked-locking pattern in S3FileIO, GCSFileIO.
- **PR/Issue**：#13276

## 总体目的

本提交修复了 S3FileIO 和 GCSFileIO 中双重检查锁定（double-checked locking）模式的一个并发安全问题。在原有的实现中，`clientByPrefix`（S3FileIO）和 `storageByPrefix`（GCSFileIO）的 Map 在同步块内直接对实例字段进行逐步填充——先创建空 Map 赋值给字段，再逐步 put 条目。这意味着在 Map 构建完成之前，其他线程可能在第一次 null 检查时看到非 null 的引用，从而读取到一个部分构造的 Map，导致数据不一致或 NullPointerException。修复方式是先在局部变量中完成 Map 的完整构建，然后一次性赋值给实例字段，确保其他线程看到的要么是 null，要么是完全构造好的 Map。

## 如何达成设计目的

- 在 S3FileIO 的 `prefixedClients()` 方法中，将 `this.clientByPrefix` 的构建改为使用局部变量 `localClientByPrefix`，在所有 put 操作完成后才将局部变量赋值给 `this.clientByPrefix`。
- 在 GCSFileIO 的 `prefixedStorages()` 方法中，对 `this.storageByPrefix` 应用相同的修复模式。
- 这种模式确保了在双重检查锁定中，实例字段的发布是安全的——其他线程要么看到 null（进入同步块），要么看到完全构造好的 Map。

## 修改详情

### `aws/src/main/java/org/apache/iceberg/aws/s3/S3FileIO.java` (修改, +4/-3 lines)

**修改目的**：修复双重检查锁定中部分构造 Map 可见性的并发问题。

**工作逻辑**：在 `prefixedClients()` 方法的同步块内，将 `this.clientByPrefix = Maps.newHashMap()` 改为 `Map<String, PrefixedS3Client> localClientByPrefix = Maps.newHashMap()`，所有后续的 `clientByPrefix.put(...)` 改为 `localClientByPrefix.put(...)`，最后在同步块结束前添加 `this.clientByPrefix = localClientByPrefix`。这样确保 `this.clientByPrefix` 从 null 直接变为完全填充的 Map，不会出现中间状态被其他线程观察到的风险。

### `gcp/src/main/java/org/apache/iceberg/gcp/gcs/GCSFileIO.java` (修改, +4/-3 lines)

**修改目的**：同 S3FileIO 的修复，修复 GCSFileIO 中相同的并发问题。

**工作逻辑**：在 `prefixedStorages()` 方法中应用相同的模式——使用局部变量 `localStorageByPrefix` 构建 Map，完成所有 put 操作后才赋值给 `this.storageByPrefix`。

## 总结

本提交修复了 S3FileIO 和 GCSFileIO 中双重检查锁定模式的并发安全缺陷。原实现在同步块内直接对实例字段逐步构建 Map，可能导致其他线程看到部分构造的 Map。修复后使用局部变量完整构建后再发布，确保了字段赋值的原子性可见性。这是一个重要的并发安全修复，防止了在多线程场景下可能出现的数据不一致或异常。
