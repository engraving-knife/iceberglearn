# 提交 1382：API, Core: Replace deprecated ContentFile#path usage with location (#11550)

## 提交信息

- **序号**：1382 / 4088
- **哈希**：50d310aef17908f03f595d520cd751527483752a
- **短哈希**：50d310aef
- **日期**：2024-11-15（Fri Nov 15 08:50:29 2024 -0700）
- **作者**：Amogh Jahagirdar <amoghj@apache.org>
- **提交说明**：API, Core: Replace deprecated ContentFile#path usage with location
- **PR/Issue**：#11550

## 总体目的

Iceberg 在 PR #11092（提交 257bad29d，2024-09-23）中已将 `ContentFile#path()` 标记为 `@Deprecated`（计划 2.0.0 移除），并新增了 `location()` 作为替代：

```java
// 旧 API（已废弃）
@Deprecated
CharSequence path();

// 新 API
default String location() {
  return path().toString();
}
```

两者的区别：

1. **返回类型**：`path()` 返回 `CharSequence`（实现上常为 Avro 的 `Utf8`，可变且会被 Avro reader 复用缓冲区），`location()` 返回 `String`（不可变、稳定）。
2. **使用便利性**：`CharSequence` 在大多数场景下需要再 `.toString()` 才能传给期望 `String` 的 API（如 `FileIO.newInputFile(String, long)`、`String.intern()`、日志格式化等），而 `location()` 直接返回 `String`，省去 `.toString()` 调用。
3. **安全性**：`Utf8` 实例在 Avro 迭代过程中会被复用（同一个 `Utf8` 对象的内部字节数组会被下一次 `read` 覆盖），若调用方持有 `path()` 返回的 `CharSequence` 引用而不做 `toString()`，后续迭代会看到被覆盖的值，导致难以排查的 bug。`location()` 通过 `path().toString()` 返回独立的 `String`，规避了这一陷阱。

本提交把 `api` 与 `core` 模块中所有内部对 `ContentFile#path()` 的调用替换为 `location()`，达成：

1. **清理 deprecation 警告**：内部代码不再使用已废弃 API，IDE 与 CI 的 deprecation 检查干净；
2. **为 2.0.0 移除 `path()` 铺路**：内部调用全部迁移完毕后，未来移除 `path()` 时只需处理外部用户调用；
3. **消除 `Utf8` 复用隐患**：少数原本依赖 `path().toString()` 的地方现在直接用 `location()`，语义更清晰；
4. **类型更精确**：断言中比较 `path()`（CharSequence）改为比较 `location()`（String），相等性判定更严格。

## 如何达成设计目的

机械式重构，逐文件把 `.path()` 替换为 `.location()`，把 `.path().toString()` 替换为 `.location()`（因为 `location()` 已返回 `String`）。涉及 52 个文件，+218/-216 行，模式高度一致：

- `file.path()` → `file.location()`；
- `file.path().toString()` → `file.location()`；
- `ContentFile::path`（方法引用）→ `ContentFile::location`；
- `entry.file().path()` → `entry.file().location()`；
- `task.file().path()` → `task.file().location()`；
- `task.deletes().get(0).path()` → `task.deletes().get(0).location()`。

不修改 `ContentFile` 接口本身（接口已在 #11092 中改好），只改调用方。`path()` 的默认实现仍保留在 `BaseFile` 等实现类中（因为 `location()` 默认实现就调用 `path()`），只是不再被内部代码直接调用。

## 修改详情

按模块分组说明代表性改动。

### API 模块

#### `api/src/main/java/org/apache/iceberg/DeleteFiles.java`（修改，+1/-1 行）

**修改目的**：`DeleteFiles.deleteFile(DataFile file)` 默认方法把 `deleteFile(file.path())` 改为 `deleteFile(file.location())`。`deleteFile` 接受 `CharSequence`（兼容 path），传入 `String`（location）同样匹配。

#### `api/src/main/java/org/apache/iceberg/io/FileIO.java`（修改，+4/-4 行）

**修改目的**：`FileIO` 接口的两个默认方法 `newInputFile(DataFile file)` 与 `newInputFile(DeleteFile file)`，把校验异常信息中的 `file.path()` 改为 `file.location()`，以及把 `newInputFile(file.path().toString(), file.fileSizeInBytes())` 改为 `newInputFile(file.location(), file.fileSizeInBytes())`。

#### `api/src/main/java/org/apache/iceberg/encryption/EncryptingFileIO.java`（修改，+3/-4 行）

**修改目的**：`EncryptingFileIO.newInputFile(ContentFile<?>)` 与 `wrap(ContentFile<?>)` 中三处 `file.path().toString()` 改为 `file.location()`。

### Core 模块 - 生产代码

#### `core/src/main/java/org/apache/iceberg/DataFiles.java` 与 `FileMetadata.java`（各 +1/-1 行）

**修改目的**：`DataFiles.Builder.copy(DataFile toCopy)` 与 `FileMetadata.Builder.copy(DeleteFile toCopy)` 中 `this.filePath = toCopy.path().toString()` 改为 `this.filePath = toCopy.location()`。这两个 builder 在拷贝文件元信息时把 `CharSequence` 转为 `String` 存储，现在直接用 `location()` 返回的 `String`。

#### `core/src/main/java/org/apache/iceberg/MergingSnapshotProducer.java`（修改，+8/-8 行）

**修改目的**：8 处冲突检测与提交校验中的 `entry.file().path().toString()` 改为 `entry.file().location().toString()`，以及 `Iterables.transform(deletes.referencedDeleteFiles(), ContentFile::path)` 改为 `ContentFile::location`，还有 `requiredDataFiles.contains(entry.file().path())` 改为 `.location()`。

**工作逻辑**：`MergingSnapshotProducer` 在 `validateDataFilesExist`、`validateNoConflictingDataFiles`、`validateNoConflictingDeleteFiles`、`validateDeletedDataFilesExist` 等校验路径中，会把冲突文件列表或缺失文件列表打印到 `ValidationException` 信息里。原来用 `path().toString()` 拼接，现改为 `location().toString()`（这里保留 `.toString()` 是因为 `Iterators.toString(Iterators.transform(...))` 需要 Object 参数，`location()` 返回的 String 本身就是 Object，`.toString()` 是冗余的但无害）。

`requiredDataFiles` 是一个 `CharSequenceSet`，`contains(entry.file().location())` 与原来 `contains(entry.file().path())` 行为一致（`CharSequenceSet` 内部按字符串内容比较）。

#### `core/src/main/java/org/apache/iceberg/ManifestFilterManager.java`（修改，+3/-3 行）

**修改目的**：3 处 `ContentFile::path` 方法引用改为 `ContentFile::location`，以及 `deletePaths.contains(file.path())` 改为 `.location()`。

**工作逻辑**：`ManifestFilterManager` 在过滤 manifest 时维护 `CharSequenceSet deletePaths` 标记待删除文件路径。`deletedFiles.stream().map(ContentFile::path).collect(Collectors.toCollection(CharSequenceSet::empty))` 改为 `.map(ContentFile::location)`——`CharSequenceSet` 接受任何 `CharSequence`，`String` 也是 `CharSequence`，行为等价。`deletePaths.contains(file.path())` 改为 `deletePaths.contains(file.location())`，同样等价。

#### `core/src/main/java/org/apache/iceberg/IncrementalFileCleanup.java`（修改，+2/-2 行）

**修改目的**：两处 `entry.file().path().toString()` 改为 `entry.file().location()`。

**工作逻辑**：`IncrementalFileCleanup` 在删除过期快照时收集待删除文件路径到 `filesToDelete` 列表。原代码注释"use toString to ensure the path will not change (Utf8 is reused)"说明了 `path()` 返回的 `Utf8` 会被 Avro reader 复用，必须 `toString()` 才能持有稳定引用。改用 `location()` 后，`location()` 默认实现内部已调用 `path().toString()`，返回稳定的 `String`，注释保留但不再有显式 `.toString()` 调用。

#### `core/src/main/java/org/apache/iceberg/CatalogUtil.java`（修改，+1/-1 行）

**修改目的**：`dropTable` 清理数据文件时 `entry.file().path().toString().intern()` 改为 `entry.file().location().intern()`。`String.intern()` 仍然有效（`location()` 返回 `String`），用于把路径字符串放入字符串池以减少内存占用（注释"intern the file path because the weak key map uses identity (==) instead of equals"）。

#### `core/src/main/java/org/apache/iceberg/ManifestFiles.java`（修改，+1/-1 行）

**修改目的**：`readPaths(ManifestFile, FileIO)` 方法中 `entry -> entry.file().path().toString()` 改为 `entry -> entry.file().location()`。`readPaths` 返回 `CloseableIterable<String>`，`location()` 直接返回 `String`。

#### 其他 Core 生产文件（每个 +1/-2 行到 +3/-3 行）

`BaseChangelogContentScanTask`、`BaseContentScanTask`、`BaseOverwriteFiles`、`ContentFileParser`、`SplitPositionDeletesScanTask`、`V1Metadata`、`V2Metadata`、`V3Metadata`、`InputFilesDecryptor`、`hadoop/Util`、`io/BaseTaskWriter`、`util/PartitionUtil` 均为同模式替换。`ContentFileParser` 中 `contentFile.path().toString()` 改为 `contentFile.location()`，用于 JSON 序列化时写 `file-path` 字段。

### Core 模块 - actions 子包

`BaseRewriteDataFilesAction`、`RewriteDataFilesCommitManager`、`RewritePositionDeletesCommitManager` 三处文件路径收集与日志打印的 `path().toString()` 改为 `location()`。

### Core 模块 - 测试代码

测试文件改动占总改动量的大头（约 30 个文件、~150 行），主要是断言中比较文件路径：

- `assertThat(task.file().path()).isEqualTo(FILE_A.path())` → `assertThat(task.file().location()).isEqualTo(FILE_A.location())`；
- `Iterables.transform(task.deletes(), ContentFile::path)` → `ContentFile::location`；
- `Sets.newHashSet(FILE_A.path(), FILE_B.path())` → `Sets.newHashSet(FILE_A.location(), FILE_B.location())`。

`DeleteFileIndexTestBase`（93 行改动）改动最多，覆盖所有扫描 + 删除文件索引的断言。`TestBase`（30 行）改动基础测试夹具中的路径比较。`TestRemoveSnapshots`（32 行）、`TestRewriteManifests`（18 行）、`TestRowDelta`（22 行）等覆盖快照删除、manifest 重写、行级 delta 等场景的断言。

`TestBaseIncrementalChangelogScan`（22 行）与 `TestV1ToV2RowDeltaDelete`（17 行）也大量替换路径断言。`DataTableScanTestBase`（27 行）覆盖数据表扫描测试。

### Hadoop 模块测试

`hadoop/TestCatalogUtilDropTable`（+3/-2 行）与 `hadoop/TestTableSerialization`（+2/-2 行）验证 drop table 与表序列化场景的路径断言。`catalog/CatalogTests`（+1/-1 行）更新通用 catalog 测试基类。

## 小结

- **成效**：API 与 Core 模块内部对已废弃 `ContentFile#path()` 的所有调用迁移到 `location()`，清除了内部 deprecation 警告，为 2.0.0 移除 `path()` 铺路。同时消除了 `Utf8` 复用隐患（少数原本依赖显式 `.toString()` 的地方现在由 `location()` 默认实现兜底）。涉及 52 个文件、+218/-216 行，纯机械重构，无行为变化。
- **影响范围**：仅 `api` 与 `core` 模块（含 `core` 的 actions 子包与 hadoop 测试）。`ContentFile` 接口本身未修改（已在 #11092 改好）。`path()` 仍保留为 public deprecated API，供外部用户使用。Flink/Spark/Trino 等引擎集成模块的 `path()` 调用不在本提交范围（应由各引擎模块自行迁移）。
- **回迁到 1.4.x 的注意事项**：
  1. **强依赖 PR #11092**：1.4.x 分支的 `ContentFile` 接口必须已有 `location()` 方法与 `path()` 的 `@Deprecated` 注解。若 1.4.x 未合入 #11092，本提交会编译失败（`location()` 方法不存在）。回迁前必须先回迁 #11092，它是单文件 12 行的纯接口变更，回迁成本极低；
  2. 若 1.4.x 已有 `location()`，本提交可整体 cherry-pick，因为纯属调用方替换，不引入新 API；
  3. 注意 `IncrementalFileCleanup` 中保留的注释"use toString to ensure the path will not change (Utf8 is reused)"在改用 `location()` 后略有过时（不再有显式 `.toString()`），但语义上仍正确（`location()` 内部做了 `toString()`），可保留或同步更新注释；
  4. `CatalogUtil` 中的 `.intern()` 调用保留，因为 `location()` 返回的是新 `String`，`intern()` 仍能将其放入字符串池；
  5. 测试文件改动量大但模式一致，cherry-pick 后若有冲突（因 1.4.x 测试代码可能与 main 有差异），需手工对齐；
  6. Flink/Spark 等引擎模块若也调用 `path()`，本提交不处理，1.4.x 上各引擎模块的 `path()` 调用可暂保留（deprecation 警告不影响功能）；
  7. 这是一次零风险重构（前提是 `location()` 已存在），建议在 1.4.x 上先回迁 #11092，再回迁本提交，最后跑全套测试验证。
