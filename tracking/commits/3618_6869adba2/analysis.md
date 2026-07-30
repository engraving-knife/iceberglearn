# 提交 3618：Core: Add builders for v4 structs (#16092)

## 提交信息

- **序号**：3618 / 4088
- **哈希**：6869adba2b869150778ce42d288c85a614d18f86
- **短哈希**：6869adba2
- **日期**：2026-04-29 22:31:20 -0600
- **作者**：Anoop Johnson
- **提交说明**：Core: Add builders for v4 structs (#16092)
- **PR/Issue**：#16092

## 总体目的

这个提交为 Iceberg v4 格式引入的三个 Struct 类型添加了 Builder 模式的构造器，以简化这些对象的创建过程并提高代码可读性。

Iceberg v4 格式引入了新的元数据结构：
- `DeletionVectorStruct`：表示删除向量（Deletion Vector）的元数据。
- `ManifestInfoStruct`：表示 manifest 文件的信息，包括文件和行的统计计数。
- `TrackingStruct`：表示文件追踪信息，包括快照 ID、序列号、删除位置等。

之前这些 Struct 对象的创建主要通过无参构造函数加 setter 方法，或者通过 copy 构造函数，代码不够清晰且容易遗漏字段。添加 Builder 模式可以让对象的创建更加直观、类型安全，并在构建时进行参数校验。

## 如何达成设计目的

为每个 Struct 类添加：
1. 一个私有的全参数构造函数。
2. 一个静态的 `Builder` 内部类，包含所有字段的 setter 方法（返回 Builder 以支持链式调用）。
3. 一个静态的 `builder()` 工厂方法。
4. Builder 的 `build()` 方法中添加参数校验（Preconditions）。

同时移除了 `TrackingStruct` 中不再需要的无参构造函数。

## 修改详情

### `core/src/main/java/org/apache/iceberg/DeletionVectorStruct.java` (+44/-2 lines)

**修改目的**：为 DeletionVectorStruct 添加 Builder。

**工作逻辑**：
1. 新增私有全参数构造函数：
```java
private DeletionVectorStruct(String location, long offset, long sizeInBytes, long cardinality) {
  super(BASE_TYPE, BASE_TYPE);
  this.location = location;
  this.offset = offset;
  this.sizeInBytes = sizeInBytes;
  this.cardinality = cardinality;
}
```

2. 新增 `Builder` 内部类，包含 `location()`、`offset()`、`sizeInBytes()`、`cardinality()` 方法。`build()` 方法校验所有字段：
```java
DeletionVectorStruct build() {
  Preconditions.checkArgument(location != null, "Invalid location: null");
  Preconditions.checkArgument(offset >= 0, "Invalid offset: %s (must be >= 0)", offset);
  Preconditions.checkArgument(sizeInBytes >= 0, "Invalid size in bytes: %s (must be >= 0)", sizeInBytes);
  Preconditions.checkArgument(cardinality >= 0, "Invalid cardinality: %s (must be >= 0)", cardinality);
  return new DeletionVectorStruct(location, offset, sizeInBytes, cardinality);
}
```

### `core/src/main/java/org/apache/iceberg/ManifestInfoStruct.java` (+127/-2 lines)

**修改目的**：为 ManifestInfoStruct 添加 Builder。

**工作逻辑**：
新增私有全参数构造函数和 Builder 类。Builder 包含 11 个字段的方法：`addedFilesCount`、`existingFilesCount`、`deletedFilesCount`、`replacedFilesCount`、`addedRowsCount`、`existingRowsCount`、`deletedRowsCount`、`replacedRowsCount`、`minSequenceNumber`、`dv`、`dvCardinality`。`build()` 方法校验计数字段 >= -1。

### `core/src/main/java/org/apache/iceberg/TrackingStruct.java` (+78/-4 lines)

**修改目的**：为 TrackingStruct 添加 Builder，移除无参构造函数。

**工作逻辑**：
1. 移除了无参构造函数 `TrackingStruct()`。
2. 新增私有全参数构造函数和 Builder 类。Builder 包含 8 个字段的方法：`status`、`snapshotId`、`dataSequenceNumber`、`fileSequenceNumber`、`dvSnapshotId`、`firstRowId`、`deletedPositions`、`replacedPositions`。`build()` 方法校验 status 不为 null。

### 测试文件

- `TestDeletionVectorStruct.java` (+75/-14 lines)：更新测试以使用 Builder。
- `TestManifestInfoStruct.java` (+236/-60 lines)：更新测试以使用 Builder。
- `TestTrackedFileStruct.java` (+35/-32 lines)：适配 TrackingStruct 变更。
- `TestTrackingStruct.java` (+43/-44 lines)：更新测试以使用 Builder。

## 总结

这个提交为 Iceberg v4 格式的三个 Struct 类型（DeletionVectorStruct、ManifestInfoStruct、TrackingStruct）添加了 Builder 模式构造器。Builder 模式使这些复杂对象的创建更加清晰、类型安全，并在构建时进行参数校验，提高了代码的可读性和可维护性。同时移除了不再需要的无参构造函数，收紧了对象创建的入口。
