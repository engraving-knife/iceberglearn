# 提交 1866：Core: Close FileIO instance in JdbcCatalog (#12540)

## 提交信息

- **序号**：1866 / 4088
- **哈希**：bf2f552722b8f29b5a6c5c0a4b98c08bb400a0eb
- **短哈希**：bf2f55272
- **日期**：2025-03-17 12:29:08 +0100
- **作者**：rcjverhoef
- **提交说明**：Core: Close FileIO instance in JdbcCatalog (#12540)
- **PR/Issue**：#12540

## 总体目的

`JdbcCatalog` 在 `initialize` 时会创建一个 `FileIO` 实例（`this.io = ioBuilder.apply(properties)` 或 `CatalogUtil.loadFileIO(...)`），用于读写表的数据文件。`JdbcCatalog` 同时维护一个 `CloseableGroup closeableGroup`，在 catalog `close()` 时统一关闭所有持有的资源（`metricsReporter()`、`connections` 等）。

但此前的 `closeableGroup` 只登记了 `metricsReporter()` 和 `connections`（JDBC 连接池），**遗漏了 `io`**。结果 catalog 关闭时 `FileIO` 实例不会被关闭，导致资源泄漏——例如 S3/HDFS 客户端的连接池、HTTP 连接、文件句柄等不会被释放。长时间运行的应用（如常驻的 Spark/Flink 作业）反复创建关闭 catalog 时，泄漏会累积，最终耗尽连接或句柄。

本提交在 `initialize` 中把 `io` 也加入 `closeableGroup`，确保 catalog 关闭时 `FileIO.close()` 被调用。

## 如何达成设计目的

在 `JdbcCatalog.initialize` 中，`closeableGroup.addCloseable(connections);` 之后新增 `closeableGroup.addCloseable(io);`。`CloseableGroup` 会在 `close()` 时逆序调用所有登记的 `Closeable.close()`，因此 `io` 会在 `connections` 之后关闭（顺序合理：先关连接池再关 FileIO）。`setSuppressCloseFailure(true)` 已设置，单个资源关闭失败不会中断其他资源的关闭。

## 修改详情

### `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java` (修改, +1 line)

**修改目的**：把 `FileIO io` 加入 `closeableGroup`，防止资源泄漏。

**工作逻辑**：`initialize` 方法中：

```java
this.closeableGroup = new CloseableGroup();
closeableGroup.addCloseable(metricsReporter());
closeableGroup.addCloseable(connections);
closeableGroup.addCloseable(io);   // 新增
closeableGroup.setSuppressCloseFailure(true);
```

`io` 在上方刚被赋值（`this.io = ioBuilder.apply(properties)` 或 `CatalogUtil.loadFileIO(...)`），此处登记后，`JdbcCatalog.close()` → `closeableGroup.close()` 会调用 `io.close()`，释放 FileIO 持有的底层资源（HTTP 连接池、文件句柄等）。`CloseableGroup` 逆序关闭，`io` 在 `connections` 之后关闭。

## 小结

- **成效**：修复了 `JdbcCatalog` 关闭时不关闭 `FileIO` 的资源泄漏 bug，避免 S3/HDFS 等客户端连接池与句柄泄漏。
- **影响范围**：仅 `core/src/main/java/org/apache/iceberg/jdbc/JdbcCatalog.java` 1 个文件、1 行新增。影响 JdbcCatalog 的关闭路径，不影响正常运行。
- **回迁到 1.4.x 的注意事项**：建议回迁。单行修复，无前置依赖，回迁零风险。需确认 1.4.x 的 `JdbcCatalog.initialize` 中 `closeableGroup` 已登记 `metricsReporter()` 与 `connections`，且 `io` 字段已赋值。若 1.4.x 的 `JdbcCatalog` 结构与 main 一致，直接在 `closeableGroup.addCloseable(connections);` 后加 `closeableGroup.addCloseable(io);` 即可。
