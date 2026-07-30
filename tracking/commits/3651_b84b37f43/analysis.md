# 提交 3651：Core: Replace string-based schema projection with selection on field-id (#16184)

## 提交信息

- **序号**：3651 / 4088
- **哈希**：b84b37f430b6c6e6b80e0a11d16c4d842b9da2a0
- **短哈希**：b84b37f43
- **日期**：2026-05-06 10:09:39 -0600
- **作者**：Hongyue/Steve Zhang
- **提交说明**：Core: Replace string-based schema projection with selection on field-id (#16184)
- **PR/Issue**：#16184

## 总体目的

这个提交将 Iceberg Core 中几处基于字符串名称的 schema 投影（`Schema.select(String...)`）替换为基于 field-id 的选择（`TypeUtil.select(Schema, Set<Integer>)`）。

Iceberg 的 schema 演进允许字段重命名，field-id 是字段的身份标识，而字段名可能变化。使用基于字符串名称的 `select` 进行投影在字段被重命名后会失效（找不到对应名称的字段），而基于 field-id 的 `TypeUtil.select` 则不受重命名影响，更加健壮。这是 Iceberg 社区推进的"以 field-id 为中心"的改进之一。

具体涉及三处：
1. `PartitionsTable` 中无分区表的 schema 选择（按名称选择需排除 partition 字段）。
2. `FileCleanupStrategy` 中 manifest 文件的投影（用于快照过期清理时读取 manifest）。
3. `ManifestReader` 中仅读取 status 字段的投影。

## 如何达成设计目的

将 `Schema.select("field1", "field2", ...)` 调用替换为 `TypeUtil.select(schema, ImmutableSet.of(fieldId1, fieldId2, ...))`，其中 field-id 通过常量字段的 `fieldId()` 方法获取。同时在 `PartitionsTable` 中将 schema 字段提取为静态常量，便于引用 field-id。

## 修改详情

### `core/src/main/java/org/apache/iceberg/PartitionsTable.java` (+77/-50 lines)

**修改目的**：将无分区表的 schema 选择从字符串改为 field-id。

**工作逻辑**：
1. 将所有 NestedField 提取为静态常量（`SPEC_ID`、`RECORD_COUNT`、`FILE_COUNT`、`TOTAL_DATA_FILE_SIZE_IN_BYTES`、`POSITION_DELETE_RECORD_COUNT` 等），并新增 `PARTITION_FIELD_ID = 1` 常量。
2. 构造函数中 schema 构造改为引用这些常量。
3. `schema()` 方法中，无分区表的 schema 选择从：
```java
return schema.select("record_count", "file_count", ...);
```
改为：
```java
return TypeUtil.select(
    schema,
    ImmutableSet.of(
        RECORD_COUNT.fieldId(),
        FILE_COUNT.fieldId(),
        ...));
```

### `core/src/main/java/org/apache/iceberg/FileCleanupStrategy.java` (+8/-7 lines)

**修改目的**：manifest 投影从字符串改为 field-id。

**工作逻辑**：
```java
// 旧
private static final Schema MANIFEST_PROJECTION =
    ManifestFile.schema()
        .select("manifest_path", "manifest_length", "partition_spec_id",
                "added_snapshot_id", "added_files_count", "deleted_files_count");
// 新
private static final Schema MANIFEST_PROJECTION =
    TypeUtil.select(
        ManifestFile.schema(),
        ImmutableSet.of(
            ManifestFile.PATH.fieldId(),
            ManifestFile.LENGTH.fieldId(),
            ManifestFile.SPEC_ID.fieldId(),
            ManifestFile.SNAPSHOT_ID.fieldId(),
            ManifestFile.ADDED_FILES_COUNT.fieldId(),
            ManifestFile.DELETED_FILES_COUNT.fieldId()));
```

### `core/src/main/java/org/apache/iceberg/ManifestReader.java` (+7/-3 lines)

**修改目的**：status-only 投影从字符串改为 field-id，并提取为静态常量。

**工作逻辑**：
新增静态常量 `STATUS_ONLY_PROJECTION`，使用 `TypeUtil.select` 基于 `ManifestEntry.STATUS.fieldId()` 选择，替代内联的 `.select("status")` 调用：
```java
private static final Schema STATUS_ONLY_PROJECTION =
    TypeUtil.select(
        ManifestEntry.getSchema(Types.StructType.of()),
        ImmutableSet.of(ManifestEntry.STATUS.fieldId()));
```

## 总结

这个提交将 Core 中三处基于字符串名称的 schema 投影替换为基于 field-id 的选择，提升了 schema 演进（尤其是字段重命名）场景下的健壮性。这是 Iceberg"以 field-id 为中心"设计理念的体现。改动还顺手将 `PartitionsTable` 的字段定义重构为静态常量，提高了代码可维护性。
