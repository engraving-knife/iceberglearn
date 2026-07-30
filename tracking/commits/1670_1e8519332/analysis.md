# 提交 1670：Data: Open file using stats in scan (#12151)

## 提交信息

- **序号**：1670 / 4088
- **哈希**：1e851933273bac7d299e6ffd3999f917e11ef569
- **短哈希**：1e8519332
- **日期**：2025-02-01（Sat Feb 1 19:14:01 2025 -0700）
- **作者**：Bryan Keller <bryanck@gmail.com>
- **提交说明**：Data: Open file using stats in scan (#12151)
- **PR/Issue**：#12151

## 总体目的

Iceberg 在扫描数据文件时需要通过 `FileIO.newInputFile(...)` 创建 `InputFile` 对象来读取文件内容。`InputFile` 需要知道文件长度（`getLength()`），因为 Parquet/ORC 等列式格式读取元数据时要从文件尾部 seek。

此前的 `GenericReader.openFile` 调用的是 `io.newInputFile(task.file().location())`——只传文件路径。对于 S3、GCS、Azure Blob 等对象存储，文件长度不会随路径一起返回，`FileIO` 实现需要额外发一次 HEAD 请求来获取文件大小，这会增加每次扫描的延迟与请求次数。

而 Iceberg 的 `DataFile` 元数据中已经记录了 `fileSizeInBytes`（文件写入时统计的大小）。`FileIO` 接口上已有 `default InputFile newInputFile(DataFile file)` 重载（在 main 分支上由 PR #9953/#10114 引入），其默认实现调用 `newInputFile(file.location(), file.fileSizeInBytes())`，把已知文件长度传给底层，从而避免 HEAD 请求。

本提交把 `GenericReader.openFile` 中的调用从 `io.newInputFile(task.file().location())` 改为 `io.newInputFile(task.file())`，让扫描路径利用 DataFile 中已记录的文件大小，减少对象存储上的 HEAD 请求，提升扫描性能。

## 如何达成设计目的

单行修改：把 `io.newInputFile(task.file().location())` 改为 `io.newInputFile(task.file())`。调用 `newInputFile(DataFile)` 后，默认实现会把 `file.location()` 与 `file.fileSizeInBytes()` 一起传给 `newInputFile(String, long)`，后者可避免对象存储实现再次 HEAD 获取长度。

## 修改详情

### `data/src/main/java/org/apache/iceberg/data/GenericReader.java`（修改，+1/-1 行）

**修改目的**：让 `openFile` 方法在创建 `InputFile` 时利用 `DataFile` 中已记录的文件统计信息（`fileSizeInBytes`），避免对象存储上额外的 HEAD 请求。

**工作逻辑**：

- 修改前：`InputFile input = io.newInputFile(task.file().location());`——只传路径，`FileIO` 实现需要自行获取文件长度；
- 修改后：`InputFile input = io.newInputFile(task.file());`——传整个 `DataFile`，`FileIO.newInputFile(DataFile)` 默认实现调用 `newInputFile(file.location(), file.fileSizeInBytes())`，把已知长度传给底层，对象存储实现（如 `S3FileIO`）可直接用该长度构造 `InputFile`，跳过 HEAD 请求。

## 小结

- **成效**：在 `iceberg-data` 模块的通用 Record 读取路径上，利用 `DataFile` 已有的文件大小统计避免对象存储 HEAD 请求，减少扫描延迟与请求开销。对 HDFS 等本地文件系统无影响（`getLength()` 通常不额外发请求）。
- **影响范围**：仅 `data` 模块的 `GenericReader` 一行修改，不改变任何读取逻辑或数据语义。
- **回迁到 1.4.x 的注意事项**：**关键**——1.4.x 分支的 `FileIO` 接口**没有** `newInputFile(DataFile)` 这个 default 方法（该方法由 main 分支的 PR #9953/#10114 引入）。直接 cherry-pick 本提交到 1.4.x 会导致编译失败。回迁时必须同时引入 `FileIO.newInputFile(DataFile)`（以及可能的 `newInputFile(DeleteFile)` / `newInputFile(ManifestFile)`）default 方法，或者改为调用 `io.newInputFile(task.file().location(), task.file().fileSizeInBytes())`（直接使用已有的 `newInputFile(String, long)` 重载）以达到相同效果。
