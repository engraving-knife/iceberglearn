# 提交 1353：Core: Support DVs in DeleteLoader (#11481)

## 提交信息

- **序号**：1353 / 4088
- **哈希**：166edc7298825321f677e1e70cf88d7249e8035c
- **短哈希**：166edc729
- **日期**：2024-11-08（Fri Nov 8 18:09:49 2024 +0100）
- **作者**：Anton Okolnychyi <aokolnychyi@apache.org>
- **提交说明**：Core: Support DVs in DeleteLoader (#11481)
- **PR/Issue**：#11481

## 总体目的

Iceberg 表格式在 v3 引入 Deletion Vector（DV，删除向量）作为位置删除（position delete）的紧凑替代形态：DV 是 Puffin 文件中存储的一段 Roaring Bitmap，记录某个数据文件中被删除的行号集合，相比传统按 (file_path, pos) 行存储的位置删除文件，DV 体积更小、读取更快，且天然以数据文件为作用域（每个 DV 恰好对应一个 referenced data file）。

`DeleteLoader` 是读取侧加载删除文件的接口，`BaseDeleteLoader` 是其通用实现。此前 `BaseDeleteLoader.loadPositionDeletes` 只能处理传统的位置删除文件（POSITION_DELETES 内容类型的 DeleteFile），无法识别和加载 DV（Puffin 文件中的 DV blob）。这意味着 DV 写入路径（`BaseDVFileWriter`）已就绪，但读取路径还读不了 DV，DV 功能未闭环。

本提交让 `BaseDeleteLoader.loadPositionDeletes` 在传入的删除文件恰好是单个 DV 时，走专用的 DV 读取路径：直接从 Puffin 文件按 `contentOffset` 与 `contentSizeInBytes` 范围读取字节，反序列化为 `PositionDeleteIndex`，跳过传统位置删除文件的逐行物化流程；否则回退到原有位置删除读取逻辑。同时为 DV 读取路径补充参数校验（offset/length 非空、不超过 2GB、引用的数据文件路径匹配）。

## 如何达成设计目的

通过四处改动完成 DV 读取支持：

1. 在 `ContentFileUtil` 中新增 `containsSingleDV(Iterable<DeleteFile>)` 工具方法，判定传入的删除文件集合是否"恰好是一个 DV"（size==1 且该文件 `isDV`）。
2. 在 `BaseDeleteLoader.loadPositionDeletes` 中分流：若 `containsSingleDV` 为真，调用 `readDV(dv)` 直接读取 DV 字节并反序列化；否则调用 `getOrReadPosDeletes`（即原逻辑）。
3. 新增 `validateDV` 与 `readBytes` 私有方法：前者校验 DV 元数据合法性与引用关系，后者按 offset/length 范围读取字节（优先用 `RangeReadable` 流的 `readFully(offset, bytes)`，否则 `seek + readFully`）。
4. 更新 `DeleteLoader` 接口 javadoc，说明该方法现可接受 DV 或位置删除文件。
5. 在 `TestDVWriters` 中新增 4 个测试用例覆盖 DV 读取、DV 重写、V2→V3 升级后用 DV 替换位置删除、分区作用域位置删除与 DV 共存等场景，并把参数化格式版本从仅 v3 扩展为 v2/v3，以同时覆盖两种格式。

## 修改详情

### `core/src/main/java/org/apache/iceberg/util/ContentFileUtil.java`

**修改目的**：新增判定"单一 DV"的工具方法。

**工作逻辑**：

```java
public static boolean containsSingleDV(Iterable<DeleteFile> deleteFiles) {
  return Iterables.size(deleteFiles) == 1 && Iterables.all(deleteFiles, ContentFileUtil::isDV);
}
```

仅当删除文件集合恰好包含 1 个文件、且该文件是 DV（Puffin 格式且内容为 DV）时返回 true。这一判定用于 `BaseDeleteLoader` 的分流决策——只有"恰好一个 DV"才走 DV 快速路径，其他情况（多个 DV、DV 与位置删除混合、纯位置删除）仍走原逻辑。同时 import 了 `Iterables`。

### `data/src/main/java/org/apache/iceberg/data/BaseDeleteLoader.java`

**修改目的**：让 `loadPositionDeletes` 支持 DV 读取，并新增校验与字节读取辅助方法。

**工作逻辑**：

1. **分流入口**：`loadPositionDeletes` 现实现为

   ```java
   if (ContentFileUtil.containsSingleDV(deleteFiles)) {
     DeleteFile dv = Iterables.getOnlyElement(deleteFiles);
     validateDV(dv, filePath);
     return readDV(dv);
   } else {
     return getOrReadPosDeletes(deleteFiles, filePath);
   }
   ```

   原有逐文件加载并合并的逻辑被提取为 `getOrReadPosDeletes`（私有方法，原方法体不变）。

2. **DV 读取** `readDV`：调用 `loadInputFile.apply(dv)` 拿到 DV 所在 Puffin 文件的 `InputFile`，按 `dv.contentOffset()`（long）与 `dv.contentSizeInBytes()`（long→int）调用 `readBytes` 读取字节，再 `PositionDeleteIndex.deserialize(bytes, dv)` 反序列化为位置索引。注释说明：DV 当前不做缓存，因为现有 Puffin 读取器至少需要 3 次请求才能取回整个文件，缓存单个 DV 仅在多个 split 落在同一节点时才有收益，而任务本地性无法保证；位置删除文件因可能被多个数据文件共用，缓存仍有价值（保留在 `getOrReadPosDeletes` 路径内）。

3. **DV 校验** `validateDV`：四条 `Preconditions.checkArgument`：
   - `dv.contentOffset() != null`：offset 不能为 null；
   - `dv.contentSizeInBytes() != null`：length 不能为 null；
   - `dv.contentSizeInBytes() <= Integer.MAX_VALUE`：DV 不能超过 2GB（因 `readBytes` 用 `byte[]` 读取，长度受 int 限制）；
   - `filePath.toString().equals(dv.referencedDataFile())`：DV 必须引用当前数据文件（DV 是文件作用域的，引用错误的数据文件说明元数据不一致）。
   错误信息中用 `ContentFileUtil.dvDesc(dv)` 输出可读描述。

4. **字节读取** `readBytes`：打开 `SeekableInputStream`，分配 `byte[length]`：
   - 若流实现 `RangeReadable`（如 S3 等支持范围读的对象存储），调用 `rangeReadable.readFully(offset, bytes)` 直接按范围拉取；
   - 否则 `stream.seek(offset)` 后 `ByteStreams.readFully(stream, bytes)` 顺序读满。
   读取完毕关闭流，IO 异常包装为 `UncheckedIOException`。

5. **import**：新增 `RangeReadable`、`SeekableInputStream`、`Preconditions`、`ByteStreams`、`ContentFileUtil` 等。

### `data/src/main/java/org/apache/iceberg/data/DeleteLoader.java`

**修改目的**：更新接口 javadoc 以反映 DV 支持。

**工作逻辑**：`loadPositionDeletes` 的 javadoc 由 "Loads the content of position delete files..." 改为 "Loads the content of a deletion vector or position delete files..."，`@param deleteFiles` 由 "position delete files" 改为 "a deletion vector or position delete files"。接口签名不变。

### `data/src/test/java/org/apache/iceberg/io/TestDVWriters.java`

**修改目的**：覆盖 DV 读取、DV 重写、V2 位置删除与 V3 DV 共存等场景。

**工作逻辑**：

1. **参数化版本扩展**：`parameters()` 由 `{3}` 改为 `{2, 3}`，让所有测试在 v2 和 v3 表上各跑一遍。同时新增 `parquetFileFactory`（PARQUET 格式的输出文件工厂）用于写数据文件与位置删除文件（DV 仍用 PUFFIN 格式的 `fileFactory`）。

2. **`testBasicDVs` 增加 `assumeThat(formatVersion).isGreaterThanOrEqualTo(3)`**：原测试仅适用于 v3，因 DV 是 v3 特性，加 assume 防止在 v2 上误跑。同时在末尾补上 `commit(result)` 与 `assertRows(...)`，验证 DV 提交后读取结果正确（删除生效，剩余 `(11, aaa)`、`(12, aaa)`）。

3. **新增 `testRewriteDVs`**：先写一个数据文件（3 行），写第一个 DV 删除行 1，提交后验证剩余行 1、3；再写第二个 DV（通过 `PreviousDeleteLoader` 传入第一个 DV 作为"已有删除"），删除行 2，提交后验证第一个 DV 被替换（addedDeleteFiles=1、removedDeleteFiles=1），剩余行 1。覆盖 DV 重写（rewrite）场景。

4. **新增 `testRewriteFileScopedPositionDeletes`**：在 v2 表上写数据文件 + 文件作用域位置删除文件（删除行 0），验证剩余行 2、3；升级表到 v3 后，用 DV 替换该位置删除文件（删除行 1），验证位置删除文件被移除、DV 被加入，剩余行 3。覆盖 V2 位置删除 → V3 DV 的替换。

5. **新增 `testApplyPartitionScopedPositionDeletes`**：在 v2 表上写两个数据文件 + 一个分区作用域位置删除文件（同时对两个文件删除若干行），验证剩余行；升级到 v3 后，对其中一个数据文件写 DV（`PreviousDeleteLoader` 传入分区作用域位置删除文件，但 DV 只针对 dataFile2），验证 DV 提交后位置删除文件仍保留（因为分区作用域位置删除仍对另一个数据文件生效），并扫描验证：dataFile1 关联位置删除文件、dataFile2 关联 DV。覆盖分区作用域位置删除与 DV 共存场景。

6. **新增辅助方法**：
   - `commit(DeleteWriteResult)`：用 `RowDelta` 把 rewrittenDeleteFiles 移除、deleteFiles 加入并提交。
   - `assertRows(Iterable<T>)`：用 `actualRowSet("*")` 比对期望行集合。
   - `writePositionDeletes(FileWriterFactory, List<Pair<String, Long>>)`：用 parquet 文件工厂创建位置删除 writer，按 `(path, pos)` 列表写入位置删除记录并返回 `DeleteFile`。

## 小结

- **成效**：`BaseDeleteLoader` 现可在读取侧识别并加载 DV（Puffin 文件中的 Roaring Bitmap 删除向量），按 offset/length 范围读取并反序列化为 `PositionDeleteIndex`，跳过传统位置删除逐行物化；DV 读取带完整参数校验，且优先利用 `RangeReadable` 做范围读以适配对象存储。DV 写入（`BaseDVFileWriter`）与读取至此闭环。测试覆盖了 DV 基本读取、DV 重写、V2 位置删除→V3 DV 替换、分区作用域位置删除与 DV 共存四类场景。
- **影响范围**：4 个文件——`ContentFileUtil.java`（+5 行工具方法）、`BaseDeleteLoader.java`（+82 行 DV 读取与校验、原逻辑提取为 `getOrReadPosDeletes`）、`DeleteLoader.java`（javadoc 调整）、`TestDVWriters.java`（+221 行测试）。共 +310 / -3 行。涉及 core 与 data 模块，是 DV 功能的关键一环。
- **回迁到 1.4.x 的注意事项**：DV 是 Iceberg v3 表格式特性，1.4.x 维护分支基于 v1/v2 表格式，本身不支持 DV（`BaseDVFileWriter`、DV 相关元数据字段、v3 表格式类均不在 1.4.x 范围内）。回迁此提交需要同时回迁 DV 写入器、v3 表格式基础类、Puffin DV blob 读写、`DeleteFile` 的 `contentOffset`/`contentSizeInBytes`/`referencedDataFile` 等元数据字段，以及 `PositionDeleteIndex.deserialize` 的 DV 反序列化支持，工程量大且超出 1.4.x 的格式边界。**不建议回迁**——1.4.x 用户不会产生 DV，读取路径也不会遇到 DV，此提交对 1.4.x 无实际价值。
