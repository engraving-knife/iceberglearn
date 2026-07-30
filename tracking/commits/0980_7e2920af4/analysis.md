# 提交 0980：Hive: close the fileIO client when closing the hive catalog (#10771)

## 提交信息

- **序号**：0980 / 4088
- **哈希**：7e2920af400e5d559f8f1742b1043787b0477d74
- **短哈希**：7e2920af4
- **日期**：2024-07-25 11:50:19 -0600
- **作者**：Hussein Awala
- **提交说明**：Hive: close the fileIO client when closing the hive catalog (#10771)
- **PR/Issue**：#10771

## 总体目的

`HiveCatalog` 在初始化时（`initialize()`）会通过 `CatalogUtil.loadFileIO(...)` 加载一个 `FileIO` 实例并存放在 `this.fileIO` 字段中，供所有 `HiveTableOperations` 共享使用。这个 `FileIO` 可能是 `S3FileIO`、`GCSFileIO`、`HadoopFileIO` 等，它们内部通常持有 HTTP 连接池、客户端、线程池等底层资源。

然而在本次修复之前，`HiveCatalog` 没有重写 `close()` 方法，关闭 catalog 时只会经由父类 `BaseMetastoreCatalog.close()` 关闭 `metricsReporter`，**从不关闭 `FileIO`**。这意味着每次创建并关闭一个 `HiveCatalog` 实例（例如在 Spark/Flink 引擎中每个 session 或任务结束时），底层 `FileIO` 持有的资源都会被泄漏：S3/HTTP 客户端连接不被释放、线程池不被关闭。在长时间运行或频繁创建 catalog 的场景下，这种泄漏会累积，最终导致文件句柄耗尽、内存泄漏或连接池打满。

本提交的目的是在 `HiveCatalog.close()` 时正确关闭其关联的 `FileIO` 实例，修复资源泄漏。该提交由 Hussein Awala 主导，Amogh Jahagirdar 和 Eduard Tudenhoefner 共同署名。

## 如何达成设计目的

设计上没有简单地直接 `this.fileIO.close()`，而是引入了一个 Caffeine 缓存 `fileIOCloser` 作为追踪与关闭机制：

- **缓存结构**：`Cache<TableOperations, FileIO>`，以 `HiveTableOperations` 为 key，以该 ops 实际使用的 `FileIO`（`ops.io()`）为 value。
- **弱键（weakKeys）**：key 使用弱引用，使 `HiveTableOperations` 对象在不被强引用时可被 GC 回收，缓存不会阻止其回收。
- **移除监听器（removalListener）**：当缓存条目被移除（无论是因为 GC 回收弱键、还是显式 invalidate）时，监听器对 value（FileIO）调用 `close()`。
- **注册时机**：在 `newTableOps()` 中，每创建一个 `HiveTableOperations`，就将其与 `ops.io()` 注册到缓存。
- **关闭时机**：重写 `HiveCatalog.close()`，先调用 `super.close()`（关闭 metricsReporter），再 `fileIOCloser.invalidateAll()` 触发所有条目的移除监听器从而关闭各 FileIO，最后 `cleanUp()` 清理缓存本身。

通过 `ops.io()` 间接获取 FileIO 而非直接用 `this.fileIO`，是一种防御性写法，确保关闭的是 ops 实际持有的 FileIO 实例。由于所有 `HiveTableOperations` 共享同一个 `HiveCatalog.fileIO`，`invalidateAll()` 会对同一个 FileIO 触发多次 `close()` 调用，这依赖于 `FileIO` 实现的 `close()` 幂等性（主流实现如 S3FileIO/HadoopFileIO 均能安全重复关闭）。

## 修改详情

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java`

**修改目的**：在关闭 HiveCatalog 时关闭其关联的 FileIO 客户端，修复资源泄漏。

**工作逻辑**：改动包含新增 import、新增字段、新增缓存工厂方法、修改 `newTableOps()`、新增 `close()` 重写，共 32 行新增、1 行修改。

1. **新增 import**：引入 `com.github.benmanes.caffeine.cache.Cache`、`Caffeine`、`RemovalListener` 以及 `java.io.IOException`。

2. **新增字段** `fileIOCloser`：
   ```java
   private Cache<TableOperations, FileIO> fileIOCloser;
   ```
   用于追踪所有创建的 TableOperations 与其 FileIO 的映射。

3. **在 `initialize()` 末尾初始化缓存**：
   ```java
   this.fileIOCloser = newFileIOCloser();
   ```
   并新增私有方法 `newFileIOCloser()`，构建一个 `weakKeys()` 的 Caffeine 缓存，其 `removalListener` 在条目被移除时对非 null 的 `fileIOInstance` 调用 `close()`。

4. **修改 `newTableOps()`**：原先直接 `return new HiveTableOperations(...)`，改为先创建 ops，再 `fileIOCloser.put(ops, ops.io())` 注册到缓存，最后返回 ops。这样每个新建的 TableOperations 都被纳入关闭追踪。

5. **新增 `close()` 重写**：
   ```java
   @Override
   public void close() throws IOException {
     super.close();
     if (fileIOCloser != null) {
       fileIOCloser.invalidateAll();
       fileIOCloser.cleanUp();
     }
   }
   ```
   先调用父类 `close()`（关闭 metricsReporter），再 `invalidateAll()` 触发所有缓存条目的移除监听器（从而关闭各 FileIO），最后 `cleanUp()` 释放缓存资源。对 `fileIOCloser` 做 null 检查以防御未初始化场景。

## 小结

- **成效**：修复了 HiveCatalog 关闭时 FileIO 客户端资源泄漏的问题，确保 catalog 关闭时底层 FileIO（及其 HTTP 连接池、线程池等）被正确释放。这对频繁创建/销毁 catalog 的引擎场景尤为重要。
- **影响范围**：仅 `hive-metastore` 模块的 `HiveCatalog.java` 一个文件。新增字段与方法，不改既有方法签名，对调用方透明。
- **回迁到 1.4.x 的注意事项**：该提交修复的是资源泄漏 bug，适合回迁到 1.4.x。回迁时需注意：(1) 1.4.x 分支的 `BaseMetastoreCatalog` 是否已 `implements Closeable` 且有 `close()` 方法供 `super.close()` 调用——若 1.4.x 的父类结构与 main 一致则可直接回迁；若不同需调整 `super.close()` 调用。(2) 需确认 1.4.x 的 `HiveCatalog.newTableOps()` 签名与 main 一致。(3) 该改动引入 Caffeine 缓存依赖，但 Caffeine 已是 Iceberg 既有依赖，无新增风险。(4) 重复 close 依赖 FileIO 幂等性，1.4.x 的 FileIO 实现应同样满足。整体回迁风险低至中等，建议回迁以修复 1.4.x 上的同一泄漏问题。
