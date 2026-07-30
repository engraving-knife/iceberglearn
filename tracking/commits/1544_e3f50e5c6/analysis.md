# 提交 1544 e3f50e5c6 分析

## 提交信息
- 哈希：e3f50e5c62d01f3f31239d197ef281fc36cf31fa
- 日期：2024-12-30（Mon Dec 30 22:04:36 2024 +0100）
- 作者：Fokko Driesprong <fokko@apache.org>
- 消息：Revert "Hive: close the fileIO client when closing the hive catalog (#10771)" (#11858)

## 总体目的

回退之前合并的 PR #10771（commit 7e2920af400e5d559f8f1742b1043787b0477d74），该 PR 试图在关闭 HiveCatalog 时通过 `FileIOTracker` 自动关闭其关联的 FileIO 客户端。回退的原因是这一改动引入了问题——在关闭 catalog 时连带关闭 FileIO 客户端，可能会影响仍在使用该 FileIO 的其他组件或后续操作，导致资源被过早释放。

`HiveCatalog` 是 Iceberg 对接 Hive Metastore 的 catalog 实现。它内部维护一个 `FileIO`（如 `HadoopFileIO`）用于读写表的数据文件和元数据文件，以及一个 `CachedClientPool` 用于与 Hive Metastore 通信。原 PR #10771 引入了 `FileIOTracker` 机制：在 `newTableOps` 创建 `HiveTableOperations` 时将其注册到 tracker，并在 catalog `close()` 时通过 tracker 关闭所有追踪的 operations 及其关联的 FileIO。

然而，FileIO 的生命周期管理在 Iceberg 中是一个需要谨慎处理的问题。FileIO 实例可能被多个 catalog 或表操作共享，过早关闭可能导致后续读取/写入失败。回退此改动是一种保守的策略——将 FileIO 的关闭责任交还给调用方或依赖 JVM 的资源清理机制，避免在 catalog 关闭时引入副作用。

## 如何达成设计目的

通过 `git revert` 直接回退原 commit 的全部改动，恢复到引入 `FileIOTracker` 之前的状态。具体包括：移除 `FileIOTracker` 相关的 import、字段声明、构造函数初始化、`newTableOps` 中的追踪逻辑，以及重写的 `close()` 方法。

### 修改详情

#### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java`

**修改目的**：移除 FileIOTracker 机制，恢复 HiveCatalog 关闭时不主动关闭 FileIO 客户端的行为。

**工作逻辑**：共移除 16 行、新增 1 行，具体包括：

1. **移除 import**：删除 `java.io.IOException` 和 `org.apache.iceberg.io.FileIOTracker` 的导入（close 方法不再需要抛 IOException，FileIOTracker 不再使用）。

2. **移除字段**：删除 `private FileIOTracker fileIOTracker;` 字段声明。

3. **移除构造函数初始化**：删除构造函数中的 `this.fileIOTracker = new FileIOTracker();`。

4. **简化 `newTableOps`**：原实现为
   ```java
   HiveTableOperations ops =
       new HiveTableOperations(conf, clients, fileIO, name, dbName, tableName);
   fileIOTracker.track(ops);
   return ops;
   ```
   回退为直接返回：
   ```java
   return new HiveTableOperations(conf, clients, fileIO, name, dbName, tableName);
   ```
   不再追踪创建的 table operations。

5. **移除 `close()` 方法**：删除整个重写的 close 方法：
   ```java
   @Override
   public void close() throws IOException {
     super.close();
     if (fileIOTracker != null) {
       fileIOTracker.close();
     }
   }
   ```
   回退后 HiveCatalog 不再重写 close，使用父类的默认实现。

## 小结

- **成效**：回退了在 HiveCatalog 关闭时自动关闭 FileIO 客户端的机制，避免了因过早释放 FileIO 资源而可能导致的运行时问题，恢复了更安全的资源生命周期管理策略。
- **影响范围**：仅 `HiveCatalog.java` 一个文件，移除了 FileIOTracker 的全部集成代码，恢复到 PR #10771 之前的行为。对 HiveCatalog 的正常功能（建表、查询、写入等）无影响，仅改变了关闭时的资源清理行为。
- **回迁到 1.4.x 的注意事项**：这是一个回退提交，旨在修复 main 分支上引入的潜在问题。如果 1.4.x 分支没有合入 PR #10771 的改动，则**无需回迁**此 revert。如果 1.4.x 已合入该改动并出现相关问题，则应回迁此 revert 以修复。需检查 1.4.x 的 HiveCatalog 是否包含 FileIOTracker 相关代码来判断。
