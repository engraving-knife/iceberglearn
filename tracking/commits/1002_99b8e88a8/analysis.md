# 提交 1002：Core: Replace the duplicated ALL_DATA_FILES with ALL_DELETE_FILES (#10836)

## 提交信息

- **序号**：1002 / 4088
- **哈希**：99b8e88a88486f541b0ad2703fdc97ab615c5398
- **短哈希**：99b8e88a8
- **日期**：2024-08-01 10:23:05 -0500
- **作者**：hsiang-c
- **提交说明**：Core: Replace the duplicated ALL_DATA_FILES with ALL_DELETE_FILES (#10836)
- **PR/Issue**：#10836

## 总体目的

本提交修复的是 `TestMetadataTableFilters` 测试类中的一个明显笔误：在定义 `AGG_FILE_TABLES` 集合时，`ALL_DATA_FILES` 被重复列出了两次，而本应出现在集合中的 `ALL_DELETE_FILES` 却被遗漏了。这种"复制粘贴"导致的重复条目使得元数据表过滤逻辑的测试覆盖出现了盲区——`ALL_DELETE_FILES` 类型实际上没有被纳入 `AGG_FILE_TABLES` 这个聚合文件表集合进行验证。

与此同时，在 `expectedManifestCount`（用于返回期望的 manifest 数量）的 switch 分支中，`ALL_DELETE_FILES` 被错误地放在了与 `DATA_FILES`、`DELETE_FILES` 一组（直接返回 `partitions`），而实际上它的行为应该与 `ALL_DATA_FILES` 一致（返回 `partitions * 2`，因为要扫描 DELETED 和 ADDED 两种状态的数据 manifest）。

这两处错误结合起来，会导致针对 `ALL_DELETE_FILES` 元数据表的过滤测试在期望值上与真实行为不一致，是测试代码中的一个 bug，需要修正以保证测试的准确性和覆盖度。

## 如何达成设计目的

实现方式非常直接，对测试文件做两处互相对应的修正：

1. 在 `AGG_FILE_TABLES` 集合定义中，把重复的第二个 `ALL_DATA_FILES` 替换为 `ALL_DELETE_FILES`，使该集合真正覆盖到所有 ALL_* 类型的文件聚合表。
2. 在 `expectedManifestCount` 方法的 switch 中，把 `ALL_DELETE_FILES` 从 `DATA_FILES`/`DELETE_FILES` 分支（返回 `partitions`）移到 `ALL_DATA_FILES` 分支（返回 `partitions * 2`），使其期望计数与其真实扫描行为对齐。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestMetadataTableFilters.java`

**修改目的**：修正测试类中 `ALL_DELETE_FILES` 元数据表类型缺失以及期望 manifest 计数分组错误的问题，使测试覆盖正确并避免误导后续开发。

**工作逻辑**：

第一处修改在 `AGG_FILE_TABLES` 静态集合中，原本两个元素都是 `ALL_DATA_FILES`（重复），改为一个是 `ALL_DATA_FILES`、一个是 `ALL_DELETE_FILES`，确保 delete 类型的聚合元数据表也被纳入聚合测试集合：

```diff
   private static final Set<MetadataTableType> AGG_FILE_TABLES =
       Sets.newHashSet(
           MetadataTableType.ALL_DATA_FILES,
-          MetadataTableType.ALL_DATA_FILES,
+          MetadataTableType.ALL_DELETE_FILES,
           MetadataTableType.ALL_FILES,
           MetadataTableType.ALL_ENTRIES);
```

第二处修改在 `expectedManifestCount` 方法的 switch 语句中。`ALL_DELETE_FILES` 与 `ALL_DATA_FILES` 在底层扫描行为上等价——都需要扫描 DELETED 和 ADDED 两种状态下的数据 manifest，因此期望数量应当是 `partitions * 2`。原代码把 `ALL_DELETE_FILES` 错放在 `DATA_FILES`/`DELETE_FILES` 分支（只返回 `partitions`），现在把它移到与 `ALL_DATA_FILES` 同一 case 中：

```diff
       case DATA_FILES:
       case DELETE_FILES:
-      case ALL_DELETE_FILES:
         return partitions;
       case ALL_DATA_FILES:
+      case ALL_DELETE_FILES:
         return partitions * 2; // ScanTask for Data Manifest in DELETED and ADDED states
```

## 小结

- **成效**：修复了 `TestMetadataTableFilters` 中元数据表类型重复与分组错误两处测试缺陷，使 `ALL_DELETE_FILES` 被正确纳入聚合文件表测试集合并使用正确的期望 manifest 计数（`partitions * 2`）。
- **影响范围**：仅涉及一个测试文件 `core/src/test/java/org/apache/iceberg/TestMetadataTableFilters.java`，4 行改动，无生产代码变更。
- **回迁到 1.4.x 的注意事项**：该提交是测试 bug 修复，风险极低，适合回迁到 1.4.x。但需要确认 1.4.x 分支上该测试文件存在相同代码结构（即同样存在重复的 `ALL_DATA_FILES` 和错误的 case 分组）；如果 1.4.x 中元数据表类型枚举或扫描行为有差异，需相应调整期望值。整体属于低风险回迁。
