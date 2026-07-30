# 提交 2901：Move deleted files to Hadoop trash if configured (#14501)

## 提交信息

- **序号**：2901 / 4088
- **哈希**：06c1e0a0be75b2dd419b2a97fcb47676cf4da279
- **短哈希**：06c1e0a0b
- **日期**：2025-11-20 11:05:15 -0600
- **作者**：Jordan Epstein
- **提交说明**：Move deleted files to Hadoop trash if configured (#14501)
- **PR/Issue**：#14501

## 总体目的

在 Iceberg 表的运行过程中，过期数据文件、 manifests 文件、以及前缀目录的清理是日常维护的关键环节。当用户使用 `HadoopFileIO` 通过 Iceberg 删除底层文件时，原来的实现直接调用 Hadoop `FileSystem.delete(...)` API。这个 Java delete API 的语义是"立即且永久删除"，它永远跳过 Hadoop 的 trash（回收站）目录，即使该表所在的 Hadoop 配置中明确启用了 trash 功能（即配置了 `fs.trash.interval`）。

这就带来一个潜在风险：对于配置了 trash 以便回收/审计/恢复误删文件的集群，Iceberg 的删除操作会绕过这套保护机制，被删文件无法通过 Hadoop trash 找回。提交作者希望让 `HadoopFileIO` 在删除时尊重 Hadoop 配置中的 trash 开关——如果配置启用了 trash，则把待删文件移动到 trash 目录而不是直接物理删除；如果没有启用，则保持原有的物理删除行为。

需要注意的是，并不是所有 `FileSystem` 实现都适合走 trash 流程。作者在 commit message 中明确指出：只对 `LocalFileSystem` 和 `DistributedFileSystem` 这两种实现启用 trash 移动，对其他文件系统实现仍走原来的直接删除路径，避免在不支持或不兼容 trash 的实现上引入意外行为。

## 如何达成设计目的

本提交在 `HadoopFileIO` 中新增一个私有方法 `deletePath(FileSystem, Path, boolean)`，集中封装"是否走 trash"的判断与执行逻辑。原先 `deleteFile` 和 `deletePrefix` 两处直接调用 `fs.delete(...)` 的地方都改为调用这个新方法。判断逻辑为：当且仅当 `fs` 是 `LocalFileSystem` 或 `DistributedFileSystem` 之一，并且 `Trash.isEnabled()` 返回 true 时，调用 `trash.moveToTrash(toDelete)`；否则回退到原有的 `fs.delete(toDelete, recursive)`。配套在 `TestHadoopFileIO` 中新增两个测试用例，分别覆盖 `deletePrefix` 与 `deleteFiles` 在启用 trash 时的行为。

## 修改详情

### `core/src/main/java/org/apache/iceberg/hadoop/HadoopFileIO.java` (+15/-2 lines)

**修改目的**：让 `HadoopFileIO` 在删除文件/前缀时尊重 Hadoop 配置中的 trash 开关，将可走 trash 的删除操作改为移动到回收站而非直接物理删除。

**工作逻辑**：
新增 import `org.apache.hadoop.fs.LocalFileSystem`、`org.apache.hadoop.fs.Trash`、`org.apache.hadoop.hdfs.DistributedFileSystem`。`deleteFile(String path)` 方法中原本的 `fs.delete(toDelete, false)` 改为 `deletePath(fs, toDelete, false)`；`deletePrefix(String prefixToDelete)` 方法中原本的 `fs.delete(prefixToDelete, true)` 改为 `deletePath(fs, prefixToDelete, true)`。

新增的核心私有方法：
```java
private void deletePath(FileSystem fs, Path toDelete, boolean recursive) throws IOException {
  Trash trash = new Trash(fs, getConf());
  if ((fs instanceof LocalFileSystem || fs instanceof DistributedFileSystem)
      && trash.isEnabled()) {
    trash.moveToTrash(toDelete);
  } else {
    fs.delete(toDelete, recursive);
  }
}
```
该方法首先基于 `fs` 和当前 Hadoop `Configuration` 构造一个 `Trash` 实例，通过 `trash.isEnabled()` 判断配置是否启用了 trash（底层由 `fs.trash.interval` 与 `fs.trash.checkpoint.interval` 决定），再用 `instanceof` 限定只对 `LocalFileSystem` 和 `DistributedFileSystem` 走 trash 分支。满足条件时调用 `trash.moveToTrash(toDelete)` 把文件/目录移入回收站；否则保持原行为调用 `fs.delete(toDelete, recursive)`。这样既复用了 Hadoop 自带的 trash 机制（含过期清理），又避免对 S3/HDFS 之外的其他 FileSystem 实现造成兼容性问题。

### `core/src/test/java/org/apache/iceberg/hadoop/TestHadoopFileIO.java` (+62/-0 lines)

**修改目的**：为启用 trash 后的 `deletePrefix` 和 `deleteFiles` 行为新增覆盖测试，验证文件确实被移到 trash 目录而非直接物理删除。

**工作逻辑**：
新增 import `static org.apache.hadoop.fs.CommonConfigurationKeysPublic.FS_TRASH_INTERVAL_KEY`。新增两个测试方法：

1. `testDeletePrefixWithTrashEnabled()`：构造一个 `Configuration` 并设置 `FS_TRASH_INTERVAL_KEY = "60"`（开启 trash，间隔 60 分钟），用本地 `FileSystem` 创建 `HadoopFileIO`。对 `1`、`1000`、`2500` 三种规模的目录并行生成随机文件，然后调用 `hadoopFileIO.deletePrefix(scalePath)`。验证两件事：一是原路径已被删除（`listPrefix` 抛 `UncheckedIOException` 且消息含 `FileNotFoundException`）；二是每个被删文件都出现在 trash 目录下——通过 `fs.getTrashRoot(scalePath).toString() + "/Current" + fileSuffix` 拼出 trash 中的预期路径并断言其存在。最后删除父目录并再次断言父目录已不存在。

2. `testDeleteFilesWithTrashEnabled()`：同样设置 `FS_TRASH_INTERVAL_KEY = "60"`，创建 10 个随机文件后调用 `hadoopFileIO.deleteFiles(...)`。断言每个原文件已不存在，并且对应 trash 路径（`fs.getTrashRoot(parent) + "/Current" + fileSuffix`）上的文件确实存在。

两个测试共同证明了启用 trash 后删除操作走的是 `trash.moveToTrash` 分支，且文件最终落在 Hadoop trash 的 `Current` 目录下。

## 总结

本提交为 `HadoopFileIO` 增加了在 Hadoop 配置启用 trash 时将删除文件移入回收站的能力，使 Iceberg 的文件删除行为与集群 trash 策略保持一致，从而支持误删恢复与回收审计需求。改动通过新增 `deletePath` 私有方法集中管理删除逻辑，仅对 `LocalFileSystem` 和 `DistributedFileSystem` 启用 trash，既最小化了对现有行为的影响，又保证了实现的安全性，配套测试覆盖了 `deletePrefix` 与 `deleteFiles` 两个入口在 trash 启用场景下的正确性。
