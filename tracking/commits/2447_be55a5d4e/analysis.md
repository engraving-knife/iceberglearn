# 提交 2447：Flink: Backport expose cleanExpiredMetadata for snapshot expiration (#13729)

## 提交信息

- **序号**：2447 / 4088
- **哈希**：be55a5d4e73e71aac5bcc1bdd14568895ab72e86
- **短哈希**：be55a5d4e7
- **日期**：2025-08-04 18:16:30 +0200
- **作者**：gaborkaszab
- **提交说明**：Flink: Backport expose cleanExpiredMetadata for snapshot expiration (#13729)
- **PR/Issue**：#13729（backports #13569）

## 总体目的

本提交是提交 2444（#13569）的 backport，将 `ExpireSnapshots` 的 `cleanExpiredMetadata` 配置支持从 Flink v2.0 目录回移植到 Flink v1.19 和 v1.20 目录。Iceberg 同时维护三个 Flink 版本，功能需在所有版本上保持一致。

原始提交 2444 只修改了 `flink/v2.0/` 目录下的 4 个文件。本提交将完全相同的改动应用到 `flink/v1.19/` 和 `flink/v1.20/` 目录下对应的 4 个文件，使三个版本的 Flink 快照过期维护操作都支持清理过期元数据。

## 如何达成设计目的

将 v2.0 的改动原样应用到 v1.19 和 v1.20 的对应文件：
1. `ExpireSnapshots.java`：Builder 新增 `cleanExpiredMetadata` 字段和 builder 方法，构建 processor 时传入。
2. `ExpireSnapshotsProcessor.java`：构造函数新增参数，非 null 时调用 `expireSnapshots.cleanExpiredMetadata(...)`，并修复 typo。
3. `OperatorTestBase.java`：新增支持 extra 列的 insert 辅助方法。
4. `TestExpireSnapshotsProcessor.java`：新增参数化测试 `testCleanExpiredMetadata`，并适配构造函数签名。

## 修改详情

### v1.19 的 4 个文件（各 +16/-1、+11/-2、+6/-0、+44/-1 lines）

- `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/api/ExpireSnapshots.java`：Builder 新增 `cleanExpiredMetadata` 字段、builder 方法、传参给 processor。
- `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/ExpireSnapshotsProcessor.java`：新增字段和构造参数，非 null 时调用 Core 的 `cleanExpiredMetadata`，修复 typo。
- `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java`：新增 `insert(Table, Integer, String, String)` 重载。
- `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestExpireSnapshotsProcessor.java`：新增参数化测试验证元数据清理开关行为，适配构造函数。

### v1.20 的 4 个文件（同上）

`flink/v1.20/` 下对应的 4 个文件做了与 v1.19 完全相同的改动。

## 总结

本提交是 2444 的 backport，将 `ExpireSnapshots` 的 `cleanExpiredMetadata` 支持同步到 Flink v1.19 和 v1.20 版本。改动内容与原提交完全一致，仅目录不同。这保证了 Iceberg 在三个 Flink 版本上功能的一致性。
