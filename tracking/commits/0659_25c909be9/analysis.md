# 提交 0659：API: Fix default FileIO#newInputFile ManifestFile, DataFile and DeleteFile implementations (#9953)

## 提交信息

- **序号**：0659 / 4088
- **哈希**：25c909be90d448ebbd070a20726ee2e63f48c717
- **短哈希**：25c909be9
- **日期**：2024-04-04 14:02:50 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：API: Fix default FileIO#newInputFile ManifestFile, DataFile and DeleteFile implementations (#9953)
- **PR/Issue**：#9953

## 总体目的

这个提交修复了 `FileIO` 接口中三个默认方法（`newInputFile(DataFile)`、`newInputFile(DeleteFile)`、`newInputFile(ManifestFile)`）的两个缺陷：一个是性能问题，一个是错误消息格式问题。

**性能缺陷（主要）**：`DataFile`、`DeleteFile`、`ManifestFile` 这三类文件对象本身就已经携带了文件大小信息（`DataFile`/`DeleteFile` 通过 `fileSizeInBytes()`，`ManifestFile` 通过 `length()`）。然而这三个默认方法原本调用的是单参数版本 `newInputFile(path.toString())`，即只传路径、不传长度。`FileIO` 接口同时还提供了一个带长度的重载 `default InputFile newInputFile(String path, long length)`（默认实现回退到单参数版本）。当只传路径时，具体的 `FileIO` 实现（如 `S3FileIO`）在真正需要文件长度（例如读取文件时）就必须向对象存储发起一次额外的 `HEAD` 请求去探查文件大小。由于文件元数据里已经有大小，这纯属浪费——每次读数据文件/删除文件/清单文件都多一次网络往返，在大规模扫描场景下代价显著。

**错误消息缺陷（次要）**：这三个默认方法里的 `Preconditions.checkArgument` 在校验"文件未加密"时，错误消息使用了 SLF4J 风格的占位符 `{}`（例如 `"Cannot decrypt data file: {} (use EncryptingFileIO)"`）。但这里用的是 Iceberg 重定位后的 Guava `Preconditions`（`org.apache.iceberg.relocated.com.google.common.base.Preconditions`），它内部使用 `String.format` 风格的 `%s` 占位符，并不识别 `{}`。结果是一旦真的触发该校验失败，错误消息里的 `{}` 不会被替换成实际路径，而是原样输出字面量 `{}`，导致用户看到的错误信息缺少关键的文件路径，难以定位问题。

## 如何达成设计目的

整体策略是：让这三个默认方法优先调用带长度参数的 `newInputFile(path, length)` 重载，把文件对象已携带的大小信息透传给底层实现，从而让 `S3FileIO` 等实现能直接复用该长度、跳过 `HEAD` 请求；同时把错误消息的占位符从 `{}` 统一改为 `%s`，使路径能在校验失败时正确插值。生产代码改动集中在 `FileIO` 接口，仅 6 行替换。配套在 `TestS3FileIO` 中新增两个测试，用 S3 mock 验证读取 `DataFile`/`ManifestFile` 时返回的长度来自文件统计信息、且确实没有发起 `headObject` 请求，从而锁定"省去 HEAD"这一行为。

## 修改详情

### `api/src/main/java/org/apache/iceberg/io/FileIO.java`

**修改目的**：让三个默认方法透传已知的文件长度，并修正错误消息占位符。

**工作逻辑**：

接口中存在如下重载关系（修改后状态）：
- `InputFile newInputFile(String path);` —— 抽象方法，不带长度。
- `default InputFile newInputFile(String path, long length)` —— 带长度重载，默认回退到单参数版本。
- `default InputFile newInputFile(DataFile file)` / `newInputFile(DeleteFile file)` / `newInputFile(ManifestFile manifest)` —— 本次修复的三个便捷方法。

三个便捷方法的改动一致，每处包含两点：

1. **占位符修正**：`"Cannot decrypt data file: {} (use EncryptingFileIO)"` → `"Cannot decrypt data file: %s (use EncryptingFileIO)"`（delete file、manifest 同理）。改为 `%s` 后，Guava `Preconditions` 会用 `String.format` 语义把 `file.path()`/`manifest.path()` 正确代入消息。

2. **长度透传**：
   - `newInputFile(DataFile)`：由 `return newInputFile(file.path().toString());` 改为 `return newInputFile(file.path().toString(), file.fileSizeInBytes());`
   - `newInputFile(DeleteFile)`：由 `return newInputFile(file.path().toString());` 改为 `return newInputFile(file.path().toString(), file.fileSizeInBytes());`
   - `newInputFile(ManifestFile)`：由 `return newInputFile(manifest.path());` 改为 `return newInputFile(manifest.path(), manifest.length());`

   这样，凡是在 `newInputFile(String, long)` 上做了优化（直接使用传入长度、不再发 HEAD）的 `FileIO` 实现，都能从这个便捷方法受益。对未覆盖带长度重载的实现而言，默认实现仍回退到单参数版本，行为与改动前等价，因而完全向后兼容。

### `aws/src/test/java/org/apache/iceberg/aws/s3/TestS3FileIO.java`

**修改目的**：验证修复后通过 `DataFile`/`ManifestFile` 读取文件时不再发起 `headObject` 请求，且返回的长度来自文件统计信息。

**工作逻辑**：

新增两个测试，均借助 `S3MockExtension` 提供的 `s3mock` mock：

1. **`testInputFileWithDataFile`**：
   - 构造一个 `DataFile`，路径为 `s3://bucket/path/to/data-file.parquet`，`fileSizeInBytes` 设为 `123L`（注意：随后真实写入的数据 `"testing"` 仅 7 字节，故意与声明的 123L 不同，以证明长度取自统计信息而非真实对象大小）。
   - 用 `s3FileIO.newOutputFile(location).create()` 写入 `"testing"`。
   - 调用 `s3FileIO.newInputFile(dataFile)` 取得 `InputFile`，随后 `reset(s3mock)` 清除之前的交互记录。
   - 断言 `inputFile.getLength()` 等于 `123L`（来自 `DataFile` 的统计，而非 HEAD 探测到的 7 字节）。
   - 断言 `verify(s3mock, never()).headObject(any(HeadObjectRequest.class))` —— 证明获取长度时未发起任何 HEAD 请求。

2. **`testInputFileWithManifest`**：
   - 构造一个 `DataFile` 并通过 `ManifestFiles.write(...)` 写出一个真实的清单文件（`ManifestWriter`），关闭后得到 `ManifestFile` 对象（其 `length()` 反映清单文件真实大小）。
   - 调用 `s3FileIO.newInputFile(manifest)` 取得 `InputFile`，`reset(s3mock)`。
   - 断言 `inputFile.getLength()` 等于 `manifest.length()`。
   - 断言 `verify(s3mock, never()).headObject(any(HeadObjectRequest.class))`。

两个测试共同确立了修复的核心收益：读取这些文件时长度直接来自元数据，不再触发额外的对象存储探测请求。

## 小结

- **成效**：成功达成目的。修复后，`FileIO` 的三个便捷默认方法会透传已知文件长度，使 `S3FileIO` 等实现能跳过冗余的 `HEAD` 请求，减少读取数据/删除/清单文件时的网络往返；同时错误消息占位符从 `{}` 改为 `%s`，使校验失败时能正确显示文件路径。新增测试以 mock 验证了"无 HEAD 请求"与"长度取自统计"两点。
- **影响范围**：`api` 模块的 `FileIO` 接口默认方法（影响所有 `FileIO` 实现的调用方），以及 `aws` 模块的测试。对任何覆盖了 `newInputFile(String, long)` 的实现（如 `S3FileIO`）带来直接性能收益；对未覆盖该重载的实现行为不变（向后兼容）。
- **回迁到 1.4.x 的注意事项**：接口默认方法改动向后兼容，可安全回迁。回迁时需确认 1.4.x 分支上 `FileIO` 已存在 `newInputFile(String, long)` 重载（该重载在本提交前已存在，默认回退到单参数版本），否则需一并引入。测试回迁需 1.4.x 上 `TestS3FileIO` 使用相同的 `S3MockExtension`（`s3mock` 字段）与可用的 `ManifestFiles.write` API；若 mock 框架或 S3 mock 用法有差异，测试部分需相应调整，但生产代码本身无回迁风险。
