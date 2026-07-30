# 提交 0924：Core: Use bulk deletes when removing old metadata files (#10679)

## 提交信息

- **序号**：0924 / 4088
- **哈希**：5455d30d9b6e0409d517138e9b80d6831ffb233b
- **短哈希**：5455d30d9
- **日期**：2024-07-11 20:30:35 -0700
- **作者**：Hongyue/Steve Zhang <steveiszhy@gmail.com>
- **提交说明**：Core: Use bulk deletes when removing old metadata files (#10679)
- **PR/Issue**：#10679

## 总体目的

Iceberg 在每次表提交后会保留一定数量的历史 metadata 文件（受 `write.metadata.previous-versions-max` 控制），超出阈值时需要删除被淘汰的旧 metadata 文件，以避免元数据目录无限膨胀。原先 `BaseMetastoreTableOperations` 在删除这些旧 metadata 文件时，是逐个文件调用 `FileIO.deleteFile(...)`，即一个文件一次 IO 请求。

对于对象存储（如 S3、GCS、Azure Blob）这类具有较高单次请求延迟的存储，逐个删除大量文件会显著放大提交尾延迟，尤其当历史版本数较多时，提交后的清理阶段会消耗可观的请求次数与时间。本提交的目的就是在底层 `FileIO` 支持批量删除（实现 `SupportsBulkOperations` 接口）时，改用一次批量调用 `deleteFiles(Iterable)` 删除全部待删文件，从而显著减少请求次数、降低清理耗时和成本。

## 如何达成设计目的

在删除旧 metadata 文件的代码点（`BaseMetastoreTableOperations` 中清理 `removedPreviousMetadataFiles` 的逻辑）加入分支判断：若当前 `FileIO` 是 `SupportsBulkOperations` 的实例，则将待删文件集合通过 `Iterables.transform(...)` 提取出文件路径，一次性调用 `deleteFiles(...)` 批量删除；否则保留原有的逐个删除路径（`Tasks.foreach(...).noRetry().suppressFailureWhenFinished().run(...)`）作为兜底。这样既能在支持批量删除的存储后端上获得性能收益，又不会破坏只支持单文件删除的 `FileIO` 实现。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseMetastoreTableOperations.java`

**修改目的**：在删除被淘汰的旧 metadata 文件时优先使用批量删除。

**工作逻辑**：导入 `SupportsBulkOperations` 和 `Iterables`；在计算出 `removedPreviousMetadataFiles`（已排除 `metadata.previousFiles()` 中需保留的部分）后，先判断 `io() instanceof SupportsBulkOperations`：

- 若是，则强转为 `SupportsBulkOperations`，用 `Iterables.transform(removedPreviousMetadataFiles, TableMetadata.MetadataLogEntry::file)` 把 `MetadataLogEntry` 集合转换为文件路径字符串集合，调用 `deleteFiles(...)` 一次性批量删除；
- 若不是，则沿用原有逻辑：`Tasks.foreach(...).noRetry().suppressFailureWhenFinished()` 并在每个失败时 `LOG.warn`，逐个调用 `io().deleteFile(previousMetadataFile.file())`。

注意批量删除分支没有显式的 per-file 失败日志，由底层 `FileIO` 实现自行决定如何处理部分失败（`SupportsBulkOperations.deleteFiles` 的契约）。

## 小结

- **成效**：在支持批量删除的 `FileIO`（如 S3FileIO）上，将旧 metadata 文件的清理由"逐文件删除"改为"一次批量删除"，减少对象存储请求次数与提交尾延迟；对不支持批量删除的 `FileIO` 保持原有行为不变，向后兼容。
- **影响范围**：仅 `core` 模块的 `BaseMetastoreTableOperations.java` 一个文件，+17/-8，影响所有继承自 `BaseMetastoreTableOperations` 的元存储 Catalog（HiveCatalog、JdbcCatalog、REST Catalog 等）在提交后清理旧 metadata 文件的路径。
- **回迁到 1.4.x 的注意事项**：回迁风险低，前提是 1.4.x 已存在 `org.apache.iceberg.io.SupportsBulkOperations` 接口以及对应 `FileIO` 实现的 `deleteFiles(Iterable)` 方法。需确认 1.4.x 上该接口与方法签名与本提交一致；若 1.4.x 较老尚未引入 `SupportsBulkOperations`，则本提交无法直接 cherry-pick，需要连同该接口一起回迁。另外批量删除分支不再 per-file 打印失败日志，回迁后若依赖该日志排查问题需留意。
