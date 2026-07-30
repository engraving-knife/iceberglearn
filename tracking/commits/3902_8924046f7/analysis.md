# 提交 3902：Core: Add writer_format_version field to TrackedFile (#16688)

## 提交信息

- **序号**：3902 / 4088
- **哈希**：8924046f7ac5326e454d1cd48a6b30a6d16ba196
- **短哈希**：8924046f7
- **日期**：2026-06-17 22:09:59 -0600
- **作者**：gaborkaszab
- **提交说明**：Core: Add writer_format_version field to TrackedFile (#16688)
- **PR/Issue**：#16688

## 总体目的

为 `TrackedFile` 接口和 `TrackedFileStruct` 实现新增 `writer_format_version` 字段。`TrackedFile` 是 Iceberg 中跟踪文件元数据的接口，用于统一表示数据文件、删除文件、manifest 文件等的跟踪信息。

`writer_format_version` 字段记录写入文件的 writer 格式版本，这对于处理不同版本 writer 写入的文件至关重要。随着 Iceberg 格式演进（如 V3 引入 deletion vectors），不同版本的 writer 可能产生不同格式的文件，在读取或重写时需要知道原始 writer 版本以正确处理。

## 如何达成设计目的

在 `TrackedFile` 接口中定义新的 `WRITER_FORMAT_VERSION` 字段（field id=157），在 `TrackedFileStruct` 实现中添加对应字段、调整 ordinal 映射、更新构造器和访问器。同时更新所有相关测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/TrackedFile.java` (+7/-0 lines)

**修改目的**：定义新的 writer_format_version 字段。

**工作逻辑**：
```java
Types.NestedField WRITER_FORMAT_VERSION =
    Types.NestedField.required(
        157, "writer_format_version", Types.IntegerType.get(), "Writer format version");
```
字段 id 为 157，类型为 required Integer。在 `schema()` 方法中将该字段添加到 `CONTENT_TYPE` 之后、`LOCATION` 之前。新增 `writerFormatVersion()` 访问器方法。

### `core/src/main/java/org/apache/iceberg/TrackedFileStruct.java` (+81/-38 lines)

**修改目的**：实现 writer_format_version 字段的存储和访问。

**工作逻辑**：

1. 新增字段定义和状态：
```java
private int writerFormatVersion = -1;
```

2. 构造器新增参数：
```java
TrackedFileStruct(
    Tracking tracking,
    FileContent contentType,
    int writerFormatVersion,  // 新增
    String location,
    // ...
```

3. **调整 ordinal 映射**：由于新字段插入在 position 2（CONTENT_TYPE 之后），所有后续字段的 ordinal 需要后移一位。`get()` 和 `set()` 方法中 case 2 从 `location` 改为 `writerFormatVersion`，原 case 2-14 全部后移为 3-15。

4. 新增访问器：
```java
@Override
public int writerFormatVersion() {
  return writerFormatVersion;
}
```

5. 拷贝构造器中复制 `writerFormatVersion`，`toString()` 中添加该字段。

### `core/src/test/java/org/apache/iceberg/TestTrackedFile.java` (+2/-2 lines)

**修改目的**：更新字段顺序和 ID 预期。

**工作逻辑**：
在字段名列表中添加 "writer_format_version"，在字段 ID 列表中添加 157：
```java
.containsExactly(
    147, 134, 157, 100, 101, 103, 104, 141, 102, 146, 140, 148, 150, 131, 132, 135);
```

### `core/src/test/java/org/apache/iceberg/TestTrackedFileAdapters.java` (+10/-3 lines)

**修改目的**：更新测试中的 TrackedFileStruct 构造调用，添加 writer_format_version 参数。

### `core/src/test/java/org/apache/iceberg/TestTrackedFileStruct.java` (+50/-19 lines)

**修改目的**：更新 TrackedFileStruct 测试以覆盖新字段。

## 总结

为 TrackedFile 接口新增 `writer_format_version` 字段（field id=157），用于跟踪写入文件的 writer 格式版本。这是一个涉及 schema 演进的修改，需要调整 `TrackedFileStruct` 中所有字段的 ordinal 映射，并更新所有相关测试。该字段为未来处理不同 writer 版本的文件提供了元数据基础。
