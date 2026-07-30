# 提交 3578：Core: Add implementations of v4 TrackedFile interfaces (#15854)

## 提交信息

- **序号**：3578 / 4088
- **哈希**：52092cd1109cba1e2f0c87959775e5f883a2cfd0
- **短哈希**：52092cd11
- **日期**：2026-04-23 14:17:44 -0700
- **作者**：Anoop Johnson
- **提交说明**：Core: Add implementations of v4 TrackedFile interfaces (#15854)
- **PR/Issue**：#15854

## 总体目的

该提交为 Iceberg 格式版本 4 的 TrackedFile 相关接口添加了具体的实现类。Iceberg v4 引入了"跟踪文件"（TrackedFile）的概念，用于在元数据中跟踪数据文件、删除向量（DeletionVector）、清单信息（ManifestInfo）和跟踪元数据（Tracking）等结构化信息。之前这些接口已定义但缺少具体的 `StructLike` 实现，无法在 Avro 序列化/反序列化等场景中使用。

该提交新增了四个 StructLike 实现类：`DeletionVectorStruct`、`ManifestInfoStruct`、`TrackedFileStruct` 和 `TrackingStruct`，它们都继承自 `SupportsIndexProjection`，支持字段索引投影（允许按需读取部分字段）。同时为 `DeletionVector` 和 `ManifestInfo` 接口添加了 `copy()` 方法，为 `FileContent` 枚举添加了 `lowerCaseName()` 方法，并从 `TrackedFile` 接口移除了 `manifestLocation()` 和 `manifestPos()` 方法（这两个方法不属于 TrackedFile 的持久化字段）。

## 如何达成设计目的

每个 StructLike 实现类采用相同的模式：
1. 定义 `BASE_TYPE`（完整字段列表的 StructType），用于索引投影的基础。
2. 为每个字段声明私有成员变量，初始化为 -1 或 null。
3. 实现 `internalGet` 和 `internalSet` 方法，通过 switch-case 按位置索引读写字段。
4. 实现 `copy()` 方法进行深拷贝（对 byte[] 等可变类型进行数组复制）。
5. 实现 `toString()` 用于调试。
6. 构造函数接收 `Types.StructType` 参数（实际投影后的类型），传递给 `SupportsIndexProjection` 父类。

## 修改详情

### `api/src/main/java/org/apache/iceberg/FileContent.java` (+8/-1 lines)

**修改目的**：为 FileContent 枚举添加 lowerCaseName 方法。

**工作逻辑**：
新增 `lowerCaseName` 字段（在构造时通过 `name().toLowerCase(Locale.ROOT)` 计算）和 `lowerCaseName()` 方法。用于序列化时以小写形式表示内容类型名称。

### `core/src/main/java/org/apache/iceberg/DeletionVector.java` (+3/-0 lines)

**修改目的**：为 DeletionVector 接口添加 copy() 方法声明。

**工作逻辑**：
新增 `DeletionVector copy()` 方法声明，用于创建删除向量的副本。

### `core/src/main/java/org/apache/iceberg/DeletionVectorStruct.java` (+127/-0 lines, new file)

**修改目的**：DeletionVector 的 StructLike 实现。

**工作逻辑**：
可变 StructLike 实现，包含 `location`（String）、`offset`（long）、`sizeInBytes`（long）、`cardinality`（long）四个字段。支持通过索引读写，`copy()` 创建新实例，`internalSet` 中 location 值通过 `value.toString()` 强制转为 String。

### `core/src/main/java/org/apache/iceberg/ManifestInfo.java` (+3/-0 lines)

**修改目的**：为 ManifestInfo 接口添加 copy() 方法声明。

### `core/src/main/java/org/apache/iceberg/ManifestInfoStruct.java` (+227/-0 lines, new file)

**修改目的**：ManifestInfo 的 StructLike 实现。

**工作逻辑**：
包含 11 个字段：addedFilesCount、existingFilesCount、deletedFilesCount、replacedFilesCount、addedRowsCount、existingRowsCount、deletedRowsCount、replacedRowsCount、minSequenceNumber、dv（byte[]）、dvCardinality（Long）。`dv` 字段在 `internalSet` 中通过 `ByteBuffers.toByteArray` 从 ByteBuffer 转换，在 `copy()` 中通过 `Arrays.copyOf` 深拷贝。

### `core/src/main/java/org/apache/iceberg/TrackedFile.java` (+6/-0 lines changes, -6 lines removed)

**修改目的**：移除 manifestLocation() 和 manifestPos() 方法。

**工作逻辑**：
从接口中移除 `manifestLocation()` 和 `manifestPos()` 两个方法，这两个方法不属于 TrackedFile 的持久化字段，不应出现在接口定义中。

### `core/src/main/java/org/apache/iceberg/TrackedFileStruct.java` (+328/-0 lines, new file)

**修改目的**：TrackedFile 的 StructLike 实现。

**工作逻辑**：
包含 TrackedFile 的所有持久化字段：tracking、contentType、location、fileFormat、recordCount、fileSizeInBytes、specId、contentStats、partition、keyMetadata 等。支持字段索引投影、统计信息子集拷贝（`copyWithStats`）、分区数据读写等。`byte[]` 类型字段在 copy 中深拷贝。

### `core/src/main/java/org/apache/iceberg/Tracking.java` (+9/-0 lines)

**修改目的**：为 Tracking 接口/类添加内容。

### `core/src/main/java/org/apache/iceberg/TrackingStruct.java` (+241/-0 lines, new file)

**修改目的**：Tracking 的 StructLike 实现。

### 测试文件（+903/-0 lines total）

**修改目的**：为新增的 StructLike 实现添加全面测试。

**工作逻辑**：
- `TestDeletionVectorStruct`（118 lines）：测试 DV 结构的读写、copy、toString 等。
- `TestManifestInfoStruct`（189 lines）：测试 ManifestInfo 结构的各字段读写和 copy。
- `TestTrackedFileStruct`（376 lines）：全面测试 TrackedFile 结构，包括字段读写、copy、统计信息子集、分区数据等。
- `TestTrackingStruct`（220 lines）：测试 Tracking 结构的读写和 copy。

## 总结

该提交为 Iceberg v4 的 TrackedFile 体系添加了完整的 StructLike 实现，使这些接口可以在 Avro 序列化/反序列化等场景中实际使用。四个新的 StructLike 类（DeletionVectorStruct、ManifestInfoStruct、TrackedFileStruct、TrackingStruct）都支持索引投影，为 v4 格式的元数据管理奠定了基础。测试覆盖全面，确保实现的正确性。
