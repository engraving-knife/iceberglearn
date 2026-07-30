# 提交 0844：Spark: Use bulk deletes in rewrite manifests action (#10343)

## 提交信息
- **序号**：0844 / 4088
- **哈希**：d8f26caa97b5c06ee3a428b5c0868762a6f553a3
- **短哈希**：d8f26caa9
- **日期**：2024-06-17
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：Spark: Use bulk deletes in rewrite manifests action (#10343)
- **PR/Issue**：#10343

## 总体目的

本提交是针对 Spark 模块 `RewriteManifestsSparkAction`（清单重写动作）的性能优化，目的是让该动作在清理旧清单文件时，能够利用底层 `FileIO` 的批量删除能力（`SupportsBulkOperations`），而不是始终走"逐个文件删除 + 工作线程池并发"的路径。

清单重写（rewrite manifests）是 Iceberg 表维护操作，它会重新组织表的 manifest 文件（例如合并小 manifest、按分区重新布局），完成后需要删除被替换的旧 manifest 文件。在云原生对象存储（如 AWS S3、GCS、Azure ADLS）上，这些存储提供了原生的批量删除 API（如 S3 的 DeleteObjects 一次可删除最多 1000 个对象），相比逐个 `DeleteObject` 请求可以显著降低请求数量、网络往返延迟和成本。Iceberg 在 `api` 模块定义了 `SupportsBulkOperations` 接口（继承 `FileIO`），由具备批量删除能力的 `FileIO` 实现（`S3FileIO`、`ADLSFileIO`、`GCSFileIO`、`HadoopFileIO`、`ResolvingFileIO` 等）标记实现，调用方通过 `instanceof` 检测后可走批量路径。

在本次提交之前，`RewriteManifestsSparkAction.deleteFiles(Iterable<String>)` 始终使用 `Tasks.foreach(locations).executeWith(ThreadPools.getWorkerPool()).noRetry().suppressFailureWhenFinished().onFailure(...).run(location -> table.io().deleteFile(location))` 模式，即把每个清单文件作为独立的 `deleteFile` 调用提交到工作线程池并发执行，没有利用 `SupportsBulkOperations.deleteFiles(Iterable)` 的批量能力。本提交让该动作在 `table.io() instanceof SupportsBulkOperations` 时走批量删除路径，否则回退到原来的逐个删除路径，与 `ExpireSnapshotsSparkAction`、`DeleteReachableFilesSparkAction` 等同类 Spark 动作的实现模式保持一致。

## 如何达成设计目的

提交通过在 `deleteFiles` 方法中引入 `instanceof SupportsBulkOperations` 分支判断，复用父类 `BaseSparkAction` 已有的批量删除辅助方法达成目的。工作逻辑如下：

1. **import 调整**：新增 `import org.apache.iceberg.io.SupportsBulkOperations;`，移除不再直接使用的 `import org.apache.iceberg.util.Tasks;`（`Tasks` 改由父类辅助方法内部使用）。同时新增 `import org.apache.iceberg.relocated.com.google.common.collect.Iterables;`（若未导入）。

2. **`deleteFiles` 方法重写**：原方法体直接用 `Tasks.foreach(...)` 编排逐个删除，改为：
   ```java
   private void deleteFiles(Iterable<String> locations) {
     Iterable<FileInfo> files =
         Iterables.transform(locations, location -> new FileInfo(location, MANIFEST));
     if (table.io() instanceof SupportsBulkOperations) {
       deleteFiles((SupportsBulkOperations) table.io(), files.iterator());
     } else {
       deleteFiles(
           ThreadPools.getWorkerPool(), file -> table.io().deleteFile(file), files.iterator());
     }
   }
   ```
   关键步骤：
   - **路径转 `FileInfo`**：使用 `Iterables.transform` 把 `Iterable<String>` 转换为 `Iterable<FileInfo>`，每个 `FileInfo` 携带路径和类型 `MANIFEST`（`MANIFEST` 是 `BaseSparkAction` 定义的受保护常量 `"Manifest"`）。`FileInfo` 是 `BaseSparkAction` 的内部类型，用于在删除时区分文件类型并记录删除统计。
   - **批量分支**：当 `table.io()` 实现了 `SupportsBulkOperations` 时，调用父类 `BaseSparkAction.deleteFiles(SupportsBulkOperations io, Iterator<FileInfo> files)`。该方法将文件按 `DELETE_GROUP_SIZE`（10 万）分批，每批按文件类型分组后调用 `io.deleteFiles(paths)` 批量删除，捕获 `BulkDeletionFailureException` 统计失败数，返回 `DeleteSummary`。这会触发底层 `FileIO`（如 `S3FileIO`）的原生批量删除 API。
   - **回退分支**：当 `table.io()` 不支持批量删除时，调用父类 `BaseSparkAction.deleteFiles(ExecutorService, Consumer<String>, Iterator<FileInfo>)`，该方法使用 `Tasks.foreach(files).retry(DELETE_NUM_RETRIES).stopRetryOn(NotFoundException.class).suppressFailureWhenFinished().executeWith(executorService).run(fileInfo -> deleteFunc.accept(path))`，即在工作线程池上并发逐个删除，并带重试和失败抑制。注意此处的回退路径相比原实现增加了重试机制（`retry(DELETE_NUM_RETRIES)`、`stopRetryOn(NotFoundException.class)`），与其它 Spark 动作的非批量删除路径一致。
   - **忽略返回值**：`RewriteManifestsSparkAction.deleteFiles` 是 `void` 返回，不消费父类方法返回的 `DeleteSummary`（清单重写动作不向上报告删除统计），仅依赖日志记录。

3. **三份代码同步修改**：提交同时修改 `spark/v3.3`、`spark/v3.4`、`spark/v3.5` 三个 Spark 版本目录下的 `RewriteManifestsSparkAction.java`，改动完全一致（v3.4 和 v3.5 文件在该方法上代码相同）。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`
**修改目的**：让清单重写动作在 FileIO 支持批量删除时走批量路径，否则回退到带重试的逐个删除路径。
**工作逻辑**：
- 新增 `import org.apache.iceberg.io.SupportsBulkOperations;`，移除 `import org.apache.iceberg.util.Tasks;`。
- `deleteFiles(Iterable<String> locations)` 方法体重写：先用 `Iterables.transform(locations, location -> new FileInfo(location, MANIFEST))` 把路径列表转为 `FileInfo` 列表（类型标记为 `MANIFEST`），再用 `instanceof SupportsBulkOperations` 判断走批量分支（调用父类 `deleteFiles((SupportsBulkOperations) table.io(), files.iterator())`）还是非批量分支（调用父类 `deleteFiles(ThreadPools.getWorkerPool(), file -> table.io().deleteFile(file), files.iterator())`）。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`
**修改目的**：同 v3.3，同步启用批量删除。
**工作逻辑**：与 v3.3 完全相同的 import 调整和 `deleteFiles` 方法重写。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java`
**修改目的**：同 v3.3/v3.4，同步启用批量删除。
**工作逻辑**：与 v3.4 完全相同（v3.5 文件在该方法上与 v3.4 一致）。

## 小结
- **成效**：让 `RewriteManifestsSparkAction` 在底层 `FileIO` 支持批量删除（`SupportsBulkOperations`）时走原生批量删除 API（如 S3 DeleteObjects），显著降低云存储场景下清单重写后清理旧 manifest 的请求数量、延迟和成本；同时与 `ExpireSnapshotsSparkAction`、`DeleteReachableFilesSparkAction` 等同类 Spark 动作的删除路径实现保持一致，统一了删除编排模式。非批量场景下回退路径还顺带引入了重试机制（`retry(DELETE_NUM_RETRIES).stopRetryOn(NotFoundException.class)`），相比原先的 `noRetry()` 提升了删除的鲁棒性。
- **影响范围**：仅影响 `spark/v3.3`、`spark/v3.4`、`spark/v3.5` 三个版本目录下的 `RewriteManifestsSparkAction.java`（产品代码），不触及测试代码。依赖父类 `BaseSparkAction` 已存在的 `deleteFiles(SupportsBulkOperations, Iterator<FileInfo>)` 和 `deleteFiles(ExecutorService, Consumer<String>, Iterator<FileInfo>)` 辅助方法以及 `FileInfo`、`MANIFEST` 常量，这些基础设施在 main 分支已就绪。
- **回迁注意事项**：回迁到 1.4.x 时需注意：(1) 1.4.x 分支的 `BaseSparkAction` 必须已提供 `deleteFiles(SupportsBulkOperations, Iterator<FileInfo>)` 和 `deleteFiles(ExecutorService, Consumer<String>, Iterator<FileInfo>)` 两个受保护方法，否则本提交的调用会编译失败——需先回迁 `BaseSparkAction` 的相关基础设施（可能在更早的提交中引入）。(2) `FileInfo` 类和 `MANIFEST` 常量需在 1.4.x 的 `BaseSparkAction` 中存在。(3) 1.4.x 上若 `RewriteManifestsSparkAction` 与 main 分支已有结构差异（如方法签名、字段名），需手动对齐。(4) 本提交同时改 v3.3/v3.4/v3.5 三份代码，回迁时若 1.4.x 只支持部分 Spark 版本，按需 cherry-pick 对应版本目录即可。(5) 非批量回退路径引入的重试行为变更（从 `noRetry` 改为 `retry(DELETE_NUM_RETRIES)`）可能影响某些依赖"删除失败立即返回"行为的测试，回迁后需观察相关测试是否仍然通过。
