# 提交 1399：Core: Delete temp metadata file when version already exists (#11350)

## 提交信息

- **序号**：1399 / 4088
- **哈希**：f6d02de77fd8932cbe1fdf97e0f00acc3368e656
- **短哈希**：f6d02de77
- **日期**：2024-11-20（Wed Nov 20 14:05:06 2024 +0800）
- **作者**：leesf <leesf@apache.org>
- **提交说明**：Core: Delete temp metadata file when version already exists (#11350)
- **PR/Issue**：#11350

## 总体目的

`HadoopTableOperations` 是 Iceberg 基于 Hadoop `FileSystem`（如 HDFS、本地文件系统、S3A 等）实现表元数据存储的 `TableOperations`。提交（commit）流程是：把新的 `TableMetadata` 序列化到一个临时文件 `metadata/v<N>.metadata.json`（src），然后用 `rename` 把它原子地重命名为最终版本文件 `metadata/v<N>.metadata.json`（dst）。在 rename 之前会检查 dst 是否已存在——如果已存在，说明该版本号已被占用（可能是并发提交冲突，或历史遗留），抛出 `CommitFailedException`。

问题在于：检查到 dst 已存在并抛异常时，src（临时 metadata 文件）**没有被清理**，残留在 `metadata/` 目录下。这些孤儿临时文件：

1. 占用存储空间（HDFS、对象存储都要付费）；
2. 干扰 `remove_orphan_files` 等清理逻辑（虽然孤儿文件清理主要针对数据文件，但 metadata 目录的杂乱也会影响运维）；
3. 在测试中表现为 `metadata/` 目录下出现非 `v1.metadata.json`、`v2.metadata.json` 的额外文件，破坏对"只应有 N 个版本文件"的不变式。

本提交修复：在检测到 dst 已存在、抛 `CommitFailedException` 之前，先尝试删除 src 临时文件；如果删除失败，把删除异常作为 suppressed exception 附加到 `CommitFailedException` 上抛出，不掩盖主异常。

## 如何达成设计目的

修改 `HadoopTableOperations#renameToFinal` 方法中"dst 已存在"的分支：

1. 不再直接 `throw new CommitFailedException(...)`，而是先构造 `CommitFailedException cfe`。
2. 调用已有的 `tryDelete(src)` 方法删除临时文件 src。`tryDelete` 已经在 rename 失败和 IOException 分支中使用过，它返回 `null` 表示删除成功，返回 `RuntimeException` 表示删除时抛异常（不向上抛，避免掩盖主异常）。
3. 如果 `tryDelete` 返回非 null（即删除失败），用 `cfe.addSuppressed(re)` 把删除异常附加到主异常上。
4. 最后 `throw cfe`。

`tryDelete` 的设计哲学是"尽力清理，不掩盖主问题"——清理失败只作为 suppressed exception 报告，主异常（commit 失败的原因）仍然优先抛出。这与同方法中 rename 失败分支、IOException 分支的处理模式完全一致，本次只是把同样的模式应用到"dst 已存在"分支，让三个失败分支行为统一。

同时新增测试：在 `TestHadoopCommits` 现有的"提交失败后无 manifest 残留"测试中，追加断言验证 `metadata/` 目录下只存在 `v1.metadata.json` 和 `v2.metadata.json` 两个文件，没有临时 metadata 残留。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopTableOperations.java`

**修改目的**：在"版本已存在"分支清理临时 metadata 文件。

**工作逻辑**（`renameToFinal` 方法，第 365 行附近）：

修改前：
```java
if (fs.exists(dst)) {
  throw new CommitFailedException("Version %d already exists: %s", nextVersion, dst);
}
```

修改后：
```java
if (fs.exists(dst)) {
  CommitFailedException cfe =
      new CommitFailedException("Version %d already exists: %s", nextVersion, dst);
  RuntimeException re = tryDelete(src);
  if (re != null) {
    cfe.addSuppressed(re);
  }

  throw cfe;
}
```

`tryDelete(src)` 会调用 `io().deleteFile(path.toString())` 删除临时 metadata 文件；若抛 `RuntimeException` 则捕获返回，附加为 suppressed。这与同一方法中 `if (!fs.rename(src, dst))` 分支和 `catch (IOException e)` 分支的处理方式完全对齐，使三个失败路径都执行 src 清理。

### `core/src/test/java/org/apache/iceberg/hadoop/TestHadoopCommits.java`

**修改目的**：验证提交失败后 `metadata/` 目录无临时 metadata 残留。

**工作逻辑**：在已有的"Should contain 0 Avro manifest files"测试末尾追加：

```java
// verifies that there is no temporary metadata.json files left on disk
List<String> actual =
    listMetadataJsonFiles().stream().map(File::getName).sorted().collect(Collectors.toList());
assertThat(actual)
    .as("only v1 and v2 metadata.json should exist.")
    .containsExactly("v1.metadata.json", "v2.metadata.json");
```

`listMetadataJsonFiles()` 是测试基类 `HadoopTableTestBase` 提供的辅助方法，列出 `metadata/` 目录下所有 `.metadata.json` 文件。断言只有 `v1` 和 `v2` 两个版本文件，证明临时文件已被清理。该测试场景模拟了版本冲突（v2 已存在时再次尝试提交 v2），从而触发"dst 已存在"分支并验证 src 被删除。

## 小结

- **成效**：`HadoopTableOperations` 在检测到目标版本文件已存在时，会先删除临时 metadata 文件再抛 `CommitFailedException`，避免孤儿临时文件残留；删除失败时作为 suppressed exception 报告，不掩盖主异常。三个失败分支（dst 已存在、rename 失败、IOException）行为统一。
- **影响范围**：2 个文件、15 处新增、1 处删除；核心改动仅一个分支，影响 `HadoopTableOperations` 提交失败路径。
- **回迁到 1.4.x 的注意事项**：
  - 这是一个资源泄漏修复，建议回迁到 1.4.x，特别是当 1.4.x 部署在 HDFS 或对象存储上、且存在并发提交冲突时，可避免 metadata 目录累积临时文件。
  - 回迁风险低：仅影响提交失败路径，成功路径无变化；`tryDelete` 与 `addSuppressed` 模式在同方法中已被使用，行为一致。
  - 回迁时需确认 1.4.x 的 `HadoopTableOperations.renameToFinal` 中"dst 已存在"分支是否仍为直接 `throw`（很可能如此），若是则可直接套用本修复。
  - 测试依赖 `listMetadataJsonFiles()` 辅助方法，回迁测试时需确认 1.4.x 的 `HadoopTableTestBase` 已提供该方法；若未提供，需一并补上。
  - 该修复对存储成本与运维整洁性有正面影响，对高并发写入场景尤其重要。
