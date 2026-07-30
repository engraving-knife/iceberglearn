# 提交 1851：Core: Fix support for GenericManifestFile intex projection (#12522)

## 提交信息

- **序号**：1851 / 4088
- **哈希**：18de78eacba1f64de88fbe91c42ea1740377a1d2
- **短哈希**：18de78eac
- **日期**：2025-03-14 10:19:44 +0100
- **作者**：Daniel Weeks
- **提交说明**：Core: Fix support for GenericManifestFile intex projection (#12522)
- **PR/Issue**：#12522

## 总体目的

Iceberg 在读取 manifest list（Avro 文件，每条记录是一个 `GenericManifestFile`）时支持列投影（column projection）——即只反序列化需要的字段（如只读 `path`、`length`、`spec_id`、`snapshot_id`，不读 partition 摘要、key metadata 等），以减少 IO 与内存开销。投影通过把"投影后的字段序号"映射回"完整 schema 中的字段序号"来实现（`fromProjectionPos` 机制，后来被抽取到 `SupportsIndexProjection` 父类）。

本提交修复的 bug：在 main 分支此前的重构里，`GenericManifestFile` 改为继承 `SupportsIndexProjection`，但用于 Avro 反射实例化的构造函数（被 Avro 反射调用以构建读取容器）只调用了 `super(ManifestFile.schema().columns().size())`，仅传入完整 schema 的列数，没有传入投影 schema。这导致 `SupportsIndexProjection` 无法建立投影字段→完整字段的索引映射，投影读取时字段位置错乱，要么读到 null，要么读到错误字段的值。

修复方案是把这个 Avro 反射构造函数改为接收 `Types.StructType projectedSchema`，并调用 `super(ManifestFile.schema().asStruct(), projectedSchema)`，让父类同时拿到完整 schema 与投影 schema，从而正确计算索引映射。

## 如何达成设计目的

1. 删除旧的 `GenericManifestFile(InputFile file, int specId)` 构造函数（它错误地承担了 Avro 反射入口的角色，且只传列数给父类）。该构造函数原本的字段初始化（`manifestPath = file.location()` 等）随构造函数一并删除——这些字段在反射读取场景下本就该由 Avro 通过 `set(...)` 回填，不应在构造时预设。
2. 新增 `GenericManifestFile(Types.StructType projectedSchema)` 构造函数，标注注释 "Used by Avro reflection to instantiate this class when reading manifest files."，调用 `super(ManifestFile.schema().asStruct(), projectedSchema)` 把完整 schema 与投影 schema 都交给父类，由父类建立投影索引映射。`this.avroSchema = AVRO_SCHEMA` 保留默认值。
3. 新增测试 `testManifestListIndexProjection`：构造一个含单个 manifest 的 manifest list 文件，用只含 `PATH/LENGTH/SPEC_ID/SNAPSHOT_ID` 4 列的投影 schema 读取，断言：投影字段非 null；未投影的数值字段（sequenceNumber、minSequenceNumber、content）返回默认值（0/0/DATA）；未投影的可空字段（addedFilesCount、partitions、keyMetadata 等）返回 null。

## 修改详情

### `core/src/main/java/org/apache/iceberg/GenericManifestFile.java` (修改, +5/-21 lines)

**修改目的**：修复 Avro 反射读取 manifest list 时的投影索引映射。

**工作逻辑**：删除 `GenericManifestFile(InputFile file, int specId)` 构造函数（其 `super(ManifestFile.schema().columns().size())` 调用是 bug 根因——只传列数无法建立投影映射）。新增 `GenericManifestFile(Types.StructType projectedSchema)` 构造函数，调用 `super(ManifestFile.schema().asStruct(), projectedSchema)` 把完整 schema 和投影 schema 都传给 `SupportsIndexProjection`，由父类计算 `fromProjectionPos` 映射。同时新增 `import org.apache.iceberg.types.Types;`。注释明确标注此构造函数"Used by Avro reflection to instantiate this class when reading manifest files."

### `core/src/test/java/org/apache/iceberg/TestTableMetadata.java` (修改, +58 lines)

**修改目的**：新增回归测试覆盖 manifest list 投影读取。

**工作逻辑**：`testManifestListIndexProjection` 步骤：
1. 用 `createManifestListWithManifestFile(previousSnapshotId, null, "file:/tmp/manifest1.avro")` 构造一个含 1 条 manifest 记录的 manifest list 文件。
2. 构造投影 schema：`ManifestFile.schema().select(PATH, LENGTH, SPEC_ID, SNAPSHOT_ID)`，只选 4 个字段。
3. 用 `InternalData.read(FileFormat.AVRO, localInput(location)).setRootType(GenericManifestFile.class).project(manifestProjection).reuseContainers().build()` 读取。
4. 断言读到的 1 条 manifest：`path/length/partitionSpecId/snapshotId` 均 non-null；`sequenceNumber/minSequenceNumber` 为 0、`content` 为 `DATA`（默认值）；`addedFilesCount/existingFilesCount/deletedFilesCount/addedRowsCount/existingRowsCount/deletedRowsCount/partitions/keyMetadata` 均 null（未投影）。

新增 `import org.apache.iceberg.io.CloseableIterable;` 用于 try-with-resources 读取。

## 小结

- **成效**：修复了 manifest list 投影读取时字段错位的 bug，使列投影能正确工作；新增回归测试防止再次退化。
- **影响范围**：core 模块，2 个文件、+62/-17 行。仅影响 manifest list 读取路径（带投影的场景），非投影读取不受影响。
- **回迁到 1.4.x 的注意事项**：**不建议直接回迁**。本提交依赖 main 分支上把 `fromProjectionPos` 抽取到 `SupportsIndexProjection` 父类的重构（1.4.x 的 `GenericManifestFile` 当前仍是内联 `fromProjectionPos` 实现，构造函数签名是 `GenericManifestFile(Schema avroSchema)` 而非 `GenericManifestFile(Types.StructType projectedSchema)`）。若 1.4.x 也观察到投影读取 bug，需先回迁 `SupportsIndexProjection` 重构，再回迁本修复；否则应针对 1.4.x 的内联实现单独修复 `GenericManifestFile(Schema avroSchema)` 中的投影逻辑。需先确认 1.4.x 是否存在同样的投影 bug。
