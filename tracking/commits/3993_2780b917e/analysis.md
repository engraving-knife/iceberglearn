# 提交 3993：Core: Cleanup some of the TrackedFile tests (#17035)

## 提交信息

- **序号**：3993 / 4088
- **哈希**：2780b917ea82c509d1fd817e536b70e7a21dc12c
- **短哈希**：2780b917e
- **日期**：2026-07-07 13:07:34 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Core: Cleanup some of the TrackedFile tests (#17035)
- **PR/Issue**：#17035

## 总体目的

本提交对 `TestTrackedFileStruct` 测试类进行了大规模清理和重构。此前测试代码存在大量样板代码：每个测试都通过 ordinal 常量逐字段 `set` 来构造 `TrackedFileStruct`，重复且易错。重构后提取了共享的测试数据常量，并使用构造函数直接创建完整的 struct，减少重复代码。

重构还引入了参数化测试和基于字段名的 `pos()` 辅助方法，使测试更简洁、可读性更强。同时清理了不再需要的 ordinal 查找逻辑和投影测试（projection test）。

## 如何达成设计目的

1. 提取共享常量：`TRACKING`、`PARTITION`、`DELETION_VECTOR`、`MANIFEST_INFO`、`CONTENT_STATS` 作为静态 final 字段，所有测试复用。
2. 使用 `TrackedFileStruct` 的全参数构造函数直接创建 struct，替代逐字段 set。
3. 新增 `pos(String name)` 辅助方法按字段名查找 ordinal，替代大量 ordinal 常量。
4. 引入 `RoundTripSerializer` 进行序列化往返测试。
5. 移除冗余的投影测试（`projectionWithoutPartition`）。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestTrackedFileStruct.java` (+232/-365 lines)

**修改目的**：清理和重构测试。

**工作逻辑**：
- 移除 15 个 ordinal 常量和 `ordinalOf` 方法，替换为 `FIELDS` 列表和 `pos()` 方法。
- 提取共享测试数据常量：
```java
private static final Tracking TRACKING = new TrackingStruct(...);
private static final PartitionData PARTITION = newPartition(7, "music");
private static final DeletionVectorStruct DELETION_VECTOR = ...;
private static final ManifestInfoStruct MANIFEST_INFO = ...;
private static final ContentStats CONTENT_STATS = ...;
```
- `testFieldAccess` → `fieldAccess`：使用构造函数直接创建，断言使用 `isSameAs` 而非逐字段比较。
- 新增 `setByPosition` 和 `getByPosition` 测试，覆盖按位置读写。
- 引入 `Comparator` 和参数化测试验证序列化往返。
- 移除 `testReaderSideFields`、`projectionWithoutPartition`、`partitionAccess` 等过时测试。

## 总结

本提交是纯测试代码重构，不改变任何生产代码逻辑。通过提取共享常量、使用构造函数和参数化测试，显著减少了测试样板代码（净减 133 行），提高了可读性和可维护性。
