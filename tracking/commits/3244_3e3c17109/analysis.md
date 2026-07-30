# 提交 3244：Core: Add org.apache.iceberg.DataFiles.Builder#withSortOrderId (#15217)

## 提交信息

- **序号**：3244 / 4088
- **哈希**：3e3c171095defb8a43309b36424775d51152e625
- **短哈希**：3e3c17109
- **日期**：2026-02-13
- **作者**：Raunaq Morarka
- **提交说明**：Core: Add org.apache.iceberg.DataFiles.Builder#withSortOrderId (#15217)
- **PR/Issue**：#15217

## 总体目的

本提交为 `DataFiles.Builder` 新增了 `withSortOrderId(int)` 方法，允许直接通过排序顺序 ID（sort order ID）设置数据文件的排序顺序，而非必须传入完整的 `SortOrder` 对象。`DataFiles.Builder` 用于构建 `DataFile` 对象，每个数据文件都关联一个 `sortOrderId` 字段，标识该文件数据按哪个排序顺序写入。

此前 Builder 已有 `withSortOrder(SortOrder newSortOrder)` 方法，它从 `SortOrder` 对象中提取 `orderId` 并赋值给 `sortOrderId` 字段。但在许多场景下，调用方手中只有排序顺序的整数 ID 而没有完整的 `SortOrder` 对象——例如从清单文件（manifest）反序列化数据文件时、在测试中构建数据文件时、或从外部元数据重建数据文件时。在这些情况下，为了设置 `sortOrderId` 而必须先构造一个完整的 `SortOrder` 对象是不必要的开销和复杂性。新增 `withSortOrderId(int)` 方法填补了这一 API 空白，使 Builder 的接口更加完整和灵活。

## 如何达成设计目的

在 `DataFiles.Builder` 中紧随 `withSortOrder(SortOrder)` 方法之后新增 `withSortOrderId(int)` 方法，直接对 `sortOrderId` 字段赋值并返回 `this` 以支持链式调用。同时在 `ScanTestBase.testDataFileSorted()` 测试中补充使用 `withSortOrderId(1)` 构建数据文件的场景，验证该方法的功能正确性。

## 修改详情

### `core/src/main/java/org/apache/iceberg/DataFiles.java` (+5/-0 lines)

**修改目的**：新增 `withSortOrderId(int)` 方法以支持直接通过整数 ID 设置排序顺序。

**工作逻辑**：在 `withSortOrder(SortOrder newSortOrder)` 方法之后新增 `public Builder withSortOrderId(int newSortOrderId) { this.sortOrderId = newSortOrderId; return this; }`。该方法直接将传入的整数赋值给 Builder 的 `sortOrderId` 字段并返回 `this`。与 `withSortOrder(SortOrder)` 的区别在于：后者需要完整的 `SortOrder` 对象并从中调用 `newSortOrder.orderId()` 提取 ID（且 null 安全检查），前者直接接受 ID 值。两个字段最终都写入同一个 `sortOrderId`，在 `build()` 时用于构建 `DataFile`。这与 Builder 中其他 `with*` 方法的链式模式一致。

### `core/src/test/java/org/apache/iceberg/ScanTestBase.java` (+11/-0 lines)

**修改目的**：在 `testDataFileSorted()` 测试中验证 `withSortOrderId(int)` 的功能。

**工作逻辑**：在现有的 `testDataFileSorted()` 测试方法中，原有逻辑已使用 `withSortOrder(SortOrder)` 追加了一个文件 `a.parquet`（排序顺序为按字段 `a` 升序，orderId 为 1）。新增逻辑在此之后追加第二个文件 `b.parquet`，使用 `withSortOrderId(1)` 直接设置排序顺序 ID 为 1。随后执行 `table.newScan().planFiles()` 扫描所有文件，断言每个 `FileScanTask` 的 `file().sortOrderId()` 都等于 1。这同时验证了两种设置方式（`withSortOrder` 和 `withSortOrderId`）最终产生相同的 `sortOrderId` 值，且扫描结果能正确读取该字段。

## 总结

本提交为 `DataFiles.Builder` 新增了 `withSortOrderId(int)` 方法，补全了直接通过整数 ID 设置数据文件排序顺序的能力。此前只能通过 `withSortOrder(SortOrder)` 间接设置，需要构造完整的 `SortOrder` 对象。新方法简化了仅有排序顺序 ID 场景下的 Builder 使用（如反序列化、测试构建），使 API 更加完整。配套测试验证了两种设置方式的等价性和扫描结果的一致性。改动虽小，但填补了 Builder 接口的一个实用性空白。
