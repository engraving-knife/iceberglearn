# 提交 1059：AWS, Core, Hive: Extract FileIO closing into separate FileIOTracker class (#10893)

## 提交信息

- **序号**：1059 / 4088
- **哈希**：cf02ffac4329141b30bca265cafb9987f64f6cc4
- **短哈希**：cf02ffac4
- **日期**：2024-08-13 17:27:32 -0600
- **作者**：Eduard Tudenhoefner
- **提交说明**：AWS, Core, Hive: Extract FileIO closing into separate FileIOTracker class (#10893)
- **PR/Issue**：#10893

## 总体目的

在 `GlueCatalog`、`HiveCatalog` 和 `RESTSessionCatalog` 三个 catalog 实现中，都存在一段几乎相同的"跟踪 `TableOperations` 对应的 `FileIO` 实例并在 catalog 关闭时关闭它们"的逻辑。这段逻辑使用 Caffeine 缓存（`weakKeys()` + `RemovalListener` 在条目被回收时关闭 `FileIO`）实现，代码在三个类中重复出现，维护成本高且容易不一致。

本提交把这段重复逻辑抽取到独立的 `FileIOTracker` 类中，统一三个 catalog 的 FileIO 跟踪与关闭行为，减少代码重复，并为后续可能的统一改进（例如更可控的关闭时机）打下基础。同时新增对应的单元测试 `TestFileIOTracker` 验证其行为。

## 如何达成设计目的

新建 `core/src/main/java/org/apache/iceberg/io/FileIOTracker.java`，封装 Caffeine 缓存与关闭逻辑：通过 `track(TableOperations ops)` 方法记录 ops 及其 `io()`，在 `close()` 时调用 `invalidateAll()` 和 `cleanUp()` 触发 RemovalListener 关闭所有 FileIO。该类实现 `Closeable`，可被纳入各 catalog 现有的 `CloseableGroup` 统一管理。

三个 catalog 分别替换原有内联逻辑：删除各自的 `newFileIOCloser()` 方法和 `Cache<TableOperations, FileIO> fileIOCloser` 字段，改为持有 `FileIOTracker fileIOTracker`，调用 `fileIOTracker.track(ops)` 代替 `fileIOCloser.put(ops, ops.io())`，并在 `close()` 中通过 `CloseableGroup` 或直接调用 `fileIOTracker.close()` 完成清理。同时修改 `TestTables.TestTableOperations` 支持注入 FileIO，使测试可以验证 FileIO 是否被关闭。

## 修改详情

### `core/src/main/java/org/apache/iceberg/io/FileIOTracker.java` (new file, +65 lines)

**修改目的**：新建统一的 FileIO 跟踪与关闭工具类。

**工作逻辑**：
```java
public class FileIOTracker implements Closeable {
  private final Cache<TableOperations, FileIO> tracker;

  public FileIOTracker() {
    this.tracker =
        Caffeine.newBuilder()
            .weakKeys()
            .removalListener(
                (RemovalListener<TableOperations, FileIO>)
                    (ops, fileIO, cause) -> {
                      if (null != fileIO) {
                        fileIO.close();
                      }
                    })
            .build();
  }

  public void track(TableOperations ops) {
    Preconditions.checkArgument(null != ops, "Invalid table ops: null");
    tracker.put(ops, ops.io());
  }

  @VisibleForTesting
  Cache<TableOperations, FileIO> tracker() {
    return tracker;
  }

  @Override
  public void close() {
    tracker.invalidateAll();
    tracker.cleanUp();
  }
}
```
使用 `weakKeys()` 让 `TableOperations` 可被 GC 回收时触发 RemovalListener 关闭对应 FileIO；`close()` 主动失效所有条目完成批量关闭。`track()` 方法对 null 入参做校验。

### `aws/src/main/java/org/apache/iceberg/aws/glue/GlueCatalog.java` (+5/-26 lines)

**修改目的**：用 `FileIOTracker` 替换 GlueCatalog 内联的 FileIO 关闭逻辑。

**工作逻辑**：
- 移除 Caffeine 相关 import，改为 import `FileIOTracker`。
- 字段 `Cache<TableOperations, FileIO> fileIOCloser` 改为 `FileIOTracker fileIOTracker`。
- 初始化时 `new FileIOTracker()` 并加入 `closeableGroup`，删除原 `newFileIOCloser()` 调用。
- 两处 `fileIOCloser.put(glueTableOperations, glueTableOperations.io())` 改为 `fileIOTracker.track(glueTableOperations)`。
- `close()` 方法删除手动 `invalidateAll/cleanUp` 的代码（已由 CloseableGroup 关闭 FileIOTracker 完成）。
- 删除私有的 `newFileIOCloser()` 方法。

### `core/src/main/java/org/apache/iceberg/rest/RESTSessionCatalog.java` (+5/-22 lines)

**修改目的**：用 `FileIOTracker` 替换 RESTSessionCatalog 内联的 FileIO 关闭逻辑。

**工作逻辑**：
- 字段 `Cache<TableOperations, FileIO> fileIOCloser` 改为 `FileIOTracker fileIOTracker`。
- 初始化时 `new FileIOTracker()` 并加入 `closeables`（CloseableGroup）。
- `trackFileIO(RESTTableOperations ops)` 中 `fileIOCloser.put(ops, ops.io())` 改为 `fileIOTracker.track(ops)`。
- `close()` 删除手动清理 fileIOCloser 的代码块。
- 删除私有 `newFileIOCloser()` 方法，移除不再使用的 `TableOperations` import。

### `hive-metastore/src/main/java/org/apache/iceberg/hive/HiveCatalog.java` (+3/-25 lines)

**修改目的**：用 `FileIOTracker` 替换 HiveCatalog 内联的 FileIO 关闭逻辑。

**工作逻辑**：
- 移除 Caffeine 相关 import，改为 import `FileIOTracker`。
- 字段 `Cache<TableOperations, FileIO> fileIOCloser` 改为 `FileIOTracker fileIOTracker`。
- 初始化时 `this.fileIOTracker = new FileIOTracker()`，删除原 `newFileIOCloser()` 方法。
- `newTableOps` 中 `fileIOCloser.put(ops, ops.io())` 改为 `fileIOTracker.track(ops)`。
- `close()` 中改为 `if (fileIOTracker != null) { fileIOTracker.close(); }`。

### `core/src/test/java/org/apache/iceberg/TestTables.java` (+17/-2 lines)

**修改目的**：让 `TestTableOperations` 支持注入 FileIO，以便测试 FileIO 关闭行为。

**工作逻辑**：
- 新增 `private final FileIO fileIO` 字段。
- 新增构造函数 `TestTableOperations(String tableName, File location, FileIO fileIO)` 允许外部传入 FileIO。
- 原构造函数内部改为使用 `new LocalFileIO()`。
- `io()` 方法从原来每次返回 `new LocalFileIO()` 改为返回持有的 `fileIO` 字段，保证同一 ops 的 io() 引用稳定（这是 FileIOTracker 跟踪关闭的前提）。
- `LocalFileIO` 由包级静态改为 `public static`，便于测试引用。

### `core/src/test/java/org/apache/iceberg/io/TestFileIOTracker.java` (new file, +72 lines)

**修改目的**：为 `FileIOTracker` 添加单元测试。

**工作逻辑**：
- `nullTableOps()`：验证 `track(null)` 抛出 `IllegalArgumentException` 且消息为 "Invalid table ops: null"。
- `fileIOGetsClosed()`：用 Mockito spy 包装两个 `TestTables.LocalFileIO`，分别构造两个 `TestTableOperations` 并 `track`，断言 estimatedSize 为 1、2；调用 `close()` 后用 Awaitility 等待 estimatedSize 变为 0，并验证两个 spy FileIO 各被 `close()` 一次。

## 总结

这是一次典型的"三处重复逻辑抽取"重构提交。把分散在 GlueCatalog、HiveCatalog、RESTSessionCatalog 中相同的 Caffeine 缓存式 FileIO 跟踪/关闭逻辑统一到 `FileIOTracker` 类，消除代码重复，降低维护成本，并通过纳入 `CloseableGroup` 让资源生命周期管理更一致。配套修改 `TestTables` 支持注入 FileIO，并新增 `TestFileIOTracker` 单元测试保证新类的正确性。整体对功能行为无改变，属于提升代码质量的内部重构。
